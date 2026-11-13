# Ondo 1.0.72 (972)

Switching playback between your phone and computer picks up at the right spot every time.

## Ondo Connect

- **Fixed: after switching devices more than once, the time and lyrics were wrong.** When a device
  stopped because playback moved away, the others weren't told (decknas's relay dropped that
  update), so they kept its old "playing" state and position. That's fixed on decknas.
- **Taking playback over starts where the music actually is.** Moving playback to this phone used
  the computer's last position report, which could be 10 seconds old; the phone now works out the
  current position from it.
- **Seeking from another device shows up right away.** After the computer seeks this phone's
  playback, the phone reports the new position immediately instead of up to 10 seconds later.
- Needs Ondo desktop 1.56.9 for the same fixes on the computer.

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass, including a new one for the takeover position.
  `:androidApp:assembleRelease` builds, signed with the Ondo release certificate
  `fac61745…91033b9c`.
- Switching desktop → phone → desktop → phone → desktop was tested from the desktop with this
  phone on 1.0.71 and the relay fix: each switch continued within a second or two of where it was,
  and only one device played at a time.
- APK SHA-256: `0e650b7679b77618fee252dea515e52856c4ed36707409ecbdbe874ec9eb6681`.
- Not tested on the phone itself with this build.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
