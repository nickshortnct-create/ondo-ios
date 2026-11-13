#!/usr/bin/env python3
"""Local-only fake Subsonic/downloader endpoints for Ondo emulator smoke tests."""
import argparse
import io
import os
import tempfile
import json
import math
from pathlib import Path
import struct
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
import wave
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(os.environ.get("ONDO_QA_DIR", str(Path(tempfile.gettempdir()) / "ondo-qa")))
ROOT.mkdir(parents=True, exist_ok=True)
PORT = int(os.environ.get("ONDO_QA_PORT", "8765"))
STARTED = time.monotonic()
REQUESTS = []
ADDED_AT = None
REQUEST_DELAY = 0
IMPORT_DELAY = 5
WAVE = io.BytesIO()
with wave.open(WAVE, 'wb') as audio:
    audio.setnchannels(1)
    audio.setsampwidth(2)
    audio.setframerate(22050)
    audio.writeframes(b''.join(struct.pack('<h', int(math.sin(i * 2 * math.pi * 440 / 22050) * 1800)) for i in range(22050 * 12)))
AUDIO = WAVE.getvalue()
def png_chunk(kind, payload):
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff)
def make_art(rgb=None):
    pixel = (lambda y: bytes(rgb)) if rgb else (lambda y: bytes((64+y,80+y,160)))
    art = b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,2,0,0,0))
    return art + png_chunk(b'IDAT',zlib.compress(b''.join(b'\0'+pixel(y)*64 for y in range(64)))) + png_chunk(b'IEND',b'')
ART = make_art()
# Cover cache QA (/qa/cover): Navidrome-style versioned ids, per-version colour, simulated failures.
COVER = dict(version=None, rgb=None, fail=False)
SONG = dict(id='qa-song-1', parent='qa-album-1', isDir=False, title='QA Morning Light',
            album='QA Family Album', artist='QA Family Artist', artistId='qa-artist-1', albumId='qa-album-1',
            track=1, year=2026, genre='Test', size=len(AUDIO), contentType='audio/wav', suffix='wav',
            duration=12, bitRate=352, path='QA Family Artist/QA Family Album/QA Morning Light.wav',
            isVideo=False, type='music', coverArt='qa-cover', created='2026-10-01T00:00:00Z')
SONG2 = dict(SONG, id='qa-song-2', title='QA Evening Song', track=2)
NEWSONG = dict(SONG, id='qa-song-new', title='QA New Song', album='QA New Album', albumId='qa-album-new')
ALBUM = dict(id='qa-album-1', name='QA Family Album', artist='QA Family Artist', artistId='qa-artist-1',
             songCount=2, duration=24, coverArt='qa-cover', created='2026-10-01T00:00:00Z', year=2026, genre='Test')
NEWALBUM = dict(ALBUM, id='qa-album-new', name='QA New Album', songCount=1, duration=12)
ARTIST = dict(id='qa-artist-1', name='QA Family Artist', albumCount=1, coverArt='qa-cover')
PLAYLIST = dict(id='qa-playlist-1', name='QA Family Favorites', owner='qa', public=False, songCount=2,
                duration=24, comment='Fake fixture only', coverArt='qa-cover',
                created='2026-10-01T00:00:00Z', changed='2026-10-01T00:00:00Z')
# Extra playlist-only songs so the playlist filter chips and search have something to work with.
EXTRA_SONGS = [dict(SONG, id=f'qa-song-{n}', title=title, track=n, genre=genre)
               for n, title, genre in [(3, 'QA Gym Anthem', 'Trap'), (4, 'QA Rainy Window', 'Ambient'),
                                       (5, 'QA Lift Off', 'Drill'), (6, 'QA Slow Dance', 'R&B')]]
PLAYLIST_SONGS = [SONG, SONG2] + EXTRA_SONGS
PLAYLIST['songCount'] = len(PLAYLIST_SONGS)
# Playlists the app creates (weekly mixes, saved copies), kept in memory like a real server would.
# Two more of the user's own start out there so "Add to playlist" ordering has something to sort.
CREATED = {pid: dict(ids=['qa-song-1'], meta=dict(PLAYLIST, id=pid, name=name, comment='', owner='qa'))
           for pid, name in [('qa-own-road', 'QA Road Trip'), ('qa-own-zebra', 'QA Zebra')]}
# Navidrome drops owner, comment, coverArt and public from playlists for clients in
# ND_SUBSONIC_MINIMALCLIENTS (decknas lists Ondo). Mirrored here: missing it let a duplicate-mix bug through.
MINIMAL_CLIENTS = {'Ondo', 'Navic', 'wavio'}
COVER_UPLOADS = {}
CURATION = dict(
    version=1, week='2026-W42', generatedAt='2026-10-16T00:00:00Z', model='qa',
    categories=[dict(key='chill', label='Chill', emoji='🌊', kind='mood'), dict(key='gym', label='Gym', emoji='💪', kind='mood'),
                dict(key='hip-hop', label='Hip-Hop', emoji='🎤', kind='genre'), dict(key='rnb', label='R&B', emoji='💜', kind='genre')],
    mixes=[dict(slot='mood-1', section='mood', title='QA Iron Rage', description='Fixture gym mix.', emoji='💪', color='#FF5252',
                songIds=['qa-song-3', 'qa-song-5', 'qa-song-1'], cover='qa1'),
           dict(slot='mood-2', section='mood', title='QA Night Haze', description='Fixture late-night mix.', emoji='🌙', color='#7C4DFF',
                songIds=['qa-song-4', 'qa-song-6']),
           dict(slot='genre-1', section='genre', title='QA Velvet Hours', description='Fixture R&B mix.', emoji='🕯️', color='#00BFA5',
                songIds=['qa-song-6', 'qa-song-2'])],
    songCategories={'qa-song-1': [0, 2], 'qa-song-2': [0, 3], 'qa-song-3': [1, 2], 'qa-song-4': [0], 'qa-song-5': [1, 2], 'qa-song-6': [0, 3]})


def all_songs():
    return {s['id']: s for s in [SONG, SONG2, NEWSONG] + EXTRA_SONGS}


def playlist_view(meta, ids):
    songs = [all_songs()[i] for i in ids if i in all_songs()]
    return dict(meta, songCount=len(songs), duration=sum(s['duration'] for s in songs)), songs


TRACK = dict(id='qa-new-track', name='QA New Song', title='QA New Song', artist='QA Family Artist',
             album='QA New Album', duration_ms=12000, image_url='')

def imported():
    return ADDED_AT is not None and time.monotonic() - ADDED_AT > IMPORT_DELAY


def write_preferences(path, offline):
    """Emit fake settings for a fresh emulator install; never read live settings."""
    settings = ET.Element('map')
    for name, value in [('instanceUrl', f'http://127.0.0.1:{PORT}'), ('username', 'qa'),
                        ('password', 'fake-test-only')]:
        ET.SubElement(settings, 'string', name=name).text = value
    ET.SubElement(settings, 'boolean', name='enableScrobbling', value='false')
    ET.SubElement(settings, 'int', name='offlineMode', value='1' if offline else '0')
    for service in ['Lidarr', 'SoulSync', 'Squid', 'YouTube', 'AudioMuse', 'Connect',
                    'ListenBrainz', 'KHInsider']:
        config = dict(url=f'http://127.0.0.1:{PORT}' if service in ['Lidarr', 'SoulSync', 'Squid', 'YouTube'] else '',
                      key='qa-key' if service in ['Lidarr', 'SoulSync', 'Squid', 'YouTube'] else '',
                      enabled=service == 'SoulSync', profile='1', root='/qa', quality='1', metadata='1')
        ET.SubElement(settings, 'string', name=f'ondo.http://127.0.0.1:{PORT}|qa.{service}').text = json.dumps(config)
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(settings).write(target, encoding='utf-8', xml_declaration=True)

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def send(self, data, content='application/json', status=200):
        if content == 'application/json' and self.headers.get('Authorization', '').startswith('Bearer ') and '/rest/' not in self.path:
            data = dict(success=True, data=data)
        raw = data if isinstance(data, bytes) else json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', content)
        self.send_header('Content-Length', str(len(raw)))
        # http.server closes after every response; say so, or OkHttp reuses the dead connection.
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self):
        self.do_GET()

    def do_DELETE(self):
        self.do_GET()

    def do_GET(self):
        global ADDED_AT, REQUEST_DELAY, IMPORT_DELAY
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        length = int(self.headers.get('Content-Length', 0))
        raw_body = self.rfile.read(length) if length else b''
        is_json = 'json' in self.headers.get('Content-Type', 'application/json')
        body = json.loads(raw_body or b'{}') if raw_body and is_json else {}
        event = dict(at=round(time.monotonic()-STARTED, 3), method=self.command, path=parsed.path,
                     query={k:v for k,v in query.items() if k in ['query','q','id','offset','size']}, body=body)
        REQUESTS.append(event)
        with (ROOT/'requests.jsonl').open('a') as log:
            log.write(json.dumps(event)+'\n')
        if parsed.path.rstrip('/').endswith('/ondo-curation/v1/curation'):
            if not self.headers.get('X-Navidrome-Token'):
                return self.send({'error': 'Navidrome login required'}, status=401)
            return self.send(CURATION)
        if '/ondo-curation/v1/covers/' in parsed.path:
            return self.send(ART, 'image/png')
        if parsed.path == '/qa/playlists':
            return self.send(dict(CREATED, covers=COVER_UPLOADS))
        # Navidrome's own API: session login and playlist cover upload/removal.
        if parsed.path.endswith('/auth/login') and self.command == 'POST':
            if body.get('password') != 'fake-test-only':
                return self.send({'error': 'Invalid username or password'}, status=401)
            return self.send({'token': 'qa-nd-token', 'username': body.get('username')})
        if '/api/playlist/' in parsed.path and parsed.path.endswith('/image'):
            if self.headers.get('X-ND-Authorization') != 'Bearer qa-nd-token':
                return self.send({'error': 'unauthorized'}, status=401)
            pid = parsed.path.split('/api/playlist/', 1)[1].rsplit('/image', 1)[0]
            if self.command == 'POST':
                if b'name="image"' not in raw_body or b'\xff\xd8' not in raw_body:
                    return self.send({'error': 'missing image file'}, status=400)
                COVER_UPLOADS[pid] = len(raw_body)
            else:
                COVER_UPLOADS.pop(pid, None)
            REQUESTS[-1]['cover'] = dict(playlist=pid, bytes=len(raw_body))
            return self.send({'status': 'ok'})
        if parsed.path == '/qa/cover':
            COVER.update({k: body[k] for k in ['version', 'rgb', 'fail'] if k in body})
            if COVER['version'] is not None:
                for item in [SONG, SONG2, NEWSONG, ALBUM, NEWALBUM, ARTIST, PLAYLIST] + EXTRA_SONGS:
                    item['coverArt'] = f"al-qacover_{COVER['version']}"
            return self.send(COVER)
        if parsed.path == '/qa/events':
            return self.send(REQUESTS)
        if parsed.path == '/qa/reset':
            REQUESTS.clear()
            ADDED_AT = None
            REQUEST_DELAY = min(max(float(body.get('request_delay', 0)), 0), 10)
            IMPORT_DELAY = min(max(float(body.get('import_delay', 5)), 0), 60)
            if body.get('title'):
                TRACK.update(id=body.get('track_id', 'qa-pending-track'), name=body['title'], title=body['title'])
                NEWSONG.update(id='qa-song-pending', title=body['title'])
            return self.send({})
        if '/rest/' in parsed.path:
            endpoint = parsed.path.rsplit('/', 1)[1].removesuffix('.view')
            data = dict(status='ok', version='1.16.1', type='navidrome', serverVersion='qa', openSubsonic=True)
            albums = [ALBUM, NEWALBUM] if imported() else [ALBUM]
            songs = [SONG, SONG2, NEWSONG] if imported() else [SONG, SONG2]
            if endpoint in ['stream', 'download']:
                return self.send(AUDIO, 'audio/wav')
            if endpoint == 'getCoverArt':
                if COVER['fail']:
                    return self.send({'error': 'cover unavailable'}, status=503)
                return self.send(make_art(COVER['rgb']) if COVER['rgb'] else ART, 'image/png')
            if endpoint in ['search3','search2']:
                text = query.get('query', [''])[0].lower()
                def fits(item):
                    return text in ' '.join(str(item.get(key,'')) for key in ['name','title','artist']).lower()
                data[endpoint.replace('search','searchResult')] = dict(artist=[ARTIST] if fits(ARTIST) else [],
                    album=[a for a in albums if fits(a)], song=[s for s in songs if fits(s)])
            elif endpoint in ['getAlbumList2', 'getAlbumList']:
                offset = int(query.get('offset', ['0'])[0]); size = int(query.get('size', ['500'])[0])
                data['albumList2' if endpoint.endswith('2') else 'albumList'] = dict(album=albums[offset:offset+size])
            elif endpoint == 'getAlbum':
                chosen = NEWALBUM if query.get('id') == ['qa-album-new'] else ALBUM
                data['album'] = dict(chosen, song=[s for s in songs if s['albumId']==chosen['id']])
            elif endpoint == 'getSong':
                data['song'] = next((s for s in songs if s['id']==query.get('id',[''])[0]), SONG)
            elif endpoint == 'getArtists':
                data['artists'] = dict(ignoredArticles='The', index=[dict(name='Q', artist=[ARTIST])])
            elif endpoint == 'getArtist':
                data['artist'] = dict(ARTIST, album=albums)
            elif endpoint in ['getArtistInfo', 'getArtistInfo2']:
                data['artistInfo2' if endpoint.endswith('2') else 'artistInfo'] = dict(biography='Generated test fixture', similarArtist=[])
            elif endpoint == 'getPlaylists':
                lists = [PLAYLIST] + [playlist_view(p['meta'], p['ids'])[0] for p in CREATED.values()]
                if query.get('c', [''])[0] in MINIMAL_CLIENTS:
                    lists = [{k: v for k, v in p.items() if k not in ['owner', 'comment', 'coverArt', 'public']} for p in lists]
                data['playlists'] = dict(playlist=lists)
            elif endpoint == 'getPlaylist':
                pid = query.get('id', [''])[0]
                if pid in CREATED:
                    meta, entries = playlist_view(CREATED[pid]['meta'], CREATED[pid]['ids'])
                    data['playlist'] = dict(meta, entry=entries)
                else:
                    data['playlist'] = dict(PLAYLIST, entry=PLAYLIST_SONGS)
            elif endpoint == 'createPlaylist':
                pid = f'qa-created-{len(CREATED) + 1}'
                CREATED[pid] = dict(ids=list(query.get('songId', [])), meta=dict(
                    PLAYLIST, id=pid, name=query.get('name', ['Untitled'])[0], comment='', owner='qa'))
                data['playlist'] = dict(playlist_view(CREATED[pid]['meta'], CREATED[pid]['ids'])[0])
            elif endpoint == 'updatePlaylist':
                entry = CREATED.get(query.get('playlistId', [''])[0])
                if entry:
                    for index in sorted((int(i) for i in query.get('songIndexToRemove', [])), reverse=True):
                        if 0 <= index < len(entry['ids']):
                            entry['ids'].pop(index)
                    entry['ids'] += query.get('songIdToAdd', [])
                    for key in ['name', 'comment']:
                        if key in query:
                            entry['meta'][key] = query[key][0]
                    if 'public' in query:
                        entry['meta']['public'] = query['public'][0] == 'true'
                    entry['meta']['changed'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            elif endpoint == 'deletePlaylist':
                CREATED.pop(query.get('id', [''])[0], None)
            elif endpoint == 'getGenres':
                data['genres'] = dict(genre=[dict(value='Test', songCount=2, albumCount=1)])
            elif endpoint == 'getInternetRadioStations':
                data['internetRadioStations'] = dict(internetRadioStation=[])
            elif endpoint in ['getStarred','getStarred2']:
                data['starred2' if endpoint.endswith('2') else 'starred'] = dict(artist=[],album=[],song=[])
            elif endpoint == 'getLyrics':
                data['lyrics'] = dict(artist='',title='',value='')
            elif endpoint == 'getLyricsBySongId':
                data['lyricsList'] = dict(structuredLyrics=[])
            return self.send({'subsonic-response':data})
        if parsed.path.endswith('/search/tracks'):
            return self.send({'tracks':[TRACK] if 'qa' in body.get('query','').lower() else [], 'source':'qa'})
        if parsed.path.endswith('/search/artists'):
            return self.send({'artists':[dict(id='qa-external-artist',name='QA Discovery Artist')], 'source':'qa'})
        if parsed.path.endswith('/wishlist/process'):
            return self.send({'success':True})
        if parsed.path.endswith('/wishlist'):
            if self.command == 'POST':
                ADDED_AT = time.monotonic()
                time.sleep(REQUEST_DELAY)
                return self.send({'id':'qa-request-1', 'success':True})
            return self.send({'tracks':[]})
        if parsed.path.endswith('/downloads'):
            rows = []
            if ADDED_AT:
                complete = imported()
                rows = [dict(id='qa-task-1',title=TRACK['title'], track_name=TRACK['title'], artist_name='QA Family Artist',
                    status='completed' if complete else 'downloading', progress=100 if complete else 50)]
            return self.send({'downloads':rows})
        if parsed.path.endswith('/fallback/search'):
            return self.send({'items':[dict(id='QAvid123456',title='QA Video Song',channel='QA Channel',duration=12,thumbnail='')]})
        if parsed.path.endswith('/search'):
            return self.send([])
        if parsed.path.endswith('/queue'):
            return self.send({'records':[]})
        if parsed.path.endswith('/squid/tasks') or parsed.path.endswith('/fallback/tasks'):
            return self.send({'tasks':[]})
        return self.send({})

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-prefs', metavar='PATH', help='Write fake emulator preferences instead of serving')
    parser.add_argument('--offline', action='store_true', help='Force offline mode in emitted preferences')
    arguments = parser.parse_args()
    if arguments.write_prefs:
        write_preferences(arguments.write_prefs, arguments.offline)
        raise SystemExit(0)
    print(f'Fake music services on http://127.0.0.1:{PORT}; logs: {ROOT}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
