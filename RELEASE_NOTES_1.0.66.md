# Ondo 1.0.66 (966)

Downloads now shows everything decknas is fetching, and requests that can't be found stop
spinning forever.

## Soulseek album downloads show up in Downloads

- **Albums you add through Lidarr now show live progress.** decknas downloads most Lidarr
  albums from Soulseek with Soularr, which never goes through Lidarr's own queue, so until now
  an album was invisible in Downloads until it reached the library. These downloads now appear
  under **Soulseek** with a progress bar and a line like "5 of 14 tracks downloaded from Soulseek".
- Finished and failed albums stay listed for two days.
- The feed comes from decknas' YouTube fallback service (`/api/v1/soularr/downloads`), so it
  uses the YouTube integration's address and key. Nothing new to set up.

## SoulSync requests that can't be found

- **SoulSync never gives up on a song.** A request it can't find on Soulseek stayed in its
  wishlist and showed as "Waiting for SoulSync…" in Downloads indefinitely. "Nun id change (sped
  up version)" and "turn up the speakers" had been doing that since September and early October.
- After 3 failed searches, a request now shows as **Music not found** with the number of searches.
- **Try YouTube** sends the song to the YouTube fallback and removes the SoulSync request.
- **Remove** drops the request from SoulSync. Pending requests can be removed too.
- SoulSync's API removes a request without adding it to its ignore list. A song that came from
  a synced Spotify playlist can come back on the next playlist sync if it is still missing.

## Server changes on decknas (not part of the APK)

- Soularr runs through `~/tools/soularr/soularr_runner.py`:
  - A priority pass every 2 minutes grabs albums that became wanted in the last 6 hours, so a new
    request starts within about 2 minutes instead of waiting behind the alphabetical backlog.
  - The main pass backs off albums that keep failing: daily retries after 3 failures in a row,
    weekly after 8.
  - It writes the progress file this release reads.
- `ytdlp-fallback-api.py` has the new `/api/v1/soularr/downloads` route.

## Verification

- `:composeApp:testAndroidHostTest`: all 90 tests pass, including 2 new ones:
  - SoulSync requests: a new request is pending, removable and has no YouTube option. After 3
    failed searches it shows as "Music not found" with Try YouTube and Remove. Running SoulSync
    tasks are not affected.
  - Soulseek album rows: progress comes from transferred bytes.
- `:androidApp:assembleRelease` builds, signed with the Ondo release certificate
  `fac61745…91033b9c` (the same as 1.0.65). The Wear OS app is unchanged (1.0.62).
- Server: `/api/v1/soularr/downloads` was checked against a live Soulseek download (Yeat –
  Luh Leakyz Vol. 1). It reported it as downloading with byte progress, and it rejects requests
  without the API key. The priority pass found and queued that album 6 seconds after it was
  marked new, and Lidarr imported all 14 tracks.
- Not covered: no emulator run of the new Downloads rows. The QA fixture server has no Soulseek or
  SoulSync wishlist fixtures yet.
