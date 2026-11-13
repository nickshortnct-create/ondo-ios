# Ondo 1.0.68 (968)

Ondo Connect now works like Spotify Connect: music plays on one device at a time, and your other
devices follow it and control it. The download services no longer need API keys.

## Ondo Connect: one player, every other device is a remote

- **Your phone follows whichever device starts playing.** When Ondo on your computer (or another
  phone) starts playing, this phone switches to it on its own: the player shows that device's song,
  progress and queue, and play, pause, skip, seek, shuffle, repeat and the queue control it. You no
  longer have to open Devices and pick it.
- **Only one device ever plays.** If music is playing here and another device starts playing, this
  phone pauses and follows that device. When playback starts on this phone, the device that was
  playing stops. Devices compare when each started, so they always agree which one keeps playing.
- **"Playing on …" under the player controls.** When music is playing on another device, a chip
  under the controls says which one. Tap it to move the music to any device, or back to this phone,
  with one tap. It picks up at the same song and position and stops where it was. While this phone is
  playing, the chip shows "Devices available" when another device is online.
- Following another device keeps Ondo Connect's connection open only while Ondo is on screen. In
  the background it still closes after 5 minutes with nothing playing here, as in 1.0.67, and it
  reconnects and follows again when you open the app.
- Watching another device never starts audio on this phone. Only moving playback here (the chip,
  "Transfer playback here", or a transfer from another device) does.
- This needs Ondo desktop 1.56.2 or later on the computer. Older desktop builds still work with this
  phone, but don't follow or stop on their own.

## Download services without API keys

- Lidarr, SoulSync, YouTube and Squid connect by themselves with the music server account you're
  signed in with. They go through a new gateway on decknas (`/ondo-gateway`), which checks your
  Navidrome login and adds each service's key on the server, so the keys never reach the phone.
  Only Navidrome admin accounts can use it, since downloads go into the shared library.
- Keys you entered before still work and are used directly. Clear a key in Integrations to switch
  that service to your login.
- Lidarr's folder and profiles are filled in automatically when you haven't picked any: the first
  folder, "Lossless" and "Standard", the same as almost all your existing artists use.

## Verification

- `:composeApp:testAndroidHostTest`: all 100 tests pass, including new ones for the device handoff rules and for routing services through the gateway. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c` (the same as 1.0.67).
- The decknas gateway, checked against a stand-in Navidrome: an admin login reaches Lidarr, SoulSync
  and the YouTube fallback (HTTP 200); a non-admin or wrong login gets 401; a key sent by the client
  is ignored. Not yet checked with your real login.
- Not tested on the phone with the desktop app yet: following, stopping and moving playback between
  devices. The desktop side has the same rules and its own tests pass.
- APK SHA-256: `940befc81dcf9bd563e0df1b0956c7340b0dc1cbbc00ab8fe30c6ba2ef571c0a`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
