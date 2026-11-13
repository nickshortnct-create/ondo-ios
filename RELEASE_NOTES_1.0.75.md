# Ondo 1.0.75 (975)

Plays are recorded with the time you actually played them, including plays made offline.

## Scrobbling

- **Fixed: Navidrome ignored the time on every play.** Ondo sent play times in a format the server
  rejects, so each play was dated when the request arrived. Songs played offline all landed at the
  moment the phone reconnected, instead of when you heard them. Times now go out as epoch
  milliseconds, the format the Subsonic API expects. This also stops the "Ignoring invalid time
  param" warning Navidrome logged for every song.

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass, including a new test that sends a scrobble
  through the real Subsonic client to a local server and checks the `time` value it receives.
  `:androidApp:assembleRelease` builds, signed with the Ondo release certificate
  `fac61745…91033b9c`.
- APK SHA-256: `85e61187d57948e40ecc316a995c56a275add70ee30cb780463a004585999d66`.
- Not tested on a phone or against the live Navidrome yet. Once this is installed, the warning
  should stop appearing in the Navidrome log.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK, which still sends the old time format.
