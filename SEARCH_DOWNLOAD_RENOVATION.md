# Search and downloads renovation

This change targets the canonical Kotlin Ondo Android app, version 1.0.59 (959). The Wavio/React Native app is a feature reference, not the release target.

## User flow

- Search while typing. Cached library matches appear first; server and discovery results arrive as each source responds. Editing or clearing the field cancels the previous work.
- All previews relevant songs and artist/album shelves, with a matching artist or album placed first. See all keeps the query and opens a full category. Songs, Albums, Artists, and Playlists show the available results; YouTube provides direct Add actions and optional video previews.
- Library songs play on tap. Their visible options include saving to this device, cancelling a transfer, removing a device copy, queue actions, and track details. Saved songs have an offline indicator.
- New music uses Add to library. Accepted requests show View progress across screens, instead of inviting another submission. Alternate matched sources remain available if the first source fails.
- Downloads separates shared Library requests from This device copies. It shows active work, failures with supported recovery actions, and recent server completions. Device copies offer Play and Remove from device. Recently finished server requests link to a search for the title.
- Search history survives restart and is scoped to the server/account. Category and navigation state are retained. Extra search modes respect the screen's content padding.

## Issues addressed

| Previous issue | Resulting behavior |
| --- | --- |
| Every search source had to finish before any matches appeared | Cached, remote library, and individual provider responses publish independently, with bounded source waits. |
| Debounce delayed cancellation; broad exception handlers swallowed cancellation | Cancellation starts when the query changes, and a generation/query check rejects stale responses. |
| Library failures silently appeared as empty searches | Existing matches survive source failures; the screen offers Retry and Refresh. |
| Songs silently stopped at ten; artist/album chips still left users in horizontal shelves | Full category results are available in a list/grid, with query-preserving See all previews. |
| Playlist results and single-track albums were discarded | Both appear in unified results and open their existing collection screens. |
| Punctuation in genuine artist names triggered a collaboration filter | Artist names are preserved; known MusicBrainz identities disambiguate same-name artists. |
| Duplicates from different providers filled the list; title-only matching confused versions | Matching uses title, credits, recording/version labels, and known metadata IDs; alternate providers are retained. |
| SQLite interpreted percent/underscore in titles as wildcards | Queries escape literal wildcard characters and search credited artist/album fields. |
| Offline searches combining artist and song names missed songs unless the whole phrase was in one field | Bound SQL matches all query words across title, artist, and album fields. Current-query cached matches survive server search with different matching rules. |
| Discovery ranking/normalization ran repeatedly during sort on the UI dispatcher | Ranking is computed once per row and merged away from the main dispatcher. |
| Recent queries disappeared after restarting the app | Intentional submitted/used queries persist, with normalized duplicate removal and clear controls. |
| Same-titled albums could request another artist's music | Acquisition requires matching album title and artist, and rejects missing credits. |
| Repeated taps or reopening a row/sheet could submit duplicate requests | Shared accepted-request state and in-flight coalescing cover providers and screens. |
| Scrolling away or closing a sheet could cancel an already-submitted acquisition | Shared work survives UI disposal, with pending state retained when a result reopens. |
| Album acquisition could report success when no source accepted any music | Empty/no-success acquisition fails visibly; success means an actual accepted request. |
| Artist pages omitted releases beyond the first 100 | Bounded, deduplicated MusicBrainz pagination respects request spacing. |
| Supplemental artist-track lookup stalled usable discographies | Discography and supplemental tracks load separately. Library badges update from database flows. |
| The downloads screen hid failures and completions | Active, attention, and recently finished requests have distinct visible groups. |
| Serial queue polling stalled the screen and discarded old rows on failure | Concurrent bounded polling preserves last-known rows and pauses while the screen/app is inactive. |
| “Enable service” only affected polling, contradicting its label | Settings describe showing the service in Downloads. |
| YouTube required preview before acquisition and shared one busy state for every result | Each result has a direct Add action, independent pending state, retryable search, and optional preview. |
| Phone download registration/service-start ordering raced | Registration and duplicate detection are atomic; background service observes registered work. |
| Cancel/delete could race the writer, or cleanup ran in a cancelled coroutine | Cancellation waits for the writer and cleanup runs in a non-cancellable context. |
| Detached progress updates could overwrite a completed download | Transfer progress writes finish before completion is recorded. |
| HTTP errors, empty responses, or error-page bodies could be saved as audio | HTTP failure, known error content types, and empty files are rejected. |
| Metadata downloads could indefinitely hold up audio | Cover/lyric work is bounded independently of the audio transfer. |
| Download actions in search were displayed with no callbacks | Search wires actual device status and download/cancel/remove actions. |
| Song options could queue unavailable offline or restricted songs despite the row being blocked | Search uses the same availability rules in tap, swipe, and More options; saved-song playback also respects explicit-content settings. |
| Missing artwork filled result lists with repeated error captions | Artwork uses a quiet placeholder while song and album actions remain usable. |
| Forced offline could still start catalog sync, and changing offline settings needed a network event | Connectivity observes the setting immediately and starts with truthful availability. Catalog sync waits for connectivity and resumes on reconnect. |
| Transfers restored after a process restart could ignore Forced offline / Wi-Fi settings | Restored transfers and retries wait for allowed connectivity before network work. Their queued rows remain cancellable. |
| Download quality could read stale cellular state, and Android monitored non-default networks | Cellular state stays current and Android follows the active connection for search, connectivity policy, and download quality. |
| Voice search was advertised without a matching Android intent or complete playback handling | Android Assistant/Auto search resolves songs, albums, artists, and playlists; browse search reports and pages actual results. |

## Service comparison

The design follows documented user-facing behavior, not assumptions about proprietary ranking algorithms:

- [Spotify autocomplete](https://support.spotify.com/us/article/search-autocomplete/) supports partial typing and previous queries.
- [Spotify search](https://support.spotify.com/ca-en/article/search/) includes title/artist/album and supported lyric searches.
- [YouTube Music discovery](https://support.google.com/youtubemusic/answer/13401025?hl=en) uses search categories and artist drill-down.
- [YouTube Music library](https://support.google.com/youtubemusic/answer/6313542?hl=en) integrates downloaded music with Library.
- [Spotify offline listening](https://support.spotify.com/us/article/listen-offline/) exposes download state and offline availability.
- [YouTube Music downloads](https://support.google.com/youtubemusic/answer/6313535?co=GENIE.Platform%3DAndroid&hl=en) supports direct song/collection actions.
- [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API) and [rate limits](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting) guide artist-discography pagination.

## Verification

The complete integrated source passed on October 1, 2026:

- `:composeApp:testAndroidHostTest`: 33 tests passed, zero failures, errors, or skips. These cover result identities/ranking, provider isolation and cancellation, literal and compound SQL query binding, accepted-request identities, and acquisition lifetime/retry behavior.
- `:androidApp:assembleRelease`: signed Ondo 1.0.59 (959), minimum Android API 24. Its signing certificate matches the previous Ondo APK.
- `:androidApp:lintRelease`: zero errors. The one existing exported playback-service warning remains; the service supports media browsers and Android Auto.
- SQLite fixtures: 11 assertions passed for mixed/reordered song+artist+album queries, required tokens, literal wildcard characters, null fields, query injection, empty searches, and result limits.
- The final build was checked against a hash snapshot of 453 Kotlin/build/manifest/resource inputs; none changed after the source freeze.
- Repeatable fake-library and generated-audio fixtures are in `scripts/qa`. Emulator results below use localhost test data; no live music acquisition was submitted.

Emulator checks passed for progressive library/discovery results with an unavailable source, category and playlist navigation, one-tap acquisition, reopening a pending request without a duplicate POST, visible completed requests, and Find in library with a prefilled query and playable local result. Device download, status filtering, direct playback from the saved WAV, and playback after an offline process restart passed without another stream request. Forced-offline startup/search/download screens made zero fixture HTTP requests; unavailable songs offered no playback, queue, or save actions. Compound artist+song queries found the expected saved song. A restored unfinished device transfer waited offline with a Cancel action, then resumed automatically when Auto was selected. Expired catalog sync also resumed without restart.

APK SHA-256: `a4b64bb3a63d7b2531131a62f474012ca6b9d424faec4a4f1f79d678a95cc412`.

Android was built and exercised. iOS shares the query improvements and offline-setting observation change, but an iOS build cannot be verified in this Linux environment.

Read-only checks against configured live services returned valid results: Lidarr 20 results in 0.52 seconds; SoulSync 10 in 0.74 seconds; YouTube 5 in 1.83 seconds. These are individual smoke measurements, not latency guarantees. No live music download was submitted by those checks.

## Backend boundaries

- Server acquisition finishes separately from the library server's scan/import. “Added to server” does not claim the recording is already playable in Navidrome. Refresh and Find in library check its availability.
- Squid retains failed task identities for one hour. Its API cannot immediately retry those tasks; the app reports the actual failure rather than displaying a nonfunctional Retry button.
- Lidarr removes imported jobs from its active queue. Disappearance alone cannot distinguish successful import from cancellation, so the app does not invent completion events.
- Accepted-request state is held by the running app session. Backend duplicate handling still applies after process restart; provider-wide durable request history is outside this app change.
- This is a search/download renovation, not a Spotify/YouTube account-transfer implementation. Existing synced playlists are now searchable.
- Android 1.0.59 (959) is distributed through the [family updater release](https://github.com/nickshortnct-create/ondo-apk/releases/tag/v1.0.59%2B959), with the asset named `Ondo.apk`. Source is maintained on the private Ondo repository's `android` branch. The published APK must retain the verified SHA-256 above.
