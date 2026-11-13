# Ondo 1.0.64 (964)

Cover art for the weekly mixes, pinning to the top of Library, and your own playlists listed first.

## Pin to Library

- **Pin playlists, albums, artists and songs to the top of Library.** Long-press any of them (or open
  ⋮ on an album or playlist) and choose **Pin to Library**. Pins appear in a **Pinned** row above
  everything else, newest first. Tapping a pin opens the album, playlist or artist, and plays a pinned
  song right away.
- Long-press a pin, or choose **Unpin from Library** in the same menu, to remove it.
- Pins are saved on this device for your account. A pinned playlist that's deleted on the server
  disappears from the row.

## Mix cover art

- Each weekly mix now has its own cover, styled like Spotify's mixes: album art from a song in the
  mix, tinted in the mix's colour, with the mix title and "MOOD MIX" or "GENRE MIX". Each mix uses a
  different album. A mix with no usable album art gets a mood-styled gradient instead: hard diagonal
  slashes for hype, gym and party, soft blurred shapes for the rest.
- decknas draws the covers each Monday with the mixes. This makes no extra Claude calls. The app
  downloads each cover once per week and sets it as that playlist's cover, so it shows on the home
  cards, in Playlists and in the playlist's header.

## Playlists

- **Your own playlists come first** in Playlists, followed by the weekly mixes in home-screen order.
  Previously the mixes, being the newest playlists, came before yours.
- Weekly mixes no longer appear in **Add to playlist**, because songs added to a mix would be lost on
  the following Monday. Use **Save a copy** on a mix to keep it.

## Fixed (server)

- Cover art and streaming stopped working away from home on Oct 16. Adding the curator route to
  Tailscale switched port 10000 from Funnel to tailnet-only, so the phone could only play downloads.
  Funnel is back on and every route was checked from outside the network.

## Verification

- `:composeApp:testAndroidHostTest`: all 84 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c` (the same as 1.0.63).
- API 35 emulator, installed over 1.0.63, against the QA fixture server:
  - The app fetched the mix cover from `/ondo-curation/v1/covers/mood-1.jpg` and showed it on the home
    card. Mixes without a cover kept the coloured card.
  - Playlists listed "QA Family Favorites" before the three mixes.
  - Long-press on an album → **Pin to Library** added a **Pinned** row at the top of Library with the
    album.
  - Not run on the emulator: pinning artists or songs, unpinning, and playing a pinned song. These use
    the same pin store and menu item as the album pin.
- Server: the covers for week 2026-W42 were generated from the real library. `/v1/covers/<slot>.jpg`
  needs a Navidrome login (401 without one) and rejects path traversal (404).
- APK SHA-256: `4bd5e39c2c2f7dc76e097908d1a4d03de7fcf8860e11d3dafd69b43fe427d4b5`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
