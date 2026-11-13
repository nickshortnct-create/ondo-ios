#!/usr/bin/env python3
"""Ondo weekly curator: Claude-made mood and genre mixes plus song categories.

Runs on decknas. Reads Navidrome's database read-only and asks Claude
(Sonnet 5.5, medium effort, through the `claude` CLI with no tools) to

  * tag every song with mood/activity categories and one genre family
    (incrementally: only songs it hasn't seen), and
  * plan and sequence this week's "Made for you" mixes.

The result is written to state/curation.json, which api.py serves to the app.
The app turns each mix into a real Navidrome playlist with the user's own
login, so this script never needs the Navidrome password and never writes to
Navidrome.

Commands:
  curator.py weekly [--force]   new mixes (cron: Mondays 02:00 America/New_York)
  curator.py tag-new            tag songs added since the last run (nightly)
  curator.py status             print a summary of the current state
"""
import argparse
import concurrent.futures
import datetime as dt
import fcntl
import hashlib
import json
import os
import random
import shutil
import sqlite3
import subprocess
import sys
import time
from zoneinfo import ZoneInfo

from covers import build_covers

HERE = os.path.dirname(os.path.abspath(__file__))
STATE = os.environ.get("ONDO_CURATOR_STATE", os.path.join(HERE, "state"))
NAVIDROME_DB = os.environ.get("NAVIDROME_DB", "/home/nicknas/nas-configs/navidrome/navidrome.db")
NAVIDROME_USER = os.environ.get("NAVIDROME_USER", "nick")
LOCAL_TZ = ZoneInfo("America/New_York")
MODEL = "claude-sonnet-5-5"
EFFORT = "medium"
CLAUDE = shutil.which("claude", path=f"{os.path.expanduser('~/.local/bin')}:/usr/local/bin:/usr/bin") or "claude"
CLAUDE_TIMEOUT_S = 15 * 60
TAG_BATCH = 150
PARALLEL = 3
MIX_SIZE = (25, 32)
MIX_CANDIDATES = 170

# Order here is the order chips appear in the app.
MOODS = [
    ("chill", "Chill", "🌊"),
    ("relax", "Relaxing", "🛋️"),
    ("hype", "Hype", "🔥"),
    ("gym", "Gym", "💪"),
    ("party", "Party", "🎉"),
    ("feel-good", "Feel Good", "☀️"),
    ("focus", "Focus", "🎧"),
    ("late-night", "Late Night", "🌙"),
    ("drive", "Drive", "🚗"),
    ("sad", "In My Feels", "🌧️"),
    ("love", "Love", "❤️"),
    ("throwback", "Throwback", "📼"),
]
GENRES = [
    ("hip-hop", "Hip-Hop", "🎤"),
    ("rnb", "R&B", "💜"),
    ("pop", "Pop", "✨"),
    ("rock", "Rock", "🎸"),
    ("alternative", "Alternative", "🌀"),
    ("indie", "Indie", "🪴"),
    ("electronic", "Electronic", "🎛️"),
    ("dance", "Dance", "🪩"),
    ("country", "Country", "🤠"),
    ("latin", "Latin", "💃"),
    ("afrobeats", "Afrobeats", "🌍"),
    ("soul", "Soul & Funk", "🎷"),
    ("jazz", "Jazz", "🎺"),
    ("metal", "Metal", "🤘"),
    ("punk", "Punk", "⚡"),
    ("folk", "Folk & Acoustic", "🪕"),
    ("classical", "Classical", "🎻"),
    ("soundtrack", "Soundtrack", "🎬"),
    ("reggae", "Reggae", "🌴"),
    ("other", "Other", "🎶"),
]
MOOD_KEYS = [k for k, _, _ in MOODS]
GENRE_KEYS = [k for k, _, _ in GENRES]
# Card colours for the app; picked per mix so the home screen isn't one hue.
PALETTE = ["#7C4DFF", "#FF6E40", "#00BFA5", "#FF4081", "#448AFF", "#FFAB00",
           "#64DD17", "#E040FB", "#00B8D4", "#FF5252", "#536DFE", "#1DE9B6"]

MOOD_MIX_COUNT = 6
GENRE_MIX_COUNT = 4


# ----------------------------------------------------------------- utilities

def now_utc():
    return dt.datetime.now(dt.timezone.utc)


def iso(ts=None):
    return (ts or now_utc()).strftime("%Y-%m-%dT%H:%M:%SZ")


def log(msg):
    line = f"{iso()} {msg}"
    print(line, flush=True)
    with open(os.path.join(STATE, "curator.log"), "a") as f:
        f.write(line + "\n")


def load_json(name, default):
    try:
        with open(os.path.join(STATE, name)) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return default


def save_json(name, data, compact=False):
    path = os.path.join(STATE, name)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        if compact:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(data, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)


def week_id(day: dt.date):
    y, w, _ = day.isocalendar()
    return f"{y}-W{w:02d}"


# ------------------------------------------------------------------ library

def read_library():
    """All present songs with the user's play counts and stars."""
    con = sqlite3.connect(f"file:{NAVIDROME_DB}?mode=ro", uri=True, timeout=30)
    try:
        user = con.execute("select id from user where user_name = ?", (NAVIDROME_USER,)).fetchone()
        user_id = user[0] if user else ""
        rows = con.execute(
            """
            select m.id, m.title, m.artist, m.album, m.year, m.tags, m.bpm, m.duration, m.created_at,
                   coalesce(a.play_count, 0), a.play_date, coalesce(a.starred, 0)
            from media_file m
            left join annotation a
              on a.item_id = m.id and a.item_type = 'media_file' and a.user_id = ?
            where m.missing = 0
            """,
            (user_id,),
        ).fetchall()
    finally:
        con.close()
    songs = []
    for (sid, title, artist, album, year, tags, bpm, duration, created, plays, played_at, starred) in rows:
        genres = []
        try:
            genres = [g["value"] for g in (json.loads(tags or "{}").get("genre") or [])]
        except (ValueError, TypeError, AttributeError):
            pass
        songs.append({
            "id": sid, "title": title or "", "artist": artist or "", "album": album or "",
            "year": year or 0, "genres": genres, "bpm": bpm or 0, "duration": duration or 0,
            "created": created or "", "plays": plays, "played_at": played_at or "", "starred": bool(starred),
        })
    return songs


def clean(s, n=60):
    return " ".join((s or "").replace("\t", " ").split())[:n]


# ------------------------------------------------------------------- claude

def ask_claude(prompt, schema, label):
    cmd = [
        CLAUDE, "-p", prompt,
        "--model", MODEL, "--effort", EFFORT,
        "--tools", "",
        "--no-session-persistence",
        "--output-format", "json",
        "--json-schema", json.dumps(schema),
    ]
    last = None
    for attempt in range(3):
        try:
            proc = subprocess.run(cmd, cwd=STATE, capture_output=True, text=True, timeout=CLAUDE_TIMEOUT_S)
            data = json.loads(proc.stdout or "{}")
            usage = data.get("usage", {})
            log(f"claude {label}: in={usage.get('input_tokens')} out={usage.get('output_tokens')} "
                f"cost_usd={data.get('total_cost_usd')} ms={data.get('duration_ms')} "
                f"model={','.join(data.get('modelUsage', {}).keys())}")
            out = data.get("structured_output")
            if data.get("is_error") or not isinstance(out, dict):
                raise RuntimeError((data.get("result") or proc.stderr or "no structured output")[-300:])
            return out
        except (subprocess.TimeoutExpired, json.JSONDecodeError, RuntimeError) as e:
            last = e
            log(f"claude {label} attempt {attempt + 1} failed: {e}")
            time.sleep(20 * (attempt + 1))
    raise RuntimeError(f"Claude failed for {label}: {last}")


# ------------------------------------------------------------------ tagging

TAG_SCHEMA = {
    "type": "object",
    "properties": {
        "songs": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "i": {"type": "integer"},
                    "m": {"type": "array", "items": {"type": "string", "enum": MOOD_KEYS}, "maxItems": 4},
                    "g": {"type": "string", "enum": GENRE_KEYS},
                },
                "required": ["i", "m", "g"],
            },
        }
    },
    "required": ["songs"],
}

TAG_PROMPT = """You are the music curator for a personal music library. Tag each song below.

For every song give:
- "m": 1 to 4 mood/activity categories that genuinely fit how the song sounds and feels:
  chill (laid-back, smooth, easy listening), relax (calm, soft, winding down),
  hype (high energy, aggressive, turn-up), gym (driving tempo/energy you'd lift or run to),
  party (dance-floor, crowd songs), feel-good (upbeat, happy, sunny), focus (low-distraction,
  steady, mostly instrumental or hypnotic), late-night (moody, atmospheric, after dark),
  drive (cruising, open road, windows down), sad (heartbreak, melancholy, emotional),
  love (romantic, sensual, love songs), throwback (nostalgic, an older classic: roughly 15+ years old or a
  song that evokes an earlier era).
- "g": the single best genre family.

Use your knowledge of the actual songs and artists. When you don't know a song, infer from artist,
title, album, year, genre tags and BPM. Be selective with moods: a hard trap song is hype/gym, not relax.
Return every index exactly once.

Columns: index | title | artist | album | year | genre tags | bpm
{lines}
"""


def tag_batch(batch, label):
    lines = "\n".join(
        f"{i} | {clean(s['title'])} | {clean(s['artist'], 40)} | {clean(s['album'], 40)} | "
        f"{s['year'] or ''} | {', '.join(s['genres'][:4])} | {s['bpm'] or ''}"
        for i, s in enumerate(batch)
    )
    out = ask_claude(TAG_PROMPT.replace("{lines}", lines), TAG_SCHEMA, label)
    result = {}
    for item in out.get("songs", []):
        i = item.get("i")
        if isinstance(i, int) and 0 <= i < len(batch):
            moods = [m for m in item.get("m", []) if m in MOOD_KEYS]
            genre = item.get("g") if item.get("g") in GENRE_KEYS else "other"
            result[batch[i]["id"]] = {"m": moods, "g": genre}
    return result


def tag_songs(songs, only_new=True):
    tags = load_json("song-tags.json", {})
    todo = [s for s in songs if not only_new or s["id"] not in tags]
    if not todo:
        log("tagging: nothing new")
        return tags
    # Group by artist/album so each batch has context (an album tagged together is more consistent).
    todo.sort(key=lambda s: (s["artist"].lower(), s["album"].lower(), s["title"].lower()))
    batches = [todo[i:i + TAG_BATCH] for i in range(0, len(todo), TAG_BATCH)]
    log(f"tagging {len(todo)} songs in {len(batches)} batches")
    done = 0
    with concurrent.futures.ThreadPoolExecutor(PARALLEL) as pool:
        futures = {pool.submit(tag_batch, b, f"tag {n + 1}/{len(batches)}"): b for n, b in enumerate(batches)}
        for fut in concurrent.futures.as_completed(futures):
            try:
                tags.update(fut.result())
                done += 1
                save_json("song-tags.json", tags, compact=True)  # progress survives a crash
            except Exception as e:  # one bad batch shouldn't lose the rest
                log(f"tagging batch failed: {e}")
    log(f"tagging finished: {done}/{len(batches)} batches, {len(tags)} songs tagged in total")
    return tags


# ------------------------------------------------------------------- mixes

PLAN_SCHEMA = {
    "type": "object",
    "properties": {
        "mixes": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "section": {"type": "string", "enum": ["mood", "genre"]},
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "emoji": {"type": "string"},
                    "moods": {"type": "array", "items": {"type": "string", "enum": MOOD_KEYS}},
                    "genres": {"type": "array", "items": {"type": "string", "enum": GENRE_KEYS}},
                    "brief": {"type": "string"},
                },
                "required": ["section", "title", "description", "emoji", "moods", "genres", "brief"],
            },
        }
    },
    "required": ["mixes"],
}

PICK_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "description": {"type": "string"},
        "songs": {"type": "array", "items": {"type": "integer"}},
    },
    "required": ["title", "description", "songs"],
}

PLAN_PROMPT = """You curate the weekly "Made for you" playlists in a personal music app, like Spotify's
daily mixes and mood playlists. Today is {date} (week {week}). Plan this week's lineup:

- exactly {mood_count} MOOD mixes (section "mood"): each built around one or two mood/activity categories,
  e.g. a gym mix, a late-night mix, a rainy-day sad mix, a sunny feel-good mix. Seasonal or time-of-year
  angles are welcome.
- exactly {genre_count} GENRE mixes (section "genre"): each built around one or two genre families,
  optionally narrowed by a mood (e.g. "Hip-Hop Heat", "Indie Comedown").

Rules:
- Make the lineup feel fresh: don't repeat a title from the recent weeks below, and rotate the angle
  of moods that recur. Each mix needs enough songs in the library (see counts; at least ~60 candidates).
- Lean toward what this listener actually plays (top artists and genres below), but keep variety.
- title: short and catchy (max 28 characters, no emoji). description: one line, max 90 characters,
  written to the listener. emoji: one emoji. brief: 1-2 sentences telling the song picker the exact vibe.
- "moods" and "genres" are the filters used to build the candidate pool; mood mixes may leave genres
  empty, genre mixes may leave moods empty.

Songs per mood category: {mood_counts}
Songs per genre family: {genre_counts}
Listener's most-played artists: {top_artists}
Recently played artists: {recent_artists}
Recent weeks' mixes (avoid repeating): {history}
"""

PICK_PROMPT = """You are sequencing one playlist for a personal music app.

Playlist: {title}
Description: {description}
Vibe: {brief}

From the candidate songs below, choose {min_size}-{max_size} songs that fit the vibe and make a great listen:
- open strong, keep the energy flowing smoothly, end on a satisfying note;
- at most 2 songs by the same artist, never two by the same artist back to back;
- mix favourites (★ or high play counts) with less-played gems the listener hasn't heard much;
- prefer songs marked "new" (not in last week's mixes).
Return the chosen candidate indexes in play order. You may polish the title (max 28 characters,
no emoji) and description (max 90 characters).

Columns: index | title | artist | year | plays | flags
{lines}
"""


def counts(tags, songs_by_id):
    moods = {k: 0 for k in MOOD_KEYS}
    genres = {k: 0 for k in GENRE_KEYS}
    for sid, t in tags.items():
        if sid not in songs_by_id:
            continue
        for m in t.get("m", []):
            moods[m] = moods.get(m, 0) + 1
        genres[t.get("g", "other")] = genres.get(t.get("g", "other"), 0) + 1
    return moods, genres


def top_artists(songs, recent=False, n=25):
    score = {}
    for s in songs:
        if recent:
            if not s["played_at"] or s["played_at"] < (now_utc() - dt.timedelta(days=30)).strftime("%Y-%m-%d"):
                continue
            score[s["artist"]] = score.get(s["artist"], 0) + 1
        else:
            score[s["artist"]] = score.get(s["artist"], 0) + s["plays"] + (3 if s["starred"] else 0)
    return [a for a, v in sorted(score.items(), key=lambda kv: -kv[1]) if v > 0][:n]


def candidate_pool(mix, songs, tags, last_week_ids, rng):
    moods, genres = set(mix.get("moods", [])), set(mix.get("genres", []))
    pool = []
    for s in songs:
        t = tags.get(s["id"])
        if not t:
            continue
        mood_ok = not moods or moods.intersection(t.get("m", []))
        genre_ok = not genres or t.get("g") in genres
        if mood_ok and genre_ok:
            pool.append(s)
    if len(pool) < MIX_SIZE[0] and moods and genres:  # too narrow: relax the genre filter
        pool = [s for s in songs if s["id"] in tags and moods.intersection(tags[s["id"]].get("m", []))]

    # Weighted sample: favourites and played songs more likely, last week's picks less likely,
    # but always a healthy share of the rest so mixes surface deep cuts.
    def weight(s):
        w = 1.0 + min(s["plays"], 30) / 10 + (1.5 if s["starred"] else 0)
        return w * (0.25 if s["id"] in last_week_ids else 1.0)

    keyed = sorted(pool, key=lambda s: -(rng.random() ** (1 / weight(s))))
    picked = keyed[:MIX_CANDIDATES]
    # Spread artists: no artist more than 8 times in the candidate list.
    per_artist, out = {}, []
    for s in picked:
        per_artist[s["artist"]] = per_artist.get(s["artist"], 0) + 1
        if per_artist[s["artist"]] <= 8:
            out.append(s)
    rng.shuffle(out)
    return out


def pick_songs(mix, pool, last_week_ids, label):
    lines = "\n".join(
        f"{i} | {clean(s['title'])} | {clean(s['artist'], 40)} | {s['year'] or ''} | {s['plays']} | "
        f"{'★ ' if s['starred'] else ''}{'' if s['id'] in last_week_ids else 'new'}"
        for i, s in enumerate(pool)
    )
    prompt = (PICK_PROMPT
              .replace("{title}", mix["title"]).replace("{description}", mix["description"])
              .replace("{brief}", mix["brief"]).replace("{min_size}", str(MIX_SIZE[0]))
              .replace("{max_size}", str(MIX_SIZE[1])).replace("{lines}", lines))
    out = ask_claude(prompt, PICK_SCHEMA, label)
    seen, ids = set(), []
    for i in out.get("songs", []):
        if isinstance(i, int) and 0 <= i < len(pool) and i not in seen:
            seen.add(i)
            ids.append(pool[i]["id"])
    title = clean(out.get("title") or mix["title"], 40)
    description = clean(out.get("description") or mix["description"], 120)
    return title, description, ids[:MIX_SIZE[1] + 5]


def build_weekly(songs, tags, force=False):
    today = dt.datetime.now(LOCAL_TZ).date()
    week = week_id(today)
    history = load_json("history.json", [])
    if not force and history and history[-1].get("week") == week:
        log(f"weekly: {week} already curated, skipping")
        return None
    if history and history[-1].get("week") == week:
        history.pop()  # forced re-run: replace this week's mixes instead of stacking a second entry

    songs_by_id = {s["id"]: s for s in songs}
    mood_counts, genre_counts = counts(tags, songs_by_id)
    recent_titles = [f"{h['week']}: " + ", ".join(m["title"] for m in h.get("mixes", [])) for h in history[-4:]]
    plan_prompt = (PLAN_PROMPT
                   .replace("{date}", today.strftime("%A %B %d, %Y")).replace("{week}", week)
                   .replace("{mood_count}", str(MOOD_MIX_COUNT)).replace("{genre_count}", str(GENRE_MIX_COUNT))
                   .replace("{mood_counts}", json.dumps(mood_counts))
                   .replace("{genre_counts}", json.dumps({k: v for k, v in genre_counts.items() if v}))
                   .replace("{top_artists}", ", ".join(top_artists(songs)))
                   .replace("{recent_artists}", ", ".join(top_artists(songs, recent=True)))
                   .replace("{history}", " | ".join(recent_titles) or "none yet"))
    plan = ask_claude(plan_prompt, PLAN_SCHEMA, "plan")["mixes"]
    moods = [m for m in plan if m["section"] == "mood"][:MOOD_MIX_COUNT]
    genres = [m for m in plan if m["section"] == "genre"][:GENRE_MIX_COUNT]
    plan = moods + genres

    last_week_ids = set()
    if history:
        for m in history[-1].get("mixes", []):
            last_week_ids.update(m.get("songIds", []))
    rng = random.Random(int(hashlib.sha256(week.encode()).hexdigest(), 16))
    pools = [candidate_pool(m, songs, tags, last_week_ids, rng) for m in plan]

    results = [None] * len(plan)
    with concurrent.futures.ThreadPoolExecutor(PARALLEL) as pool:
        futures = {}
        for n, (mix, cands) in enumerate(zip(plan, pools)):
            if len(cands) < MIX_SIZE[0]:
                log(f"weekly: skipping '{mix['title']}', only {len(cands)} candidates")
                continue
            futures[pool.submit(pick_songs, mix, cands, last_week_ids, f"pick {n + 1}/{len(plan)}")] = n
        for fut in concurrent.futures.as_completed(futures):
            n = futures[fut]
            try:
                results[n] = fut.result()
            except Exception as e:
                log(f"weekly: picking failed for '{plan[n]['title']}': {e}")

    mixes, slot_counter = [], {"mood": 0, "genre": 0}
    for n, (mix, res) in enumerate(zip(plan, results)):
        if not res or len(res[2]) < 10:
            continue
        title, description, ids = res
        section = mix["section"]
        slot_counter[section] += 1
        mixes.append({
            "slot": f"{section}-{slot_counter[section]}",
            "section": section,
            "title": title,
            "description": description,
            "emoji": (mix.get("emoji") or "🎶")[:4],
            "color": PALETTE[(n + rng.randrange(len(PALETTE))) % len(PALETTE)],
            "moods": mix.get("moods", []),
            "genres": mix.get("genres", []),
            "songIds": ids,
        })
    if not mixes:
        raise RuntimeError("weekly: no mixes could be built")
    build_covers(mixes, NAVIDROME_DB, os.path.join(STATE, "covers"), log)
    entry = {"week": week, "generatedAt": iso(), "model": MODEL, "mixes": mixes}
    history.append(entry)
    save_json("history.json", history[-12:])
    log(f"weekly: {week} built {len(mixes)} mixes: " + ", ".join(m["title"] for m in mixes))
    return entry


# ----------------------------------------------------------------- publish

def publish(tags, songs):
    """Write curation.json: the latest week's mixes plus a compact category index."""
    history = load_json("history.json", [])
    latest = history[-1] if history else {"week": None, "generatedAt": None, "mixes": []}
    present = {s["id"] for s in songs}
    mix_list = []
    for m in latest.get("mixes", []):
        ids = [i for i in m["songIds"] if i in present]
        if ids:
            mix_list.append({**m, "songIds": ids})
    # Index-encoded categories keep the payload small: [moodIndexes..., genreIndex] by position.
    song_categories = {}
    for sid, t in tags.items():
        if sid in present:
            song_categories[sid] = [MOOD_KEYS.index(m) for m in t.get("m", []) if m in MOOD_KEYS] + \
                                   [len(MOOD_KEYS) + GENRE_KEYS.index(t.get("g", "other"))]
    data = {
        "version": 1,
        "week": latest.get("week"),
        "generatedAt": latest.get("generatedAt"),
        "publishedAt": iso(),
        "model": MODEL,
        "categories": [{"key": k, "label": l, "emoji": e, "kind": "mood"} for k, l, e in MOODS] +
                      [{"key": k, "label": l, "emoji": e, "kind": "genre"} for k, l, e in GENRES],
        "mixes": mix_list,
        "songCategories": song_categories,
    }
    save_json("curation.json", data, compact=True)
    log(f"published curation.json: {len(mix_list)} mixes, {len(song_categories)} songs categorised")


# -------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["weekly", "tag-new", "status", "publish", "covers"])
    parser.add_argument("--force", action="store_true", help="weekly: run now even if not 2am or already done")
    args = parser.parse_args()
    os.makedirs(STATE, exist_ok=True)

    if args.command == "status":
        data = load_json("curation.json", {})
        print(json.dumps({k: v for k, v in data.items() if k != "songCategories"}, indent=1, ensure_ascii=False))
        print("songs categorised:", len(data.get("songCategories", {})))
        return 0

    if args.command == "weekly" and not args.force:
        # Cron fires at both 06:00 and 07:00 UTC so one of them is 02:00 in New York whatever the DST.
        if dt.datetime.now(LOCAL_TZ).hour != 2:
            return 0

    with open(os.path.join(STATE, "curator.lock"), "w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            log(f"{args.command}: another run is in progress")
            return 0
        songs = read_library()
        if args.command == "publish":
            publish(load_json("song-tags.json", {}), songs)
            return 0
        if args.command == "covers":  # redraw this week's covers without asking Claude again
            history = load_json("history.json", [])
            if history:
                build_covers(history[-1]["mixes"], NAVIDROME_DB, os.path.join(STATE, "covers"), log)
                save_json("history.json", history)
            publish(load_json("song-tags.json", {}), songs)
            return 0
        tags = tag_songs(songs, only_new=True)
        if args.command == "weekly":
            build_weekly(songs, tags, force=args.force)
        publish(tags, songs)
    return 0


if __name__ == "__main__":
    sys.exit(main())
