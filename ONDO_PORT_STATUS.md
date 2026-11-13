# Ondo port status (Navic base)

**Base:** Navic `v1.0.0-alpha55` → rebranded Kotlin/Compose app  
**Branch:** `ondo-port`  
**Wavio reference (read-only):** `/home/nicknas/wavio` on `cursor/wavio-connect-7526`

Navic is now the canonical Ondo Android app. Wavio/RN is reference only for
features not yet ported.

---

## Build & install

```bash
/home/nicknas/navic/tools/ondo-build.sh :androidApp:assembleDebug
```

| Field | Value |
| --- | --- |
| `applicationId` | `com.nickshortnct.ondo` |
| Signing | `androidApp/keystore/ondo-release.keystore` (Wavio debug keystore copy) |
| `versionCode` | **903** |
| `versionName` | **1.0.3** |
| APK | `/home/nicknas/tools/wavio-apk/ondo.apk` → `ondo-1.0.3+903-navic-debug.apk` |
| Funnel | `https://decknas.duckdns.org/wavio-apk/ondo.apk` |

**Install over existing Ondo:** same package + same keystore → direct upgrade.

---

## Feature status

| Feature | State | Notes |
| --- | --- | --- |
| Rebrand (package, icons, signing) | **Done** | `f61f7ba1` |
| Navidrome playlist sync (minimal API) | **Done** | `PlaylistSyncFetcher` uses client `Ondo`; library uses `OndoMeta` — `903` |
| Android Auto browse + play | **Done** | `MediaLibraryService` + `MediaLibraryTree` — `901` |
| Ondo Connect | **Not started** | Port from `wavio/apps/mobile/services/wavioConnect/*` |
| Search → download tab | **Not started** | Lidarr / SoulSync / Squid / yt-dlp |
| Live Downloads hub | **Not started** | |
| Gapless (native concat) | **Partial** | Navic has ExoPlayer gapless pref; no RN patch |
| Stream recovery | **Not started** | |
| Downloaders settings | **Not started** | |

---

## Android Auto (901)

- `PlaybackService` now extends **`MediaLibraryService`**
- `MediaLibrarySessionCallback` wires `MediaLibraryTree` (Room cache)
- `OndoMediaItemFactory` shared between app + Auto
- `CarPlaybackBridge` keeps in-app queue in sync when Auto starts playback

Manifest includes `MediaLibraryService` + `MediaBrowserService` intents.

---

## Next port order

1. **Ondo Connect** — relay client + settings UI + output picker
2. **Downloaders suite** — Lidarr, SoulSync, Squid, yt-dlp fallback, Live Downloads
3. **Search download tab** — merge downloader search into `SearchScreen`
4. **Gapless / stream recovery** — evaluate ExoPlayer-only path (no expo patch)

---

## Playlist refresh note

If refresh still shows `MissingFieldException` for `Song`, the installed build
predates `PlaylistSyncFetcher`. Install **901+** from the funnel URL above.

Server `ND_SUBSONIC_MINIMALCLIENTS=Ondo,...` is intentional (large playlists).
