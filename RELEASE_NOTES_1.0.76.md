# Ondo 1.0.76 (976)

Songs added from YouTube search now get their real title, artist and album, and can be upgraded to FLAC later.

## YouTube search

- **Fixed: added songs had the wrong title and artist.** Ondo saved the video's title and channel name
  as the song's details, so songs showed up like "sosocamo - gravity [Official Video]" or
  "angelato - Topic", with no album. Search now lists official audio from YouTube Music first, with its
  real title, artist and album.
- **Plain YouTube videos are still there, below the official songs.** Each result now says "Official
  audio" or "YouTube video", and official songs show their album. If you add a video, decknas looks for
  the official audio of the same song and uses that when the artist, title, version and length all
  match. If there isn't one, it keeps the video and cleans up its name.
- **FLAC upgrades.** Official audio is what the daily FLAC check can match against a lossless copy. It
  now looks at your newest added songs first.

Most of this runs on decknas and is already live, so Ondo desktop gets the same search results. This
update adds the labels, shows the album, and sends it when you add a song.

## Verification

- `:composeApp:testAndroidHostTest` passes. `:androidApp:assembleRelease` builds, signed with the Ondo
  release certificate `fac61745…91033b9c`.
- APK SHA-256: `bc5632eab577eba527db771c97a704e8ab88879bd95e1920756c903647f3900f`.
- On decknas: the live search endpoint returned official results first. A raw music video add, run
  against a scratch folder, came out as "Never Gonna Give You Up" by Rick Astley on "Whenever You Need
  Somebody", using the official audio.
- Not tested on a phone. No FLAC upgrade of a song added this way has happened yet.
- Songs added before this keep their old names.

## Wear OS

Unchanged.
