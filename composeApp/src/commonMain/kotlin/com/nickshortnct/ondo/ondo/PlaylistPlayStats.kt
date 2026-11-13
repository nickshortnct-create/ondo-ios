package com.nickshortnct.ondo.ondo

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.domain.manager.SessionManager

@Serializable
data class PlaylistPlays(val count: Int = 0, val lastPlayedAt: Long = 0)

/** Most played first, then most recently played; never-played playlists keep alphabetical order at the end. */
fun <T> sortByPlays(items: List<T>, plays: Map<String, PlaylistPlays>, id: (T) -> String, name: (T) -> String): List<T> =
	items.sortedWith(
		compareByDescending<T> { plays[id(it)]?.count ?: 0 }
			.thenByDescending { plays[id(it)]?.lastPlayedAt ?: 0 }
			.thenBy { name(it).lowercase() }
	)

/**
 * How many times each playlist has been played on this device (from the app or Android Auto), per server
 * and account, so "Add to playlist" can offer the playlists you actually listen to first. Navidrome
 * doesn't count playlist plays itself.
 */
class PlaylistPlayStats(private val settings: Settings, private val sessionManager: SessionManager) {
	private val key get() = "ondo.playlistPlays." + settings.getString("instanceUrl", "").trimEnd('/') + "|" + sessionManager.username
	private var loadedKey: String? = null
	private val plays = MutableStateFlow<Map<String, PlaylistPlays>>(emptyMap())

	private fun current(): Map<String, PlaylistPlays> {
		val key = key
		if (loadedKey != key) {
			// Another account or server: its counts are separate.
			plays.value = runCatching { ondoJson.decodeFromString<Map<String, PlaylistPlays>>(settings.getString(key, "{}")) }
				.getOrDefault(emptyMap())
			loadedKey = key
		}
		return plays.value
	}

	fun record(playlistId: String) {
		if (playlistId.isBlank()) return
		current()
		val now = Clock.System.now().toEpochMilliseconds()
		plays.update { map ->
			val previous = map[playlistId] ?: PlaylistPlays()
			map + (playlistId to PlaylistPlays(previous.count + 1, now))
		}
		settings.putString(key, ondoJson.encodeToString(plays.value))
	}

	fun sorted(playlists: List<PlaylistEntity>): List<PlaylistEntity> =
		sortByPlays(playlists, current(), { it.playlistId }, { it.name })
}
