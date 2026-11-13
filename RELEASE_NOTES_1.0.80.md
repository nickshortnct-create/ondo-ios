# Ondo 1.0.80 (980)

The cover art shows again when Ondo shares the Fold's screen with other apps, and the lyric preview can be hidden in the car.

## Split-screen layout

- **Fixed: the cover art almost disappeared with three apps open.** In a small pane (about 395 × 515 dp on
  the unfolded Fold8), the lyric preview and the Downloads / Upgrades / Integrations links took the space
  the cover art needed, which left it a few pixels tall.
- Short portrait spaces (the cover screen, or one pane of a split) now give the cover art at least 45%
  of the width (up to 200 dp). If the lyric preview would push it below that, the preview is left out.
  The Lyrics button still opens the full lyrics. If the art is still too small, the links are left out
  too.
- The cover screen keeps its lyrics and links. Normal phones and the full unfolded screen are unchanged.

## Hide lyrics on Bluetooth

- New in **Settings → Now Playing → Hide lyrics on Bluetooth**. When you're connected to a Bluetooth
  device (the van), it shows up here. Turn it on and the lyric preview under the cover art stays hidden
  whenever that device is connected. The device's audio delay can put synced lyrics out of time.
- Devices you've turned on stay listed, so you can turn them off when you're not connected.
- The full Lyrics screen still works as before.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `de94cd528b6bebc8fb0f8ec15da995297a6bb10f82ebe0ba8d15ff5068f91cd1`.
- On the `ondo-qa-phone` emulator with a fake server serving synced lyrics:
  - At normal phone size, the art, the lyric line and the controls look the same as 1.0.79.
  - At 1037 × 1352 px (395 × 515 dp, about one pane of a three-app split), the lyrics and links were
    left out and the playing cover art was about 125 dp, up from almost nothing.
  - At the cover screen's size (1247 × 1971 px), the links, the cover art and the lyrics all show.
  - The new settings group shows "No Bluetooth device connected" when nothing is connected.
- Not tested: the Bluetooth setting with a real car, which the emulator can't fake. It uses the
  device name Android reports for the connected Bluetooth audio output, which needs no extra permission.
- Not tested on the Fold itself.

## Wear OS

Unchanged.
