package com.nickshortnct.ondo.shared

import com.nickshortnct.ondo.domain.models.DomainSong

/**
 * Lets [PlaybackService] push a queue chosen from Android Auto into the app's
 * in-memory player state. The service sets ExoPlayer directly; the ViewModel
 * mirrors the same queue so Now Playing and widgets stay in sync.
 */
object CarPlaybackBridge {
	var onSelectionPlayed: ((List<DomainSong>, Int) -> Unit)? = null

	fun notifySelectionPlayed(songs: List<DomainSong>, startIndex: Int) {
		onSelectionPlayed?.invoke(songs, startIndex)
	}
}
