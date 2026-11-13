# Ondo 1.0.60 (960)

This release completes the 90 required fixes in Phases A, B and C of the
Android inspection work order. Optional Phase D changes are outside this release.

## Changes users will notice

- Playback reconnects after task removal. Swiping away an actively playing app
  keeps music playing; a paused or empty player stops. Queue edits preserve
  duplicate entries, and Play next inserts a whole collection in order, including
  when shuffle is enabled. Sleep timers follow local playback boundaries.
- Playlist refreshes and cover changes preserve membership. Large-library sync
  uses bounded queries, preserves data on partial failures, and no longer runs
  while logged out. Failed pending actions have bounded retries.
- Library lists load collection summaries; full song details load when needed.
  Offline search matches accents and Unicode case. Multi-disc albums retain
  disc and track ordering.
- Connect is opt-in. Idle devices send fewer updates, identical device names
  remain distinct, and capable peers use incremental queue edits. Authentication
  uses a bearer header rather than a WebSocket query token.
- Downloads throttle database progress writes, recover local playback after
  restart, and handle Android foreground-service timeouts. Failed acquisition
  imports appear under Needs attention. Choosing a Lidarr release does not
  enable monitoring before the user confirms.
- Collection ratings refresh immediately. Playlist additions use bounded
  batches, search keeps previous results while loading, and destructive storage
  actions ask for confirmation. Custom cover decoding runs away from the UI
  thread and changed covers refresh immediately.
- Login uses the bundled server, with a Use a different server option. Logout
  cancels account work, clears session state and asks whether to keep completed
  offline files. Integration keys remain encrypted per device; no API keys are
  bundled. Optional Navidrome gateway authentication remains available.
- Updates require an explicit download and verify the expected version, package
  and signing certificate before installation. Logs redact credentials, backups
  exclude private data, and external media controllers receive limited commands.
- Android Auto uses bounded, paged browse queries. Widgets refresh with track
  transitions. The Android build excludes iOS targets while retaining iOS sources.

## Fixed work-order IDs

| Area | IDs |
| --- | --- |
| Security and logout | SEC-1, SEC-2 |
| Playback | PLY-1, PLY-2, PLY-3, PLY-4, PLY-5, PLY-6, PLY-7, PLY-8, PLY-9, PLY-10, PLY-11, PLY-12, PLY-13, PLY-14, PLY-15, PLY-16 |
| Sync and scrobbling | SYNC-1, SYNC-2, SYNC-3, SYNC-4, SYNC-5 |
| Android Auto | AUTO-1, AUTO-2, AUTO-3 |
| Widgets | WID-1, WID-2, WID-3 |
| Acquisition | ACQ-1, ACQ-2, ACQ-3, ACQ-4, ACQ-5, ACQ-6 |
| Connect | CON-1, CON-2, CON-3, CON-4, CON-5, CON-6, CON-7 |
| Downloads | DL-1, DL-2, DL-3, DL-4, DL-5 |
| Database and search | DB-1, DB-2, DB-3, DB-4, DB-5, DB-6, DB-7, DB-8, DB-9, DB-10, DB-11, DB-12 |
| UI | UI-1, UI-2, UI-3, UI-4, UI-5, UI-6, UI-7, UI-8, UI-9, UI-10, UI-11, UI-12, UI-13, UI-14, UI-15, UI-16, UI-17, UI-18 |
| Updater | UPD-1 |
| Platform | PLAT-1, PLAT-2, PLAT-3, PLAT-4, PLAT-5, PLAT-6, PLAT-7, PLAT-8 |
| Lyrics and cancellation | LYR-1, CODE-1 |
| Build and signing | BLD-1, BLD-2 |

## Relay rollout

The compatible relay patch was deployed from `fix/ondo-connect-auth`, through
commit `621ac2dd`, before publishing the APK. Existing accounts and sessions
were preserved, and the connected clients reconnected successfully. The app
uses header-only authentication. The relay retains query-token authentication
for older clients, preserves distinct device IDs, and advertises protocol
capabilities. An invalid supplied Authorization header never falls back to a
query token. Live probes passed for local bearer authentication, legacy query
authentication, invalid/missing credential rejection, public proxy health and
public proxy bearer authentication.

## Verification

- Android host tests: 28 suites, 73 tests passed in the final host-test run.
  Coverage includes SQLite migrations 20 → 21 → 22, playlist membership,
  large-ID chunking, sync merge/retry rules, scrobble thresholds, converters,
  Unicode search, queue ordering, Connect edits, update versions and log redaction.
  The generated playlist DAO runs against native SQLite on the host; a host-only
  Android exception shim retains error messages stripped by AGP's mockable jar.
- Relay tests: four tests passed, covering bearer and legacy authentication,
  rejected credentials, same-name devices, protocol forwarding and reconnects.
- Release lint: zero errors, two warnings (`ExportedService`, `UseKtx`).
- Signed release assembly passed. Package remains `com.nickshortnct.ondo`, version
  `1.0.60` / `960`, with ARM64 and ARMv7 native libraries. The signing certificate
  matches the installed 1.0.59 APK.
- APK inspection found zero plaintext or base64 matches for the three configured
  integration API keys. The generation task bundles only service URLs.
- API 35 emulator upgrade: schema 20 → 22 preserved all library counts, both
  playlist memberships and both completed downloads in downloads schema 4.
  All normalized search fields were populated, and the queue restored on restart.
- Forced-offline playback passed for both downloaded album tracks, including
  automatic advance and portrait/landscape rotation.
- Playing task removal kept the session playing; reopening retained the queue.
  Paused task removal stopped the session; reopening created a new session and
  resumed playback. These checks used Android's task-removal command, which
  invoked `onTaskRemoved`; the emulator launcher did not respond to swipe gestures.
- Search duplicate confirmation preserved Play next: the three-entry queue was
  current track, duplicate current track, original following track. Clear downloads
  showed its confirmation and cancellation retained both files. Logout offered an
  unchecked Delete offline files choice, defaulting to keeping them.
- No fatal app crash was logged during the emulator checks, and shared app logs
  contained no unredacted sensitive query fields.

Remaining manual QA: fresh login/full sync (the emulator's local fixture server
is offline), real-device Android Auto, two-device Connect, a 1,000-song queue,
widget interaction, permission requests after rotation, live acquisition and
update installation, actual logout cleanup for both offline-file choices, and
long-running foreground-download timeouts. Automated tests do not substitute
for these checks.

## Prepared artifact

- APK: `androidApp/build/outputs/apk/release/Ondo.apk` (28,120,211 bytes).
- SHA-256: `18c9bec1b7a64a1e56214c6088335a8a6b2c3e308848a89191ecd6b566d2d50d`.
- Signing certificate SHA-256:
  `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
- Stable local copy: `/home/nicknas/tmp/ondo-1.0.60-qa/Ondo-1.0.60+960.apk`.
- Source branches: `fix/inspection-1.0.60` and `fix/ondo-connect-auth`.
- Published release: [Ondo 1.0.60 (960)](https://github.com/nickshortnct-create/ondo-apk/releases/tag/v1.0.60%2B960),
  with the asset named `Ondo.apk`. The unauthenticated public updater feed lists
  it, and the 1.0.59 client's version comparison recognizes it as newer.
- A fresh public APK download matched the checksum and signing certificate above.
- The dashboard download link `/home/nicknas/tools/wavio-apk/ondo.apk` now serves
  `ondo-1.0.60+960.apk` with the same verified checksum.
- Server API keys were not rotated during this rollout.
