# Ondo 1.0.79 (979)

Now Playing fits the Galaxy Z Fold8's cover screen: the controls no longer overlap the audio specs.

## Cover screen layout

- **Fixed: the play buttons covered the audio specs on the cover screen.** The cover screen is
  475 × 751 dp, much wider and shorter than a normal phone (about 411 × 915). Now Playing split the
  height between the cover art and the controls by a fixed ratio, which left the controls about 230 dp
  when they need about 260 dp. The buttons, which also scale with the screen's width, ended up drawn
  over the FLAC / kHz / kbps pill and the bottom bar.
- **Short, wide screens get a compact layout.** It kicks in when the screen is less than 1.8 times as
  tall as it is wide, which covers the Fold8 cover screen but not normal phones:
  - The controls keep their natural height and the cover art shrinks to fit what's left.
  - The audio specs sit between the two timestamps instead of on their own row.
  - The buttons are capped at 320 dp wide instead of 400, so the play button is a bit smaller.
  - The gap above the toolbar is 16 dp instead of 40.
- Normal phones and the unfolded inner screen are unchanged.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `f9b1890592af5506cdd8354c249c9a1104e5ac71d2f2f7d7da982a3914eed12b`.
- Installed on the Z Fold8 over adb (it reports 1.0.79). The cover screen's size, 1248 × 1972 px at
  420 dpi (475 × 751 dp), was read from the phone.
- Not tested: I haven't seen the new layout on the cover screen yet. The phone was unfolded and in use.

## Wear OS

Unchanged.
