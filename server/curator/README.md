# Ondo curator (decknas)

Builds Ondo's weekly **Made for you** mixes and the mood/genre categories behind the playlist filter
chips. Claude Sonnet 5.5 at medium effort does the curation through the `claude` CLI with tools turned
off. Runs on decknas from `/home/nicknas/tools/ondo-curator` (a copy of this directory).

| File | What it does |
| --- | --- |
| `curator.py` | Reads Navidrome's database **read-only**. It tags new songs (1–4 moods and one genre family each), plans and sequences the mixes, and writes `state/curation.json`. |
| `covers.py` | Draws a 640px cover per mix each week: album art from a mix song, duotoned in the mix colour, with the title (Google Sans). No Claude calls. `curator.py covers` redraws this week's. |
| `api.py` | Serves `state/curation.json` at `GET /v1/curation` on `127.0.0.1:8793`, with ETag and gzip. Callers authenticate with their Navidrome login (Subsonic `u`/`t`/`s` as `X-Navidrome-*` headers), which it checks against Navidrome's `ping`. It stores no credentials. |

Endpoints: `GET /v1/curation`, `GET /v1/covers/<slot>.jpg`. Public routes: `https://decknas.duckdns.org/ondo-curation/…`
(Caddy, `handle_path /ondo-curation*`) and the same path on the Funnel `:10000` host. Add Funnel paths with
`tailscale funnel --bg --https=10000 --set-path=…`. `tailscale serve` turns Funnel off for the whole port.

## Schedule (user crontab)

```
0 6,7 * * 1  curator.py weekly    # Mondays 02:00 America/New_York (the script skips the hour that isn't 2am)
45 7 * * *   curator.py tag-new   # categorise songs added since the last run
*/5 * * * *  health check || start api.py
```

## How mixes reach the app

The app (`CurationManager`) fetches the curation and makes each mix a **real Navidrome playlist** using the
user's own session. The playlist comment ends with a marker, `[ondo-mix:<slot>:<week>:<emoji>:<color>]`.
Each week the same playlist ids get new names and songs, so pins and downloads stay in place. Every device
recognises the marker, so no device creates its own copy. The server never writes to Navidrome.

## Manual runs

```
python3 curator.py weekly --force   # rebuild this week's mixes now
python3 curator.py tag-new          # categorise new songs only
python3 curator.py status           # show the current mixes
```

Logs: `state/curator.log` (one line per Claude call with token use and cost), `state/cron.log`.
State: `song-tags.json` (per-song categories), `history.json` (last 12 weeks, used to keep mixes fresh).
