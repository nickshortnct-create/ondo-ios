# Ondo 1.0.70 (970)

The audio quality line works again while music plays on another Ondo Connect device.

## Ondo Connect

- **Quality while another device plays.** The line under the progress bar (format • kHz • kbps)
  went blank, or only said "Playing on …", whenever Connect had music playing on another device,
  because devices didn't share what they were streaming. Each device now sends its format,
  sample rate and bitrate with its playback state, and the line shows them in green with the
  device's name, for example "FLAC • 44.1 kHz • 892 kbps • Ondo Desktop".
- This phone sends its own quality too, so Ondo desktop shows it under the song while it
  controls the phone. Needs Ondo desktop 1.56.4; older desktop versions still show "Playing on …".

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass, including a new one checking that format,
  bitrate and sample rate survive the trip between devices. `:androidApp:assembleRelease` builds,
  signed with the Ondo release certificate `fac61745…91033b9c`.
- Not tested on a phone with the desktop app.
- APK SHA-256: `539c01f714def55c8b241a836a28e001d6b4de417ee1fa9b962d58ec95edda33`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
