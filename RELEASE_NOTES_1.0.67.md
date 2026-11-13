# Ondo 1.0.67 (967)

Less battery drain during long listens, no more duplicate weekly mixes, a playlist editor, and
"Add to playlist" puts the playlists you play most at the top.

## Battery

Android's battery screen showed Ondo using 16% of a charge over about 4 hours of background playback. These
changes cut down how often the app wakes the phone's radio and CPU while it plays in the background:

- **Audio loads in larger, less frequent chunks.** The player only buffered 32–64 seconds ahead, so
  it went back to the network about every 30 seconds. After each request the cellular radio stays
  powered for several seconds, so on 5G it never got to idle. The player now buffers up to 10 minutes
  ahead (capped at 40 MB, about 4½ minutes of FLAC) and only fetches again once less than 2 minutes
  are left.
- **Ondo Connect stays quiet when no other device is online.** It sent a "hello" every 15 seconds and
  your playback position every 10 seconds, even with no other device connected. Now:
  - it sends nothing about playback until another device appears, then sends the current state at once;
  - the keep-alive runs every 4 minutes instead of every 15 seconds (the relay keeps a device listed
    while its connection is open);
  - the connection closes after 5 minutes paused instead of 10.
- **The progress bar updates once every 5 seconds while the app is in the background**, down from
  5 times a second. It goes back to 5 times a second as soon as you open the app.
- **Library sync runs hourly in the background** instead of every 15 minutes, and opening the app
  after a longer gap syncs straight away. Changes you make, like favourites, are still sent as soon as
  you're back online. When mobile data drops and comes back, the library is only pulled again if the
  last pull was more than 15 minutes ago.

Not changed:

- **Audio offload** stays a setting (Settings → Audio effects). Offload hands decoding to the phone's
  low-power audio chip but switches off the equaliser, ReplayGain and the volume boost. Turn it on if
  you don't use them.
- **Streaming quality on mobile data is still Lossless by default.** About two-thirds of the library is
  FLAC at roughly 1.2 Mbps, so streaming over 5G for 4 hours uses about 2 GB. For less data and less
  battery, set Settings → Streaming quality → Mobile data to a lower quality, or download playlists
  before you go out.

## Weekly mixes no longer duplicate

- Every weekly mix was being created again each time the app refreshed the mixes (on launch, at most
  every 3 hours). Navidrome had 9 copies of each of this week's 10 mixes and 3 copies of last week's.
- Cause: Navidrome leaves the owner and comment off the playlist list it sends to "minimal" clients
  (decknas lists Ondo as one), and the mix marker is stored in the comment. Ondo therefore never found
  its existing mixes. The playlist list is now requested as the full client, since it contains no songs
  and doesn't need the minimal format. If a list ever comes back without owners, the mixes are left
  alone instead of created again.
- **The duplicates are deleted automatically** the first time 1.0.67 refreshes the mixes. One copy of
  each mix is kept: this week's, most recently changed. Mixes from previous weeks' slots go too.
- The same missing comment broke a few other things, which now work again:
  - the "Your mood mixes" and "Your genre mixes" sections in Library;
  - your own playlists listed before the mixes;
  - mixes kept out of "Add to playlist";
  - the mix banner on a mix's page.
- Server-side playlist covers now show in the list (the minimal list had no `coverArt` either).

## Edit playlists

- **Edit playlist** is in a playlist's ⋮ menu and in its long-press menu. You can change:
  - the name;
  - the description;
  - **Public** (whether other people on your server can see and play it);
  - the cover (choose and crop an image, or remove it).
- The cover is uploaded to Navidrome, so it shows on your other devices and in other apps too. If
  the server won't take it, the cover is kept on this device only and the app says why.
- Only your own playlists can be edited. Weekly mixes are rewritten every Monday, so they can't be
  edited; use **Save a copy** to keep one. Mixes and other people's playlists still have the
  device-only Change/Remove playlist cover options.

## Add to playlist

- **The playlists you play most come first**, then the ones you played most recently, then the rest
  alphabetically. Plays are counted on this device whenever you play or shuffle a playlist, including
  from Android Auto. Navidrome doesn't count playlist plays, so the order starts alphabetical and
  adjusts as you listen.
- Your own playlists are now matched case-insensitively. Before, logging in as "Nick" when the server
  stores "nick" left "Add to playlist" with an empty list.

## Verification

- `:composeApp:testAndroidHostTest`: all 92 tests pass, including 2 new ones for the play-count
  order and for which playlists are editable. `:androidApp:assembleRelease` builds, signed with the
  Ondo release certificate `fac61745…91033b9c` (the same as 1.0.66).
- Navidrome 0.63.2 (throwaway container with decknas' minimal-client setting):
  - `getPlaylists` as `Ondo` returns only names; as `OndoMeta` it includes owner, comment and coverArt.
  - Renaming, `public=true` and clearing the description with a single space all work.
  - `/auth/login` plus `POST`/`DELETE /api/playlist/{id}/image` set and remove the cover. The playlist's
    coverArt id changes after an upload, so other devices fetch the new cover.
- API 35 emulator, release APK, against the QA fixture server. The fixture now strips playlist
  metadata for minimal clients like Navidrome does, and takes Navidrome logins and cover uploads:
  - Three launches made 3 mix playlists in total. Before this fix, every launch would have added
    another 3.
  - Library showed the mix sections again; Playlists listed own playlists before the mixes.
  - Edit playlist renamed a playlist, set its description, made it public and uploaded a cropped cover
    (shown in the list straight away).
  - "Add to another playlist" was alphabetical with no plays. After playing "QA Road Trip" once, it
    moved above "QA Family Favorites".
- Not measured: battery use on a real phone. Watch Settings → Battery usage after a long listen
  on 1.0.67.
- APK SHA-256: `ab1a32964173e2f3cdddbd7b3c5bab1ab39cf72f003755c1c7d976b63b269589`.

## Wear OS

Unchanged. The release includes the 1.0.62 watch APK.
