"""Cover art for the weekly mixes, in the style of Spotify's Daily Mix.

The background is album art from one of the mix's songs, duotoned in the mix colour. The mix kind
and title are set over it. Without usable art, a mood-shaped gradient is drawn instead. Output is a
640px JPEG per mix slot. No Claude calls; it only reads files.
"""
import colorsys
import hashlib
import io
import math
import os
import random
import sqlite3
import subprocess

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

SIZE = 640
MUSIC_ROOT = os.environ.get("NAVIDROME_MUSIC_ROOT", "/mnt/nas/media/Music")
FONT_PATHS = [
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "google_sans.ttf"),
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
# Moods that should feel hard-edged versus soft when there's no album art to use.
SHARP_MOODS = {"hype", "gym", "party"}


def hex_rgb(value):
    value = (value or "#7C4DFF").lstrip("#")
    try:
        return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))
    except ValueError:
        return (124, 77, 255)


def shade(rgb, lightness):
    h, l, s = colorsys.rgb_to_hls(*(c / 255 for c in rgb))
    r, g, b = colorsys.hls_to_rgb(h, max(0.0, min(1.0, lightness)), min(1.0, s * 1.1))
    return (int(r * 255), int(g * 255), int(b * 255))


def font(size, bold=True):
    for path in FONT_PATHS:
        if os.path.exists(path):
            try:
                f = ImageFont.truetype(path, size)
                if bold:
                    try:
                        f.set_variation_by_name("Bold")
                    except Exception:
                        pass
                return f
            except OSError:
                continue
    return ImageFont.load_default()


def embedded_art(rel_path):
    """The first embedded picture in a music file, via ffmpeg (handles FLAC, MP3, M4A...)."""
    path = rel_path if rel_path.startswith("/") else os.path.join(MUSIC_ROOT, rel_path)
    if not os.path.exists(path):
        return None
    try:
        out = subprocess.run(
            ["ffmpeg", "-v", "error", "-i", path, "-an", "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-"],
            capture_output=True, timeout=30,
        ).stdout
        if out:
            return Image.open(io.BytesIO(out)).convert("RGB")
    except (subprocess.TimeoutExpired, OSError, Image.UnidentifiedImageError):
        pass
    folder = os.path.dirname(path)
    for name in ("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg"):
        candidate = os.path.join(folder, name)
        if os.path.exists(candidate):
            try:
                return Image.open(candidate).convert("RGB")
            except (OSError, Image.UnidentifiedImageError):
                pass
    return None


def duotone(img, rgb):
    gray = ImageOps.autocontrast(ImageOps.grayscale(ImageOps.fit(img, (SIZE, SIZE), Image.LANCZOS)), cutoff=2)
    # A light blur keeps lettering in the artwork from competing with the mix title.
    gray = gray.filter(ImageFilter.GaussianBlur(2.2))
    return ImageOps.colorize(gray, black=shade(rgb, 0.12), white=shade(rgb, 0.74), mid=shade(rgb, 0.42))


def generated_background(rgb, moods, seed):
    rng = random.Random(seed)
    base = Image.new("RGB", (SIZE, SIZE), shade(rgb, 0.25))
    draw = ImageDraw.Draw(base)
    for y in range(SIZE):  # vertical gradient
        t = y / SIZE
        draw.line([(0, y), (SIZE, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(shade(rgb, 0.55), shade(rgb, 0.15))))
    layer = Image.new("RGBA", (SIZE, SIZE))
    ld = ImageDraw.Draw(layer)
    if SHARP_MOODS.intersection(moods):
        for _ in range(7):  # diagonal slashes
            x = rng.randint(-SIZE, SIZE)
            w = rng.randint(30, 110)
            colour = shade(rgb, rng.uniform(0.45, 0.8)) + (rng.randint(60, 140),)
            ld.polygon([(x, SIZE), (x + w, SIZE), (x + w + SIZE, 0), (x + SIZE, 0)], fill=colour)
    else:
        for _ in range(6):  # soft blurred orbs
            r = rng.randint(120, 300)
            cx, cy = rng.randint(0, SIZE), rng.randint(0, SIZE)
            colour = shade(rgb, rng.uniform(0.5, 0.85)) + (rng.randint(70, 150),)
            ld.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour)
        layer = layer.filter(ImageFilter.GaussianBlur(40))
    base.paste(layer, (0, 0), layer)
    return base


def wrap(draw, text, fnt, width):
    words, lines, line = text.split(), [], ""
    for word in words:
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=fnt) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def render(mix, art):
    rgb = hex_rgb(mix.get("color"))
    seed = int(hashlib.sha256((mix["slot"] + mix["title"]).encode()).hexdigest(), 16)
    img = duotone(art, rgb) if art is not None else generated_background(rgb, set(mix.get("moods", [])), seed)

    # Darken toward the bottom so the title always reads.
    shadow = Image.new("L", (SIZE, SIZE))
    sd = ImageDraw.Draw(shadow)
    for y in range(SIZE):
        bottom = 215 * max(0.0, (y / SIZE - 0.3) / 0.7) ** 1.3
        top = 110 * max(0.0, 1 - y / (SIZE * 0.22))
        sd.line([(0, y), (SIZE, y)], fill=int(max(bottom, top)))
    img = Image.composite(Image.new("RGB", (SIZE, SIZE), (0, 0, 0)), img, shadow)

    draw = ImageDraw.Draw(img)
    pad = 40
    label = "MOOD MIX" if mix.get("section") == "mood" else "GENRE MIX"
    draw.text((pad, pad), label, font=font(30), fill=(255, 255, 255))
    draw.rectangle([pad, pad + 44, pad + 56, pad + 50], fill=shade(rgb, 0.7))

    title = mix["title"]
    size = 92
    while size > 44:
        fnt = font(size)
        lines = wrap(draw, title, fnt, SIZE - 2 * pad)
        if len(lines) <= 3 and all(draw.textlength(l, font=fnt) <= SIZE - 2 * pad for l in lines):
            break
        size -= 6
    line_h = int(size * 1.05)
    y = SIZE - pad - 18 - line_h * len(lines)
    for l in lines:
        draw.text((pad, y), l, font=fnt, fill=(255, 255, 255))
        y += line_h
    # Colour band along the bottom, like Spotify's mixes.
    draw.rectangle([0, SIZE - 18, SIZE, SIZE], fill=shade(rgb, 0.6))
    return img


def song_paths(db_path, song_ids):
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=30)
    try:
        rows = {}
        for i in range(0, len(song_ids), 500):
            chunk = song_ids[i:i + 500]
            q = f"select id, path, album_id, has_cover_art from media_file where id in ({','.join('?' * len(chunk))})"
            for sid, path, album_id, has_art in con.execute(q, chunk):
                rows[sid] = (path, album_id, has_art)
        return rows
    finally:
        con.close()


def build_covers(mixes, db_path, out_dir, log=print):
    """Writes <slot>.jpg for each mix and sets mix["cover"] to a version string. Picks a different album per mix."""
    os.makedirs(out_dir, exist_ok=True)
    paths = song_paths(db_path, [sid for m in mixes for sid in m["songIds"]])
    used_albums = set()
    for mix in mixes:
        art = None
        for sid in mix["songIds"]:
            path, album_id, has_art = paths.get(sid, (None, None, 0))
            if not path or not has_art or album_id in used_albums:
                continue
            art = embedded_art(path)
            if art is not None and min(art.size) >= 200:
                used_albums.add(album_id)
                break
            art = None
        try:
            img = render(mix, art)
            buf = io.BytesIO()
            img.save(buf, "JPEG", quality=86, optimize=True, progressive=True)
            data = buf.getvalue()
            with open(os.path.join(out_dir, f"{mix['slot']}.jpg.tmp"), "wb") as f:
                f.write(data)
            os.replace(os.path.join(out_dir, f"{mix['slot']}.jpg.tmp"), os.path.join(out_dir, f"{mix['slot']}.jpg"))
            mix["cover"] = hashlib.sha256(data).hexdigest()[:16]
        except Exception as e:  # a cover is a nice-to-have; never fail the week over it
            log(f"cover for {mix['slot']} failed: {e}")
    return mixes
