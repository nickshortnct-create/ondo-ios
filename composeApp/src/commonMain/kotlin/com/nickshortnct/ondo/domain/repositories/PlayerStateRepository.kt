package com.nickshortnct.ondo.domain.repositories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.ui.core.PlayerUiState
import com.nickshortnct.ondo.util.Logger
import com.nickshortnct.ondo.util.chunkedIn

/**
 * Persists what is needed to restore the player: the queue as song ids (not full songs),
 * the current index, shuffle/repeat/speed and the playback position. The old format stored the
 * whole [PlayerUiState] (full queue plus the current collection) as one JSON blob and rewrote
 * it every second; that was megabytes of JSON per write for a big queue.
 */
class PlayerStateRepository(
	private val preferences: DataStore<Preferences>,
	private val songDao: SongDao
) {
	private val json = Json {
		ignoreUnknownKeys = true
	}
	private val idsSerializer = ListSerializer(String.serializer())
	private val songsSerializer = ListSerializer(DomainSong.serializer())

	/** Waits for DataStore's initial disk read, returning null when no queue was saved. */
	suspend fun readState(): PlayerUiState? = try {
		val data = preferences.data.first()
		decode(data) ?: migrateLegacy(data)
	} catch (ex: CancellationException) {
		throw ex
	} catch (ex: Exception) {
		Logger.e("PlayerStateRepository", "failed to load saved state", ex)
		null
	}

	/**
	 * Saves queue, index, flags and position in one edit. Callers invoke it when the queue or
	 * playback flags change (and on pause); position-only updates go through [setPosition].
	 */
	suspend fun setState(value: PlayerUiState) {
		try {
			preferences.edit {
				it[KEY_QUEUE_IDS] = json.encodeToString(idsSerializer, value.queue.map(DomainSong::id))
				// Songs that cannot be reloaded from the library (radio streams, external results)
				// are kept whole.
				it[KEY_EXTRA_SONGS] = json.encodeToString(songsSerializer, value.queue.filter(::needsFullCopy))
				it[KEY_INDEX] = value.currentIndex
				it[KEY_SHUFFLE] = value.isShuffleEnabled
				it[KEY_REPEAT] = value.repeatMode
				it[KEY_SPEED] = value.playbackSpeed
				it[KEY_PROGRESS] = value.progress
				it.remove(KEY_LEGACY_STATE)
			}
		} catch (ex: CancellationException) {
			throw ex
		} catch (ex: SerializationException) {
			Logger.e("PlayerStateRepository", "failed to serialise state", ex)
		} catch (ex: Exception) {
			Logger.e("PlayerStateRepository", "failed to save state", ex)
		}
	}

	suspend fun clear() { preferences.edit { it.clear() } }

	suspend fun setPosition(progress: Float) {
		try {
			preferences.edit { it[KEY_PROGRESS] = progress }
		} catch (ex: CancellationException) {
			throw ex
		} catch (ex: Exception) {
			Logger.e("PlayerStateRepository", "failed to save position", ex)
		}
	}

	private suspend fun decode(data: Preferences): PlayerUiState? {
		val ids = data[KEY_QUEUE_IDS]?.let { json.decodeFromString(idsSerializer, it) } ?: return null
		if (ids.isEmpty()) return null
		val extras = data[KEY_EXTRA_SONGS]
			?.let { runCatching { json.decodeFromString(songsSerializer, it) }.getOrNull() }
			.orEmpty()
		val fromLibrary = chunkedIn(ids) { songDao.getSongsByIds(it) }.map { it.toDomainModel() }
		val (queue, index) = restoreQueue(
			ids = ids,
			savedIndex = data[KEY_INDEX] ?: -1,
			songsById = (extras + fromLibrary).associateBy { it.id }
		)
		if (queue.isEmpty()) return null
		return PlayerUiState(
			queue = queue,
			currentIndex = index,
			currentSong = queue.getOrNull(index),
			isShuffleEnabled = data[KEY_SHUFFLE] ?: false,
			repeatMode = data[KEY_REPEAT] ?: 0,
			playbackSpeed = data[KEY_SPEED] ?: 1f,
			progress = data[KEY_PROGRESS] ?: 0f
		)
	}

	/** Reads the old single-blob format once, converts it to the new keys and removes it. */
	private suspend fun migrateLegacy(data: Preferences): PlayerUiState? {
		val encoded = data[KEY_LEGACY_STATE] ?: return null
		val legacy = try {
			json.decodeFromString<PlayerUiState>(encoded)
		} catch (ex: Exception) {
			Logger.e("PlayerStateRepository", "failed to read legacy state", ex)
			null
		}
		if (legacy != null) setState(legacy) else preferences.edit { it.remove(KEY_LEGACY_STATE) }
		return legacy?.takeIf { it.queue.isNotEmpty() }
	}

	private fun needsFullCopy(song: DomainSong) = song.isExternal || song.id.startsWith("radio_")

	private companion object {
		val KEY_LEGACY_STATE = stringPreferencesKey("player_state")
		val KEY_QUEUE_IDS = stringPreferencesKey("player_queue_ids")
		val KEY_EXTRA_SONGS = stringPreferencesKey("player_queue_extra_songs")
		val KEY_INDEX = intPreferencesKey("player_index")
		val KEY_SHUFFLE = booleanPreferencesKey("player_shuffle")
		val KEY_REPEAT = intPreferencesKey("player_repeat")
		val KEY_SPEED = floatPreferencesKey("player_speed")
		val KEY_PROGRESS = floatPreferencesKey("player_progress")
	}
}

/**
 * Maps saved ids back to songs, dropping ids that no longer exist, and moves the saved index so
 * it still points at the same song (or the next surviving one if that song is gone).
 */
internal fun restoreQueue(
	ids: List<String>,
	savedIndex: Int,
	songsById: Map<String, DomainSong>
): Pair<List<DomainSong>, Int> {
	val queue = ids.mapNotNull(songsById::get)
	if (queue.isEmpty()) return queue to -1
	val survivorsBefore = ids.take(savedIndex.coerceAtLeast(0)).count { it in songsById }
	return queue to survivorsBefore.coerceIn(0, queue.lastIndex)
}
