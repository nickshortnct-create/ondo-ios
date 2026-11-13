package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.supervisorScope
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.*

 data class MusicResult(val service: String, val kind: String, val id: String, val title: String, val artist: String, val raw: JsonElement)
data class DownloadRow(val service: String, val id: String, val title: String, val status: String, val detail: String, val progress: Float?, val raw: JsonElement)
data class TrackInfo(val number: String, val title: String, val lengthMs: Int?)
data class YouTubeVideo(val id: String, val title: String, val channel: String, val durationMs: Long?, val thumbnail: String, val album: String = "", val official: Boolean = false)

// Providers combined by searchAll(). YouTube results stay in their dedicated
// tab and only enter the configured fallback pipeline after an explicit tap.
val combinedSearchProviders = listOf("Lidarr", "SoulSync", "Squid", "KHInsider")

class OndoDownloads(val api: OndoApi) {
    // Soulseek: albums Lidarr wants that decknas' Soularr is downloading from Soulseek. They never
    // enter Lidarr's own queue, so without this they are invisible until imported.
    val providers = listOf("Lidarr", "Soulseek", "SoulSync", "Squid", "YouTube")
    private val acquisitionCoordinator = AcquisitionCoordinator(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    val pendingItems: StateFlow<Set<String>> = acquisitionCoordinator.pendingItems
    private val observedCompletions = mutableMapOf<String, Set<String>>()
    private val queuePollMutex = Mutex()
    private val observedQueueRows = mutableMapOf<String, List<DownloadRow>>()
    private val _libraryRevision = MutableStateFlow(0)
    val libraryRevision: StateFlow<Int> = _libraryRevision
    private val _requestedItems = MutableStateFlow<Set<String>>(emptySet())
    val requestedItems: StateFlow<Set<String>> = _requestedItems

    private suspend fun requestAcquisition(key: String, request: suspend () -> String): String = coalesceAcquisition("item:$key", key) {
        if (key in _requestedItems.value) "Already requested. Follow progress in Downloads."
        else request().also { _requestedItems.update { requested -> requested + key } }
    }

    private suspend fun requestAcquisitionData(key: String, request: suspend () -> JsonElement): JsonElement {
        val response = coalesceAcquisition("item:$key", key) {
            if (key in _requestedItems.value) jsonObject("alreadyRequested" to true.j()).toString()
            else request().also { _requestedItems.update { requested -> requested + key } }.toString()
        }
        // Another source may have accepted this same song and returned its
        // human-readable confirmation through the shared coordinator.
        return runCatching { ondoJson.parseToJsonElement(response) }.getOrElse { jsonObject("alreadyRequested" to true.j()) }
    }

    private fun requireAcceptedTask(result: JsonElement) {
        val task = result.obj()["task"] ?: error("The source did not confirm this request. Try again.")
        check(task.str("id").isNotBlank() && task.str("status").isNotBlank()) { "The source did not confirm this request. Try again." }
        check(downloadQueueState(task.str("status")) !in setOf(DownloadQueueState.FAILED, DownloadQueueState.CANCELLED)) {
            "This source could not add the music. Try another result or try again later."
        }
    }

    private suspend fun coalesceAcquisition(key: String, pendingKey: String, request: suspend () -> String): String =
        acquisitionCoordinator.submit(key, pendingKey, request)
    private fun result(service: String, kind: String, raw: JsonElement): MusicResult {
        val artist = raw.obj()["artist"]?.let { if(it is JsonPrimitive) it.content else it.str("artistName", it.str("name")) }
            ?: raw.obj()["artists"]?.arr()?.joinToString(", ") { if(it is JsonPrimitive) it.content else it.str("name") }
            ?: raw.str("subtitle").substringBefore(" - ")
        // Lidarr's artist search/lookup results omit "id" entirely for anything
        // not yet added to its library (it's not 0, the field is just absent) --
        // without a foreignArtistId fallback, every not-yet-added artist hit gets
        // the same blank id, which crashes any LazyColumn/Grid keyed on it (all
        // such hits collide on the same "<service>" key). foreignArtistId is
        // MusicBrainz's own id and always present/unique per distinct artist.
        val id = raw.str("id", raw.str("foreignAlbumId", raw.str("foreignArtistId", raw.str("guid", raw.str("albumId")))))
        return MusicResult(service, kind, id, raw.str("title", raw.str("name", raw.str("artistName"))), artist, raw)
    }
    suspend fun search(service: String, query: String, page: Int = 0): List<MusicResult> {
        require(query.isNotBlank()) { "Enter an artist, album, or song" }
        return when(service) {
            "Lidarr" -> api.call(service, "/search", params = mapOf("term" to query)).arr().mapNotNull { entry ->
                when { entry.obj()["album"] != null -> result(service, "album", entry.obj().getValue("album"))
                    entry.obj()["artist"] != null -> result(service, "artist", entry.obj().getValue("artist"))
                    entry.str("foreignAlbumId").isNotBlank() -> result(service, "album", entry)
                    else -> null }
            }
            "SoulSync" -> {
                val payload = jsonObject("query" to query.j(), "limit" to 30.j())
                // Independent bounded lookups preserve songs if artist lookup fails.
                supervisorScope {
                    val endpoints = listOf("tracks" to "track", "artists" to "artist")
                    val outcomes = endpoints.map { (path, kind) -> async {
                        try {
                            val response = withTimeoutOrNull(4_000) {
                                api.call(service, "/search/$path", "POST", payload)
                            } ?: error("SoulSync $path lookup timed out")
                            Result.success(response.obj()[path].orEmptyArray().map { entry ->
                                result(service, kind, JsonObject(entry.obj() + ("source" to response.str("source").j())))
                            })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure<List<MusicResult>>(e)
                        }
                    } }.awaitAll()
                    if (outcomes.all { it.isFailure }) throw outcomes.first().exceptionOrNull()!!
                    outcomes.flatMap { it.getOrDefault(emptyList()) }
                }
            }
            "Squid" -> searchSquid(query)
            "KHInsider" -> api.raw("https://khinsider.squid.wtf/api/search", params = mapOf("q" to query)).obj()["items"].orEmptyArray().map { result(service,"album",it) }
            "AudioMuse", "AudioMuse lyrics" -> {
                val data = api.call("AudioMuse", if(service.endsWith("lyrics")) "/api/lyrics/search/text" else "/api/clap/search", "POST", jsonObject("query" to query.j(), "limit" to 50.j()))
                data.obj()["results"].orEmptyArray().map { result("AudioMuse", "library", JsonObject(it.obj() + ("id" to it.str("item_id").j()))) }
            }
            else -> emptyList()
        }
    }
    private suspend fun searchSquid(query: String): List<MusicResult> {
        val gatewayError = Regex("HTTP (502|503|504)")
        for (attempt in 0..2) {
            try {
                return api.raw(
                    "https://zeus.squid.wtf/api/songs",
                    headers = mapOf("Referer" to "https://saavn.squid.wtf/"),
                    params = mapOf("q" to query)
                ).obj()["results"].orEmptyArray().map { result("Squid", "track", it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 2 || !gatewayError.containsMatchIn(e.message.orEmpty())) throw e
                delay(500L * (attempt + 1))
            }
        }
        return emptyList()
    }
    // Public catalog lookup works without configuring a download destination.
    // ServiceConfig.enabled only controls background queue polling.
    fun searchAllFlow(query: String): Flow<DownloadSearchOutcome> {
        require(query.isNotBlank()) { "Enter an artist, album, or song" }
        val configured = combinedSearchProviders.filter {
            it in listOf("Squid", "KHInsider") || api.settings.get(it).url.isNotBlank()
        }
        return searchSources(configured) { service -> search(service, query) }
    }

    suspend fun searchAll(query: String): Pair<List<MusicResult>, List<String>> {
        val outcome = searchAllFlow(query).last()
        return outcome.results to outcome.failures
    }
    // Artist metadata (bio, genres, images, foreignArtistId) from Lidarr's
    // metadata backend, without needing the artist added to Lidarr's own
    // library first -- same lookup Lidarr itself uses for "add new artist".
    // NOTE: unlike older Lidarr versions, this does NOT include a discography
    // -- the /artist/lookup response has no "albums" field until the artist is
    // actually added to Lidarr's library. Use discography() below instead.
    suspend fun artist(musicBrainzId: String, name: String): JsonElement {
        val term = if (musicBrainzId.isNotBlank()) "lidarr:$musicBrainzId" else name
        val candidates = api.call("Lidarr", "/artist/lookup", params = mapOf("term" to term)).arr()
        if (musicBrainzId.isNotBlank()) return candidates.firstOrNull() ?: JsonNull
        // A bare-name lookup (no MusicBrainz ID to disambiguate with) can return many
        // unrelated same-named entries -- e.g. "Ken Carson" matches the modern rapper
        // plus a 1930s cowboy-film singer, an obscure "mashup?" artist, and several
        // unrelated "Ken"s, in no particular order. Real/notable entries almost always
        // carry genre tags; historical/duplicate MusicBrainz stubs almost never do, so
        // prefer whichever candidate has the most.
        return candidates.maxByOrNull { it.obj()["genres"].orEmptyArray().size } ?: JsonNull
    }
    // Full discography, split into (albums-and-EPs, singles) by MusicBrainz's
    // release-group primary-type. Resolves the artist via artist() above (which
    // also disambiguates a bare name to the right MusicBrainz ID), then queries
    // MusicBrainz's own release-group endpoint directly -- Lidarr's lookup has
    // no album list to read (see artist() above), and adding every artist a user
    // merely views to Lidarr's permanent library just to preview a discography
    // isn't acceptable. Each result keeps service="Lidarr"/kind="album" so the
    // existing download/browse actions on DiscoveryResultCard still work.
    suspend fun discography(musicBrainzId: String, name: String): Pair<List<MusicResult>, List<MusicResult>> {
        // A known identity can browse public metadata directly even while the
        // downloader is unavailable. Acquisition resolves the full Lidarr object.
        val lidarrArtist = if (musicBrainzId.isNotBlank()) jsonObject(
            "foreignArtistId" to musicBrainzId.j(), "artistName" to name.j()
        ) else artist(musicBrainzId, name)
        val resolvedId = lidarrArtist.str("foreignArtistId").ifBlank { musicBrainzId }
        if (resolvedId.isBlank()) return emptyList<MusicResult>() to emptyList()
        val releaseGroups = linkedMapOf<String, JsonElement>()
        val pageSize = 100
        var offset = 0
        // Prolific artists exceed one page. Bound work against a malformed count
        // or an endpoint ignoring offsets, and respect MusicBrainz's 1 req/sec.
        for (page in 0 until 20) {
                        val data = api.raw(
                "https://musicbrainz.org/ws/2/release-group",
                headers = mapOf("User-Agent" to "Ondo/1.0 (+https://github.com/nickshortnct-create/ondo)"),
                params = mapOf("artist" to resolvedId, "limit" to "$pageSize", "offset" to "$offset", "fmt" to "json")
            )
            val groups = data.obj()["release-groups"].orEmptyArray()
            val previousSize = releaseGroups.size
            groups.forEach { group -> group.str("id").takeIf { it.isNotBlank() }?.let { releaseGroups[it] = group } }
            offset += groups.size
            val count = data.num("release-group-count", -1.0).toInt()
            if (groups.size < pageSize || releaseGroups.size == previousSize || (count >= 0 && offset >= count)) break
        }
        // MusicBrainz doesn't return release-groups in any particular (let alone
        // chronological) order -- a prolific artist's brand-new release can land
        // anywhere in the list, e.g. position 60 of 72 for one seen in testing.
        // Sort newest-first so it's actually visible in the carousel instead of
        // buried past what "See all" shows without scrolling. ISO date strings
        // ("2026-09-18", or a bare "2026"/"2026-09" for less-precise dates) sort
        // correctly via plain string comparison; a missing date sorts last.
        val releases = releaseGroups.values
            .sortedByDescending { it.str("first-release-date") }
            .mapNotNull { rg ->
                val foreignAlbumId = rg.str("id")
                if (foreignAlbumId.isBlank()) return@mapNotNull null
                result("Lidarr", "album", JsonObject(mapOf(
                    "foreignAlbumId" to foreignAlbumId.j(),
                    "title" to rg.str("title").j(),
                    "albumType" to rg.str("primary-type", "Album").j(),
                    "artist" to lidarrArtist
                )))
            }
        val (singles, albumsAndEps) = releases.partition { it.raw.str("albumType") == "Single" }
        return albumsAndEps to singles
    }
    // Full tracklist for a release-group (album/single), from MusicBrainz's own
    // release data -- Lidarr has no per-track listing for anything not already
    // added to its library. Picks the first officially-released edition; falls
    // back to any release if none is marked official (e.g. mixtapes/leaks).
    suspend fun tracklist(releaseGroupId: String): List<TrackInfo> {
        if (releaseGroupId.isBlank()) return emptyList()
        val headers = mapOf("User-Agent" to "Ondo/1.0 (+https://github.com/nickshortnct-create/ondo)")
        val official = api.raw(
            "https://musicbrainz.org/ws/2/release",
            headers = headers,
            params = mapOf("release-group" to releaseGroupId, "inc" to "recordings", "status" to "official", "fmt" to "json")
        ).obj()["releases"].orEmptyArray()
        val releases = official.ifEmpty {
            api.raw(
                "https://musicbrainz.org/ws/2/release",
                headers = headers,
                params = mapOf("release-group" to releaseGroupId, "inc" to "recordings", "fmt" to "json")
            ).obj()["releases"].orEmptyArray()
        }
        val release = releases.firstOrNull() ?: return emptyList()
        return release.obj()["media"].orEmptyArray().flatMap { medium ->
            medium.obj()["tracks"].orEmptyArray().map { track ->
                TrackInfo(track.str("number"), track.str("title"), track.num("length").toInt().takeIf { it > 0 })
            }
        }
    }
    suspend fun browse(item: MusicResult): List<MusicResult> = when(item.service) {
        "Lidarr" -> api.call("Lidarr", "/album/lookup", params = mapOf("term" to item.title)).arr().filter { it.obj()["artist"]?.str("foreignArtistId") == item.raw.str("foreignArtistId") }.map { result("Lidarr", "album", it) }
        "KHInsider" -> {
            val album = api.raw("https://khinsider.squid.wtf/api/album", params = mapOf("url" to item.raw.str("url")))
            album.obj()["tracks"].orEmptyArray().map { track -> result("KHInsider", "track", JsonObject(track.obj() + mapOf("album" to album.str("name",item.title).j(), "artist" to album.str("albumArtist",album.str("primaryArtist",item.title)).j(), "coverUrl" to album.str("coverUrl").j()))) }
        }
        else -> emptyList()
    }
    suspend fun acquire(item: MusicResult): String = requestAcquisition(acquisitionKey(item)) { acquireInternal(item) }

    private suspend fun acquireInternal(item: MusicResult): String {
        when(item.service) {
            "Lidarr" -> {
                require(item.kind == "album") { "Choose an album from this artist first" }
                ensureAlbum(item, true)
                api.settings.markDownloadRequested("Lidarr")
                api.settings.markDownloadRequested("Soulseek")
            }
            "SoulSync" -> {
                if(item.kind == "artist") {
                    api.call("SoulSync", "/watchlist", "POST", jsonObject("artist_id" to item.id.j(), "artist_name" to item.title.j(), "source" to item.raw.str("source").j()))
                    return "Artist added to the watchlist"
                }
                val track = item.raw
                val payload = JsonObject(track.obj() + mapOf("album" to jsonObject("name" to track.str("album",item.title).j(), "images" to JsonArray(if(track.str("image_url").isBlank()) emptyList() else listOf(jsonObject("url" to track.str("image_url").j()))))))
                api.call("SoulSync", "/wishlist", "POST", jsonObject("track_data" to payload, "source_type" to "api".j(), "failure_reason" to "Requested from Ondo".j()))
                // Requests live in SoulSync's wishlist before a task appears in
                // /downloads. Keep that provider visible in Ondo's queue while
                // this durable request waits to be processed.
                api.settings.markDownloadRequested("SoulSync")
                try { api.call("SoulSync", "/wishlist/process", "POST", jsonObject("track_ids" to JsonArray(listOf(item.id.j())))) }
                catch(e: CancellationException) { throw e } catch(e: Exception) { return "Saved to the library queue. Processing will retry automatically." }
                return "Adding to your library. Follow progress in Downloads."
            }
            "Squid", "KHInsider" -> {
                require(item.kind == "track") { "Open the album and select a track" }
                val extra = item.raw.obj()["more_info"] ?: JsonNull
                val url = if(item.service == "Squid") decryptSaavnUrl(extra.str("encrypted_media_url")) else item.raw.str("url")
                val queued = api.call("Squid", "/squid/tracks", "POST", jsonObject("url" to url.j(), "title" to item.title.j(), "artist" to item.artist.j(), "album" to (if(item.service == "Squid") extra.str("album") else item.raw.str("album")).j(), "coverUrl" to item.raw.str("coverUrl", item.raw.str("image")).j(), "ext" to (if(item.service == "Squid") "m4a" else url.substringBefore('?').substringAfterLast('.',"mp3")).j()))
                requireAcceptedTask(queued)
                api.settings.markDownloadRequested("Squid")
            }
            else -> error("Choose a downloader for this result")
        }
        return "Adding to your library. Follow progress in Downloads."
    }
    // Fan a discography album out to every connected downloader instead of
    // just Lidarr -- Lidarr's own indexers (usenet/torrent) often come back
    // empty on obscure/underground releases that SoulSync (Soulseek) or
    // Squid/KHInsider still have. Runs every configured source in parallel,
    // best-effort: one source erroring never blocks the others, and the
    // summary string reports what actually happened on each.
    suspend fun acquireEverywhere(item: MusicResult): String = requestAcquisition(acquisitionKey(item)) { acquireEverywhereInternal(item) }

    private suspend fun acquireEverywhereInternal(item: MusicResult): String {
        require(item.service == "Lidarr" && item.kind == "album") { "Choose an album from this artist first" }
        val services = combinedSearchProviders.filter { api.settings.get(if (it == "KHInsider") "Squid" else it).url.isNotBlank() }
        require(services.isNotEmpty()) { "Connect a music source in Integrations before adding music" }
        val outcomes = coroutineScope {
            services.map { service ->
                async {
                    val outcome = try {
                        Result.success(if (service == "Lidarr") { acquireInternal(item); -1 } else acquireAlbumElsewhere(service, item))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Result.failure<Int>(e) }
                    service to outcome
                }
            }
        }.awaitAll()
        val summary = outcomes.joinToString(", ") { (service, outcome) ->
            outcome.fold(
                onSuccess = { count -> if (count < 0) "Lidarr (searching its indexers)" else "$service ($count track${if (count == 1) "" else "s"} requested)" },
                onFailure = { "$service (${it.message ?: "failed"})" }
            )
        }
        check(outcomes.any { (_, outcome) -> outcome.getOrNull()?.let { it != 0 } == true }) {
            "No matching album was queued. $summary"
        }
        return if (outcomes.any { it.second.isFailure }) "Request queued. Some sources were unavailable; follow progress in Downloads."
        else "Adding to your library. Follow progress in Downloads."
    }
    // A free-text search can return another artist's same-named album. Only
    // request tracks whose artist and album identities both match; deluxe,
    // live and unrelated editions must be selected explicitly.
    private suspend fun acquireAlbumElsewhere(service: String, item: MusicResult): Int {
        val query = "${item.artist} ${item.title}"
        fun albumOf(track: MusicResult): String {
            val nested = (track.raw.obj()["more_info"] ?: JsonNull).str("album")
            return nested.ifBlank { track.raw.str("album") }
        }
        fun matchesAlbum(candidate: String): Boolean = acquisitionIdentity(candidate) == acquisitionIdentity(item.title) && candidate.isNotBlank()
        fun matchesTrack(candidate: MusicResult): Boolean = matchesAcquisitionAlbum(item.title, item.artist, albumOf(candidate), candidate.artist)
        val tracks: List<MusicResult> = when (service) {
            "SoulSync", "Squid" -> search(service, query).filter { it.kind == "track" && matchesTrack(it) }
            "KHInsider" -> search(service, query).filter { it.kind == "album" && matchesAlbum(it.title) }
                .flatMap {
                    try { browse(it) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                }.filter { matchesTrack(it) }
            else -> emptyList()
        }
        var requested = 0
        for (track in tracks) {
            try { acquire(track); requested++ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { /* best-effort -- one bad match shouldn't block the rest */ }
        }
        return requested
    }
    /**
     * @param monitor false only for browsing releases: Lidarr must not monitor (and later download)
     * an album just because the user opened the release picker.
     */
    /**
     * The Lidarr folder and profiles new artists are added with. Anything not picked in
     * Integrations is filled in from Lidarr (its first folder, the "Lossless" and
     * "Standard" profiles when they exist) and remembered.
     */
    private suspend fun lidarrDefaults(): ServiceConfig {
        val cfg = api.settings.get("Lidarr")
        if (cfg.root.isNotBlank() && cfg.quality.toIntOrNull() != null && cfg.metadata.toIntOrNull() != null) return cfg
        fun pick(rows: JsonArray, preferred: String) = (rows.firstOrNull { it.str("name").equals(preferred, ignoreCase = true) } ?: rows.firstOrNull())?.str("id").orEmpty()
        val filled = cfg.copy(
            root = cfg.root.ifBlank { api.call("Lidarr", "/rootfolder").arr().firstOrNull()?.str("path").orEmpty() },
            quality = cfg.quality.takeIf { it.toIntOrNull() != null } ?: pick(api.call("Lidarr", "/qualityprofile").arr(), "Lossless"),
            metadata = cfg.metadata.takeIf { it.toIntOrNull() != null } ?: pick(api.call("Lidarr", "/metadataprofile").arr(), "Standard"),
        )
        require(filled.root.isNotBlank() && filled.quality.toIntOrNull() != null && filled.metadata.toIntOrNull() != null) { "Lidarr has no root folder or profiles set up yet" }
        api.settings.save("Lidarr", filled)
        return filled
    }

    suspend fun ensureAlbum(item: MusicResult, search: Boolean, monitor: Boolean = true): JsonElement {
        // A MusicResult built from discography()'s MusicBrainz data only carries
        // enough fields to display a card (foreignAlbumId/title/albumType/artist),
        // never a real Lidarr id -- resolve it by the exact foreignAlbumId (same
        // lidarr:<id> term syntax artist() uses) to find out whether Lidarr
        // already has it, and if not, to get the full Album resource shape
        // (releases, media, duration, etc.) its POST /album validation needs.
        // Unlike a bare-text term, this is an exact match, not a fuzzy search.
        val resolved = if (item.raw.num("id") <= 0 && item.raw.obj()["releases"] == null) {
            api.call("Lidarr", "/album/lookup", params = mapOf("term" to "lidarr:${item.raw.str("foreignAlbumId")}")).arr().firstOrNull() ?: item.raw
        } else item.raw
        if (resolved.num("id") > 0) {
            // Already in Lidarr's library (e.g. added on an earlier tap, or found
            // via a real album/lookup elsewhere) -- POSTing it again as a new
            // album fails validation ("This album has already been added").
            // Just (re-)monitor it and, if asked, trigger a fresh search.
            val albumId = resolved.obj().getValue("id")
            if (monitor) api.call("Lidarr", "/album/monitor", "PUT", jsonObject("albumIds" to JsonArray(listOf(albumId)), "monitored" to true.j()))
            if (search) api.call("Lidarr", "/command", "POST", jsonObject("name" to "AlbumSearch".j(), "albumIds" to JsonArray(listOf(albumId))))
            return resolved
        }
        val cfg = lidarrDefaults()
        val artist = JsonObject(resolved.obj()["artist"].objOrEmpty() + mapOf("qualityProfileId" to cfg.quality.toInt().j(), "metadataProfileId" to cfg.metadata.toInt().j(), "rootFolderPath" to cfg.root.j(), "monitored" to monitor.j(), "monitorNewItems" to "none".j(), "addOptions" to jsonObject("monitor" to "none".j(), "searchForMissingAlbums" to false.j())))
        return api.call("Lidarr", "/album", "POST", JsonObject(resolved.obj() + mapOf("artist" to artist, "monitored" to monitor.j(), "anyReleaseOk" to true.j(), "addOptions" to jsonObject("addType" to "automatic".j(), "searchForNewAlbum" to search.j()))))
    }
    suspend fun releases(item: MusicResult): List<MusicResult> {
        val album = ensureAlbum(item, search = false, monitor = false)
        return api.call("Lidarr", "/release", params = mapOf("albumId" to album.str("id"))).arr().map { release ->
            result("Lidarr", "release", JsonObject(release.obj() + mapOf("artist" to item.artist.j(), "album" to album)))
        }
    }
    suspend fun grab(item: MusicResult): JsonElement {
        require(item.service == "Lidarr" && item.kind == "release" && item.raw.str("guid").isNotBlank() && item.raw.num("indexerId") > 0) { "Choose a valid release before adding it" }
        return requestAcquisitionData(acquisitionKey(item)) {
            val result = api.call("Lidarr", "/release", "POST", jsonObject("guid" to item.raw.str("guid").j(), "indexerId" to item.raw.num("indexerId").toInt().j()))
            // Choosing a release is the user's decision to get this album, so it is monitored now
            // (releases() deliberately left it unmonitored while browsing).
            item.raw.obj()["album"]?.obj()?.get("id")?.let { albumId ->
                api.call("Lidarr", "/album/monitor", "PUT", jsonObject("albumIds" to JsonArray(listOf(albumId)), "monitored" to true.j()))
            }
            api.settings.markDownloadRequested("Lidarr")
            result
        }
    }
    suspend fun fallback(artist: String, title: String, album: String): String {
        require(artist.isNotBlank() && title.isNotBlank()) { "Enter both artist and song title" }
        return requestAcquisition(acquisitionKey("track", title, artist)) {
            val result = api.call("YouTube", "/fallback/tracks", "POST", jsonObject("artist" to artist.j(), "title" to title.j(), "album" to album.j()))
            requireAcceptedTask(result)
            api.settings.markDownloadRequested("YouTube")
            "Adding to your library. Follow progress in Downloads."
        }
    }
    suspend fun searchYouTube(query: String): List<YouTubeVideo> {
        require(query.isNotBlank()) { "Enter a video, song, or artist" }
        return api.call("YouTube", "/fallback/search", params = mapOf("q" to query)).obj()["items"].orEmptyArray().mapNotNull { item ->
            val id = item.str("id")
            if (id.isBlank()) return@mapNotNull null
            YouTubeVideo(
                id = id,
                title = item.str("title"),
                channel = item.str("channel"),
                durationMs = item.num("duration").takeIf { it > 0 }?.let { (it * 1000).toLong() },
                thumbnail = item.str("thumbnail"),
                album = item.str("album"),
                official = item.flag("official")
            )
        }
    }
    suspend fun downloadYouTubeVideo(video: YouTubeVideo): JsonElement {
        require(Regex("^[A-Za-z0-9_-]{11}$").matches(video.id)) { "This video has an invalid ID" }
        require(video.channel.isNotBlank() && video.title.isNotBlank()) { "This video is missing its artist or title" }
        val key = acquisitionKey("track", video.title, video.channel)
        return requestAcquisitionData(key) {
            val result = api.call("YouTube", "/fallback/videos", "POST", jsonObject(
                "videoId" to video.id.j(),
                "artist" to video.channel.j(),
                "title" to video.title.j(),
                // Left blank for plain uploads, so decknas looks up the song's real details.
                "album" to video.album.j(),
                "durationMs" to (video.durationMs ?: 0L).j()
            ))
            requireAcceptedTask(result)
            api.settings.markDownloadRequested("YouTube")
            result
        }
    }
    fun youtubeSearchUrl(query: String): String {
        require(query.isNotBlank()) { "Enter a video title or YouTube link" }
        val idPattern = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/))([A-Za-z0-9_-]{11})")
        val exactId = idPattern.find(query)?.groupValues?.get(1)
            ?: Regex("^[A-Za-z0-9_-]{11}$").matchEntire(query.trim())?.value
        return if (exactId != null) "https://www.youtube.com/watch?v=$exactId"
        else "https://www.youtube.com/results?search_query=${query.encodeURLParameter()}"
    }
    suspend fun queue(service: String): List<DownloadRow> {
        val data = api.call(if (service == "Soulseek") "YouTube" else service, when(service) { "Lidarr" -> "/queue"; "SoulSync" -> "/downloads"; "Squid" -> "/squid/tasks"; "Soulseek" -> "/soularr/downloads"; else -> "/fallback/tasks" }, params = mapOf("limit" to "200", "pageSize" to "100", "includeArtist" to "true", "includeAlbum" to "true"))
        val key = when(service) { "Lidarr" -> "records"; "SoulSync" -> "downloads"; else -> "tasks" }
        // The yt-dlp fallback API also downloads into other users' Navidrome libraries
        // (Sophie's, via the Subsonic proxy) and tags those tasks with their library.
        // Only the shared library's downloads belong in this app's queue.
        val queueRows = data.obj()[key].orEmptyArray().filter { row -> row.str("library").let { it.isBlank() || it == "music" } }.map { row ->
            val status = if (service == "Lidarr") lidarrQueueStatus(row) else row.str("status")
            val detail = row.str("errorMessage", row.str("error")).ifBlank { row.str("detail") }.ifBlank {
                row.obj()["statusMessages"].orEmptyArray().joinToString("\n") { statusMessage ->
                    statusMessage.obj()["messages"].orEmptyArray().joinToString(" ") { (it as? JsonPrimitive)?.content.orEmpty() }
                }
            }
            DownloadRow(service, row.str("id").ifBlank { "${row.str("title")}:${row.str("createdAt")}" }, row.str("title", row.str("track_name", row.obj()["album"]?.str("title") ?: "Requested music")), status, detail, queueProgress(service, row), row)
        }
        if (service != "SoulSync") return queueRows

        // SoulSync keeps a track in /wishlist from the initial request until
        // the download succeeds, while /downloads can be empty before its
        // worker creates a task. Show these durable requests as pending rows.
        val visibleTasks = queueRows.filter { it.state != DownloadQueueState.CANCELLED }
        val visibleKeys = visibleTasks.map { trackKey(it.title, it.artist) }.toSet()
        val wishlist = try {
            api.call("SoulSync", "/wishlist").obj()["tracks"].orEmptyArray()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return queueRows
        }
        val pendingRows = wishlist.mapNotNull { request ->
            val title = request.str("track_name").ifBlank { request.obj()["track_data"]?.str("name") ?: "Requested song" }
            val artist = request.str("artist_name").ifBlank { request.obj()["track_data"]?.let { result("SoulSync", "track", it).artist }.orEmpty() }
            if (trackKey(title, artist) in visibleKeys) return@mapNotNull null
            val status = soulSyncWishlistStatus(request)
            DownloadRow(
                service = "SoulSync",
                id = "wishlist:${request.str("id")}",
                title = title,
                status = status,
                detail = if (status == "not_found") {
                    "SoulSync searched Soulseek ${request.num("retry_count").toInt()} times and couldn’t find it. Try YouTube, or remove the request."
                } else "Waiting for SoulSync to process this request.",
                progress = null,
                raw = request
            )
        }
        return queueRows + pendingRows
    }
    suspend fun queueAll(): DownloadQueueSnapshot = queuePollMutex.withLock { coroutineScope {
        val services = providers.filter { api.settings.get(it).url.isNotBlank() && api.settings.shouldPollDownloads(it) }
        val outcomes = services.map { service -> async {
            val result = try {
                val rows = withTimeoutOrNull(12_000L) { queue(service) }
                    ?: error("Taking too long to respond. Try refreshing.")
                Result.success(rows)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure<List<DownloadRow>>(e) }
            service to result
        } }.awaitAll()
        val rowsByService = outcomes.mapNotNull { (service, result) -> result.getOrNull()?.let { service to it.distinctBy { row -> row.key } } }.toMap()
        observedQueueRows.keys.retainAll(services.toSet())
        observedQueueRows.putAll(rowsByService)
        // A failed task from one source must not clear an accepted request
        // still running elsewhere, including a source temporarily unavailable.
        val acceptedKeys = observedQueueRows.values.flatten().filter { it.state == DownloadQueueState.ACTIVE || it.state == DownloadQueueState.COMPLETE }.map { acquisitionKey(it) }.toSet()
        val failedKeys = rowsByService.values.flatten().filter { it.state == DownloadQueueState.FAILED }.map { acquisitionKey(it) }.toSet() - acceptedKeys
        if (failedKeys.isNotEmpty()) _requestedItems.update { it - failedKeys }
        rowsByService.forEach { (service, rows) ->
            val completed = rows.filter { it.state == DownloadQueueState.COMPLETE }.map { it.key }.toSet()
            val previouslyCompleted = observedCompletions[service]
            if (previouslyCompleted != null && (completed - previouslyCompleted).isNotEmpty()) _libraryRevision.value++
            observedCompletions[service] = completed
        }
        DownloadQueueSnapshot(rowsByService, outcomes.mapNotNull { (service, result) -> result.exceptionOrNull()?.let { service to (it.message ?: "Could not update downloads") } }.toMap())
    } }
    suspend fun retry(row: DownloadRow): String {
        require(row.canRetry) { "This source does not support retrying this request from Ondo" }
        return coalesceAcquisition("retry:${row.key}", acquisitionKey(row)) {
            when (row.service) {
                "Lidarr" -> {
                    val albumId = row.raw.obj()["album"]?.num("id")?.takeIf { it > 0 } ?: row.raw.num("albumId")
                    api.call("Lidarr", "/command", "POST", jsonObject("name" to "AlbumSearch".j(), "albumIds" to JsonArray(listOf(albumId.toInt().j()))))
                }
                "SoulSync" -> {
                    // Only stuck wishlist requests can be retried (canRetry): hand the song to the
                    // YouTube fallback, then drop the SoulSync request so it stops spinning.
                    val album = row.raw.str("album_name")
                    val result = api.call("YouTube", "/fallback/tracks", "POST", jsonObject("artist" to row.artist.j(), "title" to row.title.j(), "album" to album.j()))
                    requireAcceptedTask(result)
                    api.call("SoulSync", "/wishlist/${row.wishlistTrackId.encodeURLParameter()}", "DELETE")
                    api.settings.markDownloadRequested("YouTube")
                    _requestedItems.update { it + acquisitionKey("track", row.title, row.artist) }
                    return@coalesceAcquisition "Trying YouTube. Follow progress in Downloads."
                }
                "YouTube" -> {
                    val videoId = row.raw.str("videoId")
                    val result = api.call("YouTube", if (videoId.isBlank()) "/fallback/tracks" else "/fallback/videos", "POST", JsonObject(row.raw.obj().filterKeys { it in setOf("videoId", "artist", "title", "album", "durationMs") }))
                    check(result.obj()["task"]?.let { downloadQueueState(it.str("status")) != DownloadQueueState.FAILED } != false) { "The source could not retry this request. Try again later." }
                }
            }
            api.settings.markDownloadRequested(row.service)
            _requestedItems.update { it + acquisitionKey(row) }
            "Trying again. Follow progress in Downloads."
        }
    }
    suspend fun cancel(row: DownloadRow) {
        require(row.canCancel) { "This source does not support cancelling this request from Ondo" }
        when(row.service) {
            "Lidarr" -> api.call("Lidarr", "/queue/${row.id}", "DELETE", params = mapOf("removeFromClient" to "true", "blocklist" to "false"))
            "SoulSync" -> api.call("SoulSync", "/wishlist/${row.wishlistTrackId.encodeURLParameter()}", "DELETE")
            else -> error("Use the service’s web interface to cancel this download")
        }
        _requestedItems.update { it - acquisitionKey(row) }
    }
    suspend fun wishlist() = api.call("SoulSync", "/wishlist").obj()["tracks"].orEmptyArray()
    suspend fun watchlist() = api.call("SoulSync", "/watchlist").obj()["artists"].orEmptyArray()
}
fun JsonElement?.orEmptyArray() = this as? JsonArray ?: JsonArray(emptyList())
fun JsonElement?.objOrEmpty() = this as? JsonObject ?: JsonObject(emptyMap())
private fun trackKey(title: String, artist: String): String = acquisitionKey("track", title, artist)
expect fun decryptSaavnUrl(encrypted: String): String
