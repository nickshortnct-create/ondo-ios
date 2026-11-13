# Ondo 1.0.82 (982)

Sophie's music no longer shows up for you.

## Sophie's downloads

- Sophie's iPhone app (Arpeggi) adds songs through the Subsonic proxy, which downloads them from
  YouTube into her own library. Those downloads went through the same downloader as yours, so they
  filled Ondo's Downloads screen (196 of the last 200 entries were hers). Ondo now lists only
  downloads into the shared library.

## Weekly mixes

- The weekly mixes could pick songs from Sophie's library: the curator on the NAS read every song on
  the server. The curator now uses only the shared library, and this week's mixes were rebuilt
  without her 45 songs.
- Ondo used to rewrite a mix playlist only when its name or description changed. It now also
  rewrites one that has more songs than this week's mix, so the trimmed mixes replace the copies
  already on your account the next time Ondo syncs.

## Verification

- `:composeApp:testAndroidHostTest`: all 114 tests pass. `:androidApp:assembleRelease` builds, signed
  with the Ondo release certificate `fac61745…91033b9c`.
- APK SHA-256: `d699a0fcfe5cdabb59d6f3223974c3fdfda0f7bf552a90a3c8a917b10df0f94e`.
- Server: the downloader's task list tags each task with its library (`sophie` or `music`), which is
  what the new filter reads. The curator now reads 8,204 songs, the shared library's full count, and
  republishing removed all 45 of Sophie's songs from this week's 10 mixes.
- Not tested on the phone: the Downloads screen with the live task list, and the mix rewrite against
  the server.

## Wear OS

- Unchanged; the watch app from 1.0.80 is reused.
