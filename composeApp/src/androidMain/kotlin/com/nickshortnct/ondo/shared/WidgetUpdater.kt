package com.nickshortnct.ondo.shared

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tells the home-screen widgets what is playing. It runs inside [PlaybackService], so widgets stay
 * correct after auto-advance and while Android Auto drives playback with no app UI.
 */
class WidgetUpdater(
	private val context: Context,
	private val player: Player,
	private val scope: CoroutineScope
) : Player.Listener {
	private var pending: Job? = null

	fun start() = player.addListener(this)

	fun release() {
		player.removeListener(this)
		pending?.cancel()
	}

	override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = schedule()

	override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()

	private fun schedule() {
		pending?.cancel()
		pending = scope.launch {
			delay(DEBOUNCE_MS)
			send()
		}
	}

	private fun send() {
		val metadata = player.mediaMetadata
		context.sendBroadcast(
			Intent("${context.packageName}.NOW_PLAYING_UPDATED").apply {
				setPackage(context.packageName)
				putExtra("isPlaying", player.isPlaying)
				putExtra("title", metadata.title?.toString() ?: "Unknown song")
				putExtra("artist", (metadata.artist ?: metadata.albumArtist)?.toString() ?: "Unknown artist")
				putExtra("artUrl", metadata.artworkUri?.toString())
			}
		)
	}

	private companion object {
		const val DEBOUNCE_MS = 500L
	}
}
