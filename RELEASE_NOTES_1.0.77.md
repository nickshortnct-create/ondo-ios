# Ondo 1.0.77 (977)

Downloaded songs now keep their cover art on the phone, so it shows right away without asking the server.

## Cover art for downloads

- **Fixed: downloaded playlists still loaded covers from the server.** Covers lived in the general
  image cache, and Ondo went back to the server, showing nothing until it answered, whenever Navidrome
  changed a cover's version stamp. Navidrome changes that stamp when a song's or album's record is
  updated, and 29% of the library's songs were updated in the last 7 days. That cache can also be
  cleared or evict old covers.
- **Each download now saves its song cover and album cover as permanent files.** The app, the mini
  player, the media notification, the widget and Android Auto all load covers the same way, so they
  use these files first. No database change.
- **Songs you've already downloaded get covers too.** About 15 seconds after launch, Ondo saves any
  that are missing, one at a time in the background. It copies them from the image cache when it can,
  and asks the server otherwise. If several fail in a row, it stops and tries again next launch.
- **New artwork still arrives.** If a cover's version changes, Ondo replaces the saved file in the
  background and keeps showing the old one until it has the new one. The new picture appears the next
  time Ondo starts. A missing cover is fetched on any connection, but replacing one waits for Wi-Fi.
- **Storage.** Removing a download also removes covers nothing else needs. "Clear image cache" leaves
  saved covers alone, and clearing all downloads removes them. Saved covers aren't included in the
  storage figures in settings. Expect a few hundred KB per song; Navidrome's own resized covers
  average 205 KB, and the High cover quality setting can make them larger.

## Other fixes

- **Fixed: some covers were fetched again and again.** About 8% of songs have no embedded art and
  aren't on a numbered disc, so they use the id `al-<album>_0`, while the album page uses
  `al-<album>_<timestamp>`. Ondo treated the two as different versions of one cover, so moving between
  them went back to the server each time, even for songs that aren't downloaded. `_0` now counts as
  "no version".
- **Fixed: starting without the app open could skip the cover cache.** If Android Auto, the playback
  service or a widget loaded an image before the app's screen appeared, Ondo could end up on a default
  image loader, without saved covers, until the process ended. The loader is now set up when the
  process starts. This comes from reading the code; it wasn't reproduced.
- **Covers get their own time limit during a download.** They shared 15 seconds with lyrics, and a
  cover that missed it wasn't tried again. Each now gets 30 seconds, and launch retries any failure.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass, including new tests for saving, replacing,
  restart, removal, image cache reuse and choosing the newest cover id. `:androidApp:assembleRelease`
  builds, signed with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `08b1328112051a28085f9a31720f8cd1d791230f5bb872a968c19e10850d3bb1`.
- On an Android 15 emulator with the mock Subsonic server. The first three checks ran on the build
  before the newest-id fix; the rest ran on this APK.
  - Downloading a two-song album saved one shared cover file, copied from the image cache with no
    extra server request.
  - With a new version stamp, cover requests returning 503 and the image cache wiped, the album page
    showed the saved cover with zero cover requests. Playback made none either, and the media
    notification took its colours from the saved cover.
  - With no network at all, the album page and mini player still showed the saved cover.
  - On Wi-Fi, the new artwork was downloaded once in the background. After a restart with cover
    requests failing, the album page and mini player showed it with zero cover requests. The earlier
    build failed this check because a stale song row hid the album's newer stamp, which led to the fix.
  - Deleting the saved cover files brought them back after launch, and deleting the album removed its
    cover.
- Control: on 1.0.76, the same version change with the cover already downloaded made one server
  request for it, while this build made none.
- Not tested:
  - On a phone or against the live Navidrome.
  - Android Auto and the widget.
  - Waiting for Wi-Fi before replacing a cover on mobile data.
  - The first launch backfill on a large library.
  - Per-song `mf-` and disc `dc-` covers on a device. The emulator library has one cover shared by
    every song, so these are covered only by unit tests.
- Not changed: songs that aren't downloaded still wait for the server when a cover's version changes,
  and a playlist's own header image isn't saved, so it still uses the image cache.

## Wear OS

Unchanged.
