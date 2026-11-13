# Ondo 1.0.74 (974)

The quick settings player, notification and lock screen show what your computer is playing.

## Ondo Connect

- **Fixed: the quick settings media player showed an old song while your computer played.** While
  another device is the player, Ondo now shows that device as its own media player: its song,
  cover, progress and play/pause state, in quick settings, the notification and on the lock
  screen. Its buttons (play/pause, next, previous, seeking) control the computer. This phone's
  own player entry, which would show the last song played here, is hidden meanwhile and comes
  back when playback returns to the phone.

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `3b4d84621aea9d3149b4e4a698f9b85b69350293454278f7f34f749e299a0434`.
- Not tested on a phone yet: the quick settings player and lock screen while the computer plays.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
