# Ondo 1.0.71 (971)

Lyrics and the progress bar follow along smoothly while music plays on another device, and the
notification controls now control that device.

## Ondo Connect

- **Fixed: synced lyrics didn't work while your computer was playing.** Ondo desktop only sends
  its position every 10 seconds, and the phone used to wait for the next update, so the lyrics
  sat on one line and then jumped ahead. The phone now counts the position forward on its own
  between updates (and corrects itself with each one), so lyrics highlight in time, word by word
  where the source has word timing.
- The progress bar and time in Now Playing move smoothly for the same reason instead of jumping
  every 10 seconds.
- This only runs while Ondo is on screen and another device is playing.
- **The notification, quick settings player, lock screen, headphone and Bluetooth buttons control
  the playing device.** While your computer is playing, play/pause, next, previous and seeking
  there now act on the computer instead of starting this phone's own player and pulling playback
  back to the phone. Play and pause both toggle the computer, since the notification still shows
  the phone's own (paused) player. To move playback to the phone, use the device button in Now
  Playing.

## Lyrics

- **Word-by-word lyrics come first.** If any lyrics source has word-by-word timing for a song,
  Ondo uses it, even when a source earlier in your list has only line-by-line lyrics. Songs whose
  lyrics were already saved keep them; use refresh on the lyrics screen to look again.

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass, including a new one for counting the remote
  position forward. `:androidApp:assembleRelease` builds, signed with the Ondo release
  certificate `fac61745…91033b9c`.
- APK SHA-256: `090fa93f379c018bd992550272e2978edf621b3811613e6a5c2d75939c67b8d0`.
- Not tested on a phone with the desktop playing yet: lyrics timing, and the notification and
  Bluetooth controls.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
