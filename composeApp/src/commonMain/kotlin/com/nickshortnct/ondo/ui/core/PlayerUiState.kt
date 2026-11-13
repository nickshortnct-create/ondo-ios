package com.nickshortnct.ondo.ui.core

import kotlinx.serialization.Serializable
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection

@Serializable
data class PlayerUiState(
	val queue: List<DomainSong> = emptyList(),
	val queueEntryIds: List<String> = emptyList(),
	val currentSong: DomainSong? = null,
	val currentCollection: DomainSongCollection? = null,
	val currentIndex: Int = -1,
	val isPaused: Boolean = false,
	val isShuffleEnabled: Boolean = false,
	val repeatMode: Int = 0,
	val progress: Float = 0f,
	val isLoading: Boolean = false,
	val playbackSpeed: Float = 1.0f,
	val playbackBitrate: Int? = null,
	val playbackSampleRate: Int? = null,
	val playbackMimeType: String? = null
)

fun PlayerUiState.entryIds(): List<String> {
	if (queueEntryIds.size == queue.size) return queueEntryIds
	val occurrences = mutableMapOf<String, Int>()
	return queue.map { song ->
		val occurrence = occurrences[song.id] ?: 0
		occurrences[song.id] = occurrence + 1
		"${song.id}#$occurrence"
	}
}
