package com.nickshortnct.ondo.ui.screens.playlist.viewmodels

import kotlinx.coroutines.CancellationException

internal const val PLAYLIST_BATCH_SIZE = 100

internal suspend fun appendPlaylistBatches(ids: List<String>, append: suspend (List<String>) -> Unit) {
	var completed = 0
	for (batch in ids.chunked(PLAYLIST_BATCH_SIZE)) {
		try {
			append(batch)
			completed += batch.size
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			throw IllegalStateException("$completed songs were added before the request failed. Retry to add the remaining songs.", e)
		}
	}
}
