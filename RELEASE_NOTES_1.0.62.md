# Ondo 1.0.62 (962)

Android Auto reliability, more player buttons in quick settings and Android
Auto, and a new Wear OS app.

## Android Auto

- **Ondo no longer disappears from Android Auto when the phone app is swiped
  away.** Removing Ondo from Recents while playback was paused stopped the
  playback service even with Android Auto connected. Android then killed the
  process, Auto lost its connection to Ondo, and Ondo either vanished from the
  launcher or showed a blank Now Playing screen until the phone app was opened
  again. The service now stays running while Android Auto, a watch or
  Assistant is connected.
- **Now Playing is filled in when Auto connects to a closed app.** When Auto
  starts Ondo without the phone app open, the last queue is loaded paused at
  the saved position. The car shows the current song immediately and play
  resumes it. This is skipped when the phone app is open, because the app
  restores its own queue.
- **Starred and Shuffle all are visible in Auto.** Auto shows at most four
  root tabs, so the fifth and sixth categories were hidden. Categories past
  the head unit's limit now appear under a **More** tab.
- Category tabs resolve through `getItem`, so Auto no longer receives
  "not supported" for Playlists, Albums or Artists.

## Player buttons (quick settings, notification, Android Auto, watch)

- Shuffle and Repeat (off / all / one) now have labels that describe their
  state, such as "Shuffle on" or "Repeat one".
- New **Favourite** (heart) button that stars or unstars the current song and
  syncs it to the server. Starring from the app, the notification, Auto or the
  watch updates the other places too.
- Android allows two extra buttons in the quick-settings player next to
  previous and next, so shuffle and repeat appear there. Android Auto and the
  expanded notification also show Favourite.

## Wear OS app (new)

A separate watch APK (`Ondo-Wear.apk`) with the same package name and signing
key as the phone app. The Wear Data Layer only connects apps that share both.

- **Phone remote:** shows the phone's Now Playing with artwork and progress.
  Supports play/pause, previous/next, shuffle, repeat, favourite and phone
  volume (buttons or the crown/bezel). It also works while the phone app is
  closed.
- **Login from the phone:** the phone sends the server URL and a Subsonic
  token with its salt, never the password. Logging out on the phone signs the
  watch out.
- **Library on the watch:** Playlists, Albums, Artists, Recently added,
  Recently played, Favourite songs, Shuffle all and voice/keyboard Search.
- **Play on phone or watch:** a switch on the home screen chooses the device.
  On the phone, albums and playlists start at the tapped track and use the
  phone's library and downloads. On the watch, music streams through Bluetooth
  headphones or the speaker. Wear OS shows its output picker when no suitable
  output is connected.
- **Offline downloads:** download any album or playlist to the watch, play it
  without a phone or network, shuffle all downloads and delete downloads.
  Watch streaming and download quality can be 128, 192 or 320 kbps MP3, or
  the original file.
- Plays scrobble to the server, and watch favourites sync back.

### Installing on the watch

Wear apps outside the Play Store are installed over ADB:

1. On the watch: Settings → System → About → tap *Build number* 7 times, then
   Settings → Developer options → enable **ADB debugging** and **Wireless
   debugging** → *Pair new device*.
2. On a computer on the same Wi-Fi:
   `adb pair <ip>:<pair-port>` (enter the code), then
   `adb connect <ip>:<port>` and `adb install Ondo-Wear.apk`.
3. Update the phone app to 1.0.62 and open it once while logged in.
   The watch receives the login automatically.

## Known limits

- Ondo still doesn't appear in Android Auto unless Auto's *Unknown sources*
  developer setting is on, because the APK is installed outside the Play
  Store. That setting can reset after an Android Auto update. If Ondo is
  missing from Auto's launcher, enable it again: in Android Auto settings,
  tap *Version* 10 times, open ⋮ → *Developer settings* → **Unknown sources**.
- Watch → phone control, Data Layer login sync and Android Auto were not tested on
  real hardware for this build. See Verification.

## Verification

- `:androidApp:assembleRelease`, `:wearApp:assembleRelease` and
  `:wearApp:assembleDebug` build. Both release APKs are signed with the Ondo
  release key.
- API 35 phone emulator, upgraded from 1.0.61 with the QA fixture library:
  - The platform session publishes *Shuffle on / Repeat one / Add to
    favourites* custom actions.
  - The quick-settings player shows previous, next, shuffle and repeat.
  - Starring in the app changed the session button to *Remove from
    favourites*.
- Wear OS 5.1 emulator with the QA fixture server:
  - Home, Albums, the album screen and the player screen render.
  - "Play on watch" queued the album. Media3 held it with
    `PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT` because the emulator
    has no headphones. That is the expected Wear behaviour.
  - Downloading the album saved both tracks and the index, and the button
    changed to delete.
  - Tapping the watch favourite button called `/rest/star`. The R8 release
    watch APK launches without errors.
- APK SHA-256: phone `527a523531a722ed7bb4207f99aded2f2b588c9199ddcfd5324159b891ae14f5`,
  watch `5dd321b8174f04ba64848629db2f1658fd9d7de1461c447d69f1c7ded27b78bb`.
  Both are signed with certificate `fac61745…91033b9c`.

## Publication

Published as [v1.0.62+962](https://github.com/nickshortnct-create/ondo-apk/releases/tag/v1.0.62%2B962)
with assets `Ondo.apk` (phone, read by the in-app updater) and `Ondo-Wear.apk`.
The public updater feed lists it as the newest release, and a fresh download of
`Ondo.apk` matches the SHA-256 above. The DeckNAS dashboard link
`/home/nicknas/tools/wavio-apk/ondo.apk` now points to `ondo-1.0.62+962.apk`.
