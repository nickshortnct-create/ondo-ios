# Ondo 1.0.63 (963)

Weekly mixes picked by Claude, mood and genre filters in playlists, and search inside playlists.

## Made for you (home screen)

- **Two new rows at the top of Library: Your mood mixes and Your genre mixes.** Every Monday at 2:00 AM
  (New York time), Claude (Sonnet 5.5, medium effort) on decknas builds 6 mood mixes and 4 genre mixes
  from your library. They lean toward artists you play, mix in songs you haven't heard much, and avoid
  repeating last week's picks. Each mix gets its own title, description, emoji and colour, and the
  themes rotate weekly.
- Mixes are ordinary playlists on your server. You can download them, and Android Auto, the watch and
  other Subsonic apps see them too. Each week's mix replaces the previous week's in the same playlist,
  so a downloaded or pinned mix stays where it is.
- **Save a copy** at the top of a mix keeps the current version as a normal playlist (for example
  "Iron Rage · Oct 12") so it isn't replaced next Monday.
- Mixes appear in their own rows and are left out of the regular Playlists row. Pull to refresh on
  Library also checks for new mixes.

## Playlists

- **Search button** in a playlist's top bar. It opens a search field that filters the playlist as you
  type. Every word has to match the song title, artist or album, ignoring accents and case.
- **Mood and genre chips** under the play buttons, like Spotify's Liked Songs: Chill, Relaxing, Hype,
  Gym, Party, Feel Good, Focus, Late Night, Drive, In My Feels, Love and Throwback, followed by the
  playlist's genres (Hip-Hop, R&B, Electronic…). Only chips that would narrow this playlist appear.
  Tap a chip to filter and tap it again to clear it. Chips and search work together.
- Play and Shuffle play the filtered songs, and a "12 of 87 songs" line shows how far the list is
  narrowed.
- Claude categorises every song in the library on decknas, and new songs are categorised nightly.
  Songs added since the last run use their genre tags until then.

## Server side (decknas)

- New `server/curator`: `curator.py` runs on cron and reads Navidrome's database read-only. `api.py`
  serves the result at `/ondo-curation` and accepts only requests carrying your Navidrome login. The
  server stores no music-server password. The app creates and updates the mix playlists with your own
  login. See `server/curator/README.md`.
- The first run categorised all 5,739 songs and built week 2026-W42's mixes: Iron Rage, After Midnight
  Haze, Cuffing Season Blues, Last Warm Days, Haunted Pregame, Old Tapes New Fall, Rage Season, Pen
  Game, Velvet Hours and Circuit Bloom.

## Wear OS

The watch app is unchanged. `Ondo-Wear.apk` 1.0.62 is still the current watch build, and the new mix
playlists appear in its Playlists list.

## Verification

- `:androidApp:assembleRelease` builds. The APK is signed with the Ondo release certificate
  `fac61745…91033b9c`, the same as 1.0.62, so it installs over it as an update.
- New `CurationTest` (5 tests) passes. It covers the mix marker round trip and hiding it from comments,
  which chips appear, combined search and chip filtering that keeps playlist positions, the genre-tag
  fallback, and the week labels.
- API 35 phone emulator, fresh install against the QA fixture server
  (`scripts/qa/ondo-search-fixtures.py`, which now serves a curation and supports playlist writes):
  - The app fetched the curation and created three mix playlists with the right songs, names and
    `[ondo-mix:…]` markers.
  - Library showed the "Your mood mixes" and "Your genre mixes" rows. The Playlists tab lists the
    mixes alongside the regular playlist.
  - The 6-song playlist showed the search button and Chill, Gym, Hip-Hop and R&B chips. R&B appeared
    because the fixture has two R&B songs. Tapping Gym showed "2 of 6 songs" (QA Gym Anthem, QA Lift Off).
  - Not verified on the emulator: the software-rendered emulator kept crashing, so typing in the
    search field and Save a copy weren't run on screen. The search matching is covered by the unit tests.
- Server: the first curator run tagged 5,739 songs and built 10 mixes (51 Sonnet 5.5 calls). `api.py`
  returned 401 without a valid Navidrome login, and with a stand-in Navidrome ping it returned 200 with
  an ETag, 304 for a matching If-None-Match, and gzip (211 KB → 119 KB).
  `https://decknas.duckdns.org/ondo-curation/health` returns 200.
- APK SHA-256: phone `21d9c01a40319af638739df52d4c3b07bc0f281377ba97625652de2640ed5ee9`.
