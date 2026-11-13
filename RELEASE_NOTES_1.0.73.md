# Ondo 1.0.73 (973)

Covers stay put when you move playback between your phone and computer.

## Ondo Connect

- **Fixed: the computer lost the album cover after taking playback over from the phone.** Songs sent
  over Connect didn't say which album they belong to, and the computer needs that to find the
  cover. The phone now sends (and keeps) each song's album and artist, so covers show on the
  computer, and the album and artist links work for songs that came from the computer.
- Needs Ondo desktop 1.56.10 for the computer side (it also fills in missing albums by itself for
  songs from older phone builds).

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass, including a new one for album and artist ids
  travelling with a song. `:androidApp:assembleRelease` builds, signed with the Ondo release
  certificate `fac61745…91033b9c`.
- APK SHA-256: `8c71d0623baa8c011967babfdfd6087f37c32b0f3704d49b8a138307bc39f6a6`.
- Not tested on the phone itself with this build.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
