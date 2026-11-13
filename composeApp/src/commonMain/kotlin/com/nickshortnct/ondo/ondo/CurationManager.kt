package com.nickshortnct.ondo.ondo

import com.russhwolf.settings.Settings
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.domain.manager.PlaylistCoverManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.util.Logger

/** Where the last downloaded curation is kept between launches (it's ~200 KB, too big for preferences). */
expect class CurationCacheStorage {
	fun read(): String?
	fun write(text: String)
}

/**
 * Keeps the weekly "Made for you" mixes in step with the server's curator.
 *
 * Fetches the curation (mixes + song categories) from the decknas curator service with the user's
 * Navidrome login, then makes each mix a real Navidrome playlist owned by the user. Mix playlists are
 * recognised by the marker at the end of their comment ([mixComment]), so every device updates the same
 * playlists instead of creating its own, and downloads, Android Auto and the watch see them as playlists.
 */
class CurationManager(
	private val settings: Settings,
	private val sessionManager: SessionManager,
	private val ondoApi: OndoApi,
	private val playlistSyncFetcher: PlaylistSyncFetcher,
	private val dbRepository: DbRepository,
	private val storage: CurationCacheStorage,
	private val playlistCoverManager: PlaylistCoverManager,
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mutex = Mutex()
	private val http = ondoHttpClient {
		install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 }
	}

	val curation: StateFlow<Curation?>
		field = MutableStateFlow<Curation?>(null)

	/** True while mixes are being fetched or written to playlists. */
	val syncing: StateFlow<Boolean>
		field = MutableStateFlow(false)

	private val scopeKey get() = settings.getString("instanceUrl", "").trimEnd('/') + "|" + sessionManager.username

	init {
		scope.launch {
			runCatching { storage.read() }.getOrNull()?.let { cached ->
				val owner = cached.substringBefore('\n')
				if (owner == scopeKey) {
					runCatching { ondoJson.decodeFromString<Curation>(cached.substringAfter('\n')) }
						.onSuccess { if (curation.value == null) curation.value = it }
				}
			}
		}
		scope.launch {
			sessionManager.isLoggedIn.collect { loggedIn ->
				if (loggedIn) refresh(force = false) else curation.value = null
			}
		}
	}

	fun refreshAsync(force: Boolean) {
		scope.launch { refresh(force) }
	}

	suspend fun refresh(force: Boolean) = mutex.withLock {
		syncing.value = true
		try {
			val fetchedAgo = Clock.System.now().toEpochMilliseconds() - settings.getLong(KEY_FETCHED_AT, 0)
			if (force || curation.value == null || fetchedAgo !in 0..REFETCH_AFTER_MS) fetch()
			curation.value?.let { syncMixPlaylists(it) }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "Couldn't refresh the weekly mixes", e)
		} finally {
			syncing.value = false
		}
	}

	private fun authHeaders(): Map<String, String>? {
		val auth = ondoApi.navidromeAuth?.invoke().orEmpty()
		if (listOf("u", "t", "s").any { auth[it].isNullOrBlank() }) return null
		return mapOf("X-Navidrome-User" to auth.getValue("u"), "X-Navidrome-Token" to auth.getValue("t"), "X-Navidrome-Salt" to auth.getValue("s"))
	}

	private suspend fun fetch() {
		val auth = ondoApi.navidromeAuth?.invoke().orEmpty()
		if (listOf("u", "t", "s").any { auth[it].isNullOrBlank() }) return
		var lastError: Exception? = null
		for (url in candidateUrls()) {
			try {
				val response = http.get(url) {
					header("X-Navidrome-User", auth["u"])
					header("X-Navidrome-Token", auth["t"])
					header("X-Navidrome-Salt", auth["s"])
					val etag = settings.getString(KEY_ETAG, "")
					if (etag.isNotBlank() && curation.value != null) header(HttpHeaders.IfNoneMatch, etag)
				}
				when (response.status.value) {
					304 -> Unit
					in 200..299 -> {
						val text = response.bodyAsText()
						val parsed = ondoJson.decodeFromString<Curation>(text)
						curation.value = parsed
						runCatching { storage.write(scopeKey + "\n" + text) }
							.onFailure { Logger.w(TAG, "Couldn't cache the curation", it) }
						settings.putString(KEY_ETAG, response.headers[HttpHeaders.ETag].orEmpty())
					}
					else -> throw OndoHttpException(response.status.value, "Curator returned HTTP ${response.status.value}")
				}
				settings.putLong(KEY_FETCHED_AT, Clock.System.now().toEpochMilliseconds())
				settings.putString(KEY_WORKING_URL, url)
				return
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				lastError = e
			}
		}
		lastError?.let { throw it }
	}

	/** The curator lives next to Navidrome on the same host; fall back to the public decknas address. */
	private fun candidateUrls(): List<String> {
		val derived = runCatching {
			val instance = Url(settings.getString("instanceUrl", ""))
			URLBuilder(instance).apply { encodedPath = CURATOR_PATH; parameters.clear() }.buildString()
		}.getOrNull()?.takeIf { it.startsWith("http") }
		return listOfNotNull(derived, DEFAULT_CURATOR_URL).distinct()
	}

	private suspend fun syncMixPlaylists(curation: Curation) {
		val week = curation.week ?: return
		if (curation.mixes.isEmpty()) return
		val username = sessionManager.username
		val all = playlistSyncFetcher.fetchAllPlaylists()
		// Without owners the server sent a stripped list, and the mixes in it can't be recognised.
		// Creating them anyway is what left nine copies of every mix, so wait for a proper list.
		if (all.isNotEmpty() && all.all { it.owner.isBlank() }) {
			Logger.w(TAG, "Playlist list has no owners or comments; not touching the weekly mixes")
			return
		}
		val existing = all
			.filter { it.owner.equals(username, ignoreCase = true) }
			.mapNotNull { entity -> parseMixTag(entity.comment)?.let { it to entity } }
		// Per slot, the copy to keep comes first: this week's, then the most recently changed.
		val bySlot = existing
			.sortedWith(compareByDescending<Pair<MixTag, PlaylistEntity>> { it.first.week == week }.thenByDescending { it.second.modifiedAt })
			.groupBy({ it.first.slot }, { it.second })
		val api = sessionManager.api
		val changed = mutableListOf<String>()
		val created = mutableMapOf<String, String>()
		var removed = false

		for (mix in curation.mixes) {
			if (mix.songIds.isEmpty()) continue
			val comment = mixComment(mix, week)
			val current = bySlot[mix.slot].orEmpty()
			val playlist = current.firstOrNull()
			if (playlist == null) {
				val newPlaylist = api.createPlaylist(name = mix.title, songIds = mix.songIds)
				try {
					api.updatePlaylist(id = newPlaylist.id, comment = comment)
				} catch (e: Exception) {
					// Untagged, it would never be recognised as this mix again.
					runCatching { api.deletePlaylist(newPlaylist.id) }
					throw e
				}
				changed += newPlaylist.id
				created[mix.slot] = newPlaylist.id
			} else if (playlist.comment != comment || playlist.name != mix.title || playlist.songCount > mix.songIds.size) {
				// More songs than the mix: the curator dropped some mid-week (e.g. Sophie's library
				// songs). Fewer is left alone, since ids the server no longer knows are skipped on add.
				// Same playlist id every week, so a mix stays pinned/downloaded in the same place.
				api.updatePlaylist(
					id = playlist.playlistId,
					name = mix.title,
					comment = comment,
					songIdsToAdd = mix.songIds,
					songIndicesToRemove = (0 until playlist.songCount).toList()
				)
				changed += playlist.playlistId
			}
			// Extra copies of a slot (two devices racing, or the duplicates older versions made): keep one.
			current.drop(1).forEach { api.deletePlaylist(it.playlistId); removed = true }
		}
		val activeSlots = curation.mixes.filter { it.songIds.isNotEmpty() }.map { it.slot }.toSet()
		bySlot.filterKeys { it !in activeSlots }.values.flatten().forEach {
			api.deletePlaylist(it.playlistId)
			removed = true
		}

		if (changed.isNotEmpty() || removed) {
			dbRepository.syncPlaylists().getOrThrow()
			changed.forEach { dbRepository.syncPlaylistSongs(it).getOrThrow() }
			Logger.i(TAG, "Weekly mixes for $week: ${changed.size} updated")
		}
		applyCovers(curation, created + bySlot.mapValues { it.value.first().playlistId })
	}

	/** Downloads each mix's generated cover and sets it as the playlist's cover on this device. */
	private suspend fun applyCovers(curation: Curation, playlistIdBySlot: Map<String, String>) {
		val headers = authHeaders() ?: return
		val base = settings.getString(KEY_WORKING_URL, "").substringBeforeLast("/v1/curation", "")
		if (base.isBlank()) return
		var synced = false
		for (mix in curation.mixes) {
			val version = mix.cover ?: continue
			val playlistId = playlistIdBySlot[mix.slot] ?: continue
			val key = "$KEY_COVER_PREFIX$playlistId"
			if (settings.getString(key, "") == version) continue
			try {
				val response = http.get("$base/v1/covers/${mix.slot}.jpg") { headers.forEach { (k, v) -> header(k, v) } }
				if (response.status.value !in 200..299) continue
				val bytes = response.readRawBytes()
				if (!synced) {
					// The cover is stored against the playlist row, so make sure the row exists locally.
					dbRepository.syncPlaylists().getOrThrow()
					synced = true
				}
				playlistCoverManager.setCustomCover(playlistId, bytes)
				settings.putString(key, version)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.w(TAG, "Couldn't fetch the cover for ${mix.slot}", e)
			}
		}
	}

	private companion object {
		const val TAG = "CurationManager"
		const val KEY_ETAG = "ondo.curation.etag"
		const val KEY_FETCHED_AT = "ondo.curation.fetchedAt"
		const val KEY_WORKING_URL = "ondo.curation.url"
		const val KEY_COVER_PREFIX = "ondo.curation.cover."
		const val CURATOR_PATH = "/ondo-curation/v1/curation"
		const val DEFAULT_CURATOR_URL = "https://decknas.duckdns.org/ondo-curation/v1/curation"
		const val REFETCH_AFTER_MS = 3L * 60 * 60 * 1000
	}
}
