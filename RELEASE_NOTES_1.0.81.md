# Ondo 1.0.81 (981)

Publix Maps can now show Ondo as a mini player on the map.

## Publix Maps mini player

- Publix Maps can browse your playlists and mixes and start them, like Android Auto does. Before,
  Ondo let other apps only play, pause and skip.
- Only Publix Maps itself gets this: Ondo checks both the app's name and that it is signed with the
  Publix Maps release key, so another app can't pretend to be it.
- Music still plays in Ondo, and the Publix Maps voice lowers it for each direction, as any
  navigation app does.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `9c07668ed3366f44c08b330c8cdc7f85dd55d3d94e2e421986e393b764eb9070`.
- On the `ondo-qa-phone` emulator with Publix Maps 1.0.0 (signed with its release key) installed:
  Publix Maps connected, Ondo logged `onGetLibraryRoot from com.nicknas.publixmaps` succeeded and
  returned its six root sections (Playlists, Albums, Artists, Recently added and the rest), which
  only a trusted controller can do.
- Not tested: playing a real playlist from Publix Maps, since Ondo on the emulator was not signed
  in to the server; and an impostor app with Publix Maps' name but another key, which the
  certificate check refuses by construction (`PackageManager.hasSigningCertificate`).

## Wear OS

- Unchanged; the watch app from 1.0.80 is reused.
