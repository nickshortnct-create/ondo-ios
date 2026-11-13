# Ondo 1.0.69 (969)

Ondo Connect signs in by itself and has a cleaner screen, a device button sits next to Lyrics and
Queue, and the album-colour glow is now behind the whole app.

## Ondo Connect

- **Signs in by itself.** Connect uses the music server account you're signed in to Ondo with, so
  there's no separate username and password. If you turn Connect on and it isn't signed in yet, it
  signs in (and creates the Connect account the first time) on its own.
- **Tries both routes to decknas.** If the relay address in settings can't be reached, Ondo also
  tries the DuckDNS and Tailscale Funnel addresses, for signing in and for staying connected.
- **Devices button in Now Playing.** A small device icon sits next to Lyrics and Queue. It turns
  green while music is playing on another device. Tap it to see your devices and what each one is
  playing, and to move the music to one of them or back to this phone.
- **New Connect screen.** One status card shows whether Connect is on, signed in and connected,
  with a single switch. Below it are your devices as cards (the one playing is green), then this
  phone's name. The relay address and signing in with a different account are under Advanced.
- The "Devices" link above the cover in Now Playing is gone; Downloads, Upgrades and Integrations
  stay there.
- **Fixed: while another device was playing, the artist name disappeared** under the song title,
  and the audio quality line was blank. The artist now shows, and the quality line says which
  device is playing instead.

## Glow behind the whole app

- The Now Playing background (the album cover blurred into glowing colour orbs) now shows behind
  every screen, in the colours of the song that's playing. Outside Now Playing it stays still
  instead of slowly turning, so it doesn't cost battery or slow down scrolling.

## Verification

- `:composeApp:testAndroidHostTest`: all tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- The Connect sign-in failure in 1.0.68 ("connection closed") came from decknas: the Tailscale
  Funnel route on port 10000 had been switched to tailnet-only. That was fixed on the server.
- Not tested on a phone: the new screens, the device sheet and the app-wide background.
- APK SHA-256: `23f3c1f48460d0ee5f6eac3bc0aa556dd0324b7260092633d64dc467a1b1c249`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
