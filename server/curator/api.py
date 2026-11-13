#!/usr/bin/env python3
"""Serves the Ondo curator's output (state/curation.json) to the app.

GET /v1/curation   the weekly mixes and the song category index
GET /v1/covers/<slot>.jpg   a mix's cover art
GET /health        liveness for the cron watchdog (no auth)

Requests authenticate with the user's Navidrome login as Subsonic token auth,
either X-Navidrome-User/-Token/-Salt headers (the app's gateway convention) or
u/t/s query parameters. They're checked against Navidrome's own ping, so this
service holds no credentials. Supports ETag/If-None-Match and gzip.
"""
import gzip
import hashlib
import json
import os
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
STATE = os.environ.get("ONDO_CURATOR_STATE", os.path.join(HERE, "state"))
PORT = int(os.environ.get("ONDO_CURATOR_PORT", "8793"))
NAVIDROME = os.environ.get("NAVIDROME_INTERNAL_URL", "http://127.0.0.1:4533/navidrome")
AUTH_TTL_S = 15 * 60

_auth_cache = {}
_auth_lock = threading.Lock()
_file_cache = {"mtime": None, "body": b"", "gz": b"", "etag": ""}
_file_lock = threading.Lock()


def navidrome_accepts(user, token, salt):
    key = hashlib.sha256(f"{user}\0{token}\0{salt}".encode()).hexdigest()
    with _auth_lock:
        if _auth_cache.get(key, 0) > time.time():
            return True
    query = urllib.parse.urlencode({"u": user, "t": token, "s": salt, "v": "1.16.1", "c": "ondo-curator", "f": "json"})
    try:
        with urllib.request.urlopen(f"{NAVIDROME}/rest/ping.view?{query}", timeout=10) as r:
            ok = json.load(r).get("subsonic-response", {}).get("status") == "ok"
    except Exception:
        return False
    if ok:
        with _auth_lock:
            if len(_auth_cache) > 256:
                _auth_cache.clear()
            _auth_cache[key] = time.time() + AUTH_TTL_S
    return ok


def curation_file():
    path = os.path.join(STATE, "curation.json")
    mtime = os.stat(path).st_mtime
    with _file_lock:
        if _file_cache["mtime"] != mtime:
            with open(path, "rb") as f:
                body = f.read()
            _file_cache.update(mtime=mtime, body=body, gz=gzip.compress(body, 6),
                               etag='"' + hashlib.sha256(body).hexdigest()[:32] + '"')
        return _file_cache["body"], _file_cache["gz"], _file_cache["etag"]


class Handler(BaseHTTPRequestHandler):
    server_version = "ondo-curator/1"

    def log_message(self, fmt, *args):
        pass

    def send(self, status, body=b"", content_type="application/json", headers=None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "private, no-cache")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def error(self, status, message):
        self.send(status, json.dumps({"error": message}).encode())

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        path = url.path.rstrip("/")
        if path == "/health":
            return self.send(200, b'{"ok":true}')
        cover = path.startswith("/v1/covers/") and path.removeprefix("/v1/covers/")
        if path != "/v1/curation" and not (cover and cover.endswith(".jpg") and "/" not in cover and ".." not in cover):
            return self.error(404, "not found")
        q = urllib.parse.parse_qs(url.query)
        user = self.headers.get("X-Navidrome-User") or (q.get("u") or [""])[0]
        token = self.headers.get("X-Navidrome-Token") or (q.get("t") or [""])[0]
        salt = self.headers.get("X-Navidrome-Salt") or (q.get("s") or [""])[0]
        if not (user and token and salt) or not navidrome_accepts(user, token, salt):
            return self.error(401, "Navidrome login required")
        if cover:
            try:
                with open(os.path.join(STATE, "covers", cover), "rb") as f:
                    data = f.read()
            except FileNotFoundError:
                return self.error(404, "no cover")
            etag = '"' + hashlib.sha256(data).hexdigest()[:16] + '"'
            if etag in (self.headers.get("If-None-Match") or ""):
                return self.send(304, headers={"ETag": etag})
            return self.send(200, data, "image/jpeg", {"ETag": etag})
        try:
            body, gz, etag = curation_file()
        except FileNotFoundError:
            return self.error(503, "The first curation hasn't finished yet")
        if etag in (self.headers.get("If-None-Match") or ""):
            return self.send(304, headers={"ETag": etag})
        if "gzip" in (self.headers.get("Accept-Encoding") or ""):
            return self.send(200, gz, headers={"ETag": etag, "Content-Encoding": "gzip", "Vary": "Accept-Encoding"})
        return self.send(200, body, headers={"ETag": etag, "Vary": "Accept-Encoding"})


def main():
    os.makedirs(STATE, exist_ok=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
