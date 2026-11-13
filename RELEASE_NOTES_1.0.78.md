# Ondo 1.0.78 (978)

The line-by-line lyrics now show on wide screens like a Galaxy Z Fold's inner display.

## Lyrics on foldables and tablets

- **Fixed: no synced lyrics on the Fold's inner screen.** Ondo switches to its side-by-side layout
  whenever the screen is wider than it is tall, and that layout only had the cover and the controls.
  The synced line under the cover, which highlights each word as it's sung, was only in the
  portrait layout. Unfolded, the phone always used side-by-side, so it never showed lyrics.
- **The synced line now sits above the song title on the right.** It's the same component as in
  portrait, so it shows the current line and the next one, uses the lyrics timing offset, and appears
  or hides with a bounce when a song has lyrics or doesn't.
- **A phone turned sideways keeps controls only.** The lyric line is only shown when the screen is
  at least 480 dp tall, so ordinary phones in landscape don't push the controls off screen.
- The full lyrics screen (lyrics button at the bottom right) is unchanged.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `c5e2f380f3396ef0a46efe2fb1a0f512cf0e8fd8c35299ecd2e45e99f3a218a4`.
- Not tested: on the Fold or on an emulator. This is a layout change that reuses the existing
  portrait lyric component.

## Wear OS

Unchanged.
