# Ondo 1.0.65 (965)

Cover art stays put: covers no longer go blank after a while or when decknas can't be reached.

## Cover art

- **Covers are kept in the app's own storage.** They used to live in Android's cache folder, which
  the system empties whenever it wants space. Covers for downloaded songs were stored there too, so
  downloads could lose their artwork. Covers now go in the app's private files, which Android only
  removes if you clear the app's storage or uninstall it. **Settings → Data & storage → Clear image
  cache** still clears them.
- **Covers are saved per album, song and artist.** Navidrome adds a version stamp to each cover id
  and changes it whenever an album is edited or rescanned. decknas's tag and cover jobs edit albums
  often, and each change left the saved cover behind, so the app needed the server again to show
  anything. Covers are now saved under the album, song or artist id without the stamp.
- **New artwork still shows up.** When the stamp changes, the app downloads the cover once more.
  If that fails, for example offline or while decknas is down, it shows the saved cover and tries
  again next time.
- The first time you open 1.0.65 it downloads covers again as you browse, because the old cache
  can't be reused. The old cache is deleted.

## Verification

- `:composeApp:testAndroidHostTest`: all 88 tests pass, including 4 new ones for splitting cover
  ids and saving cover versions across restarts. `:androidApp:assembleRelease` builds, signed with
  the Ondo release certificate `fac61745…91033b9c` (the same as 1.0.64).
- API 35 emulator, fresh install, against the QA fixture server. `scripts/qa/ondo-search-fixtures.py`
  has a new `/qa/cover` control that sets a Navidrome-style versioned cover id, changes the artwork
  colour, and can make cover requests fail:
  - Version 1 (red): fetched once and saved under `files/cover_art/`.
  - App restarted, same version: the red cover showed with no cover request to the server.
  - Version 2 (blue): fetched once and shown.
  - Version 3 with cover requests failing (503) and the app's cache folder emptied: the saved blue
    cover still showed. Before this change the cover would have been blank.
  - Cover requests working again: the app fetched version 3 and showed it (green).
- Not tested on iOS (no Mac build here). The iOS code only chooses the folder, using the same
  Foundation call as the app's settings store.
- APK SHA-256: `9d06e33e9329c4db4ca3c347ef6ba1d13947359da345e7a23b07e520b5e89136`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
