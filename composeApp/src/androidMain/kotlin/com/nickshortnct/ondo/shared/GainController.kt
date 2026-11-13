package com.nickshortnct.ondo.shared

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainReplayGain
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.exoplayer.AudioGainProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** ReplayGain values travel with each media item as metadata extras so the service needs no database lookup. */
object GainExtras {
	private const val ALBUM_ID = "ondo.albumId"
	private const val ALBUM_GAIN = "ondo.rg.albumGain"
	private const val ALBUM_PEAK = "ondo.rg.albumPeak"
	private const val TRACK_GAIN = "ondo.rg.trackGain"
	private const val TRACK_PEAK = "ondo.rg.trackPeak"
	private const val BASE_GAIN = "ondo.rg.baseGain"
	private const val FALLBACK_GAIN = "ondo.rg.fallbackGain"

	fun write(song: DomainSong): Bundle = Bundle().apply {
		putString(ALBUM_ID, song.albumId)
		song.replayGain?.let { rg ->
			rg.albumGain?.let { putFloat(ALBUM_GAIN, it) }
			rg.albumPeak?.let { putFloat(ALBUM_PEAK, it) }
			rg.trackGain?.let { putFloat(TRACK_GAIN, it) }
			rg.trackPeak?.let { putFloat(TRACK_PEAK, it) }
			rg.baseGain?.let { putFloat(BASE_GAIN, it) }
			rg.fallbackGain?.let { putFloat(FALLBACK_GAIN, it) }
		}
	}

	fun albumId(extras: Bundle?): String? = extras?.getString(ALBUM_ID)

	fun read(extras: Bundle?): DomainReplayGain? {
		if (extras == null) return null
		fun value(key: String): Float? = if (extras.containsKey(key)) extras.getFloat(key) else null
		val rg = DomainReplayGain(
			albumGain = value(ALBUM_GAIN),
			albumPeak = value(ALBUM_PEAK),
			trackGain = value(TRACK_GAIN),
			trackPeak = value(TRACK_PEAK),
			baseGain = value(BASE_GAIN),
			fallbackGain = value(FALLBACK_GAIN)
		)
		return rg.takeIf {
			it.albumGain != null || it.trackGain != null || it.baseGain != null || it.fallbackGain != null
		}
	}
}

/**
 * Keeps the [AudioGainProcessor] in step with the settings and the playing item. It lives in the
 * playback service, so it also works for Android Auto sessions with no app UI.
 */
@OptIn(UnstableApi::class)
class GainController(
	private val player: Player,
	private val processor: AudioGainProcessor,
	private val preferences: PreferenceManager
) : Player.Listener {
	fun start(scope: CoroutineScope) {
		player.addListener(this)
		scope.launch {
			snapshotFlow { Triple(preferences.replayGainMode, preferences.rgAmpGain, preferences.ampGain) }
				.collect { (mode, rgAmp, amp) ->
					processor.update { it.copy(mode = mode, rgAmpDb = rgAmp, ampDb = amp) }
				}
		}
		refresh()
	}

	fun release() = player.removeListener(this)

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()

	override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()

	private fun refresh() {
		val replayGain = GainExtras.read(player.currentMediaItem?.mediaMetadata?.extras)
		processor.update { it.copy(replayGain = replayGain, albumContext = queueIsSingleAlbum()) }
	}

	private fun queueIsSingleAlbum(): Boolean {
		val count = minOf(player.mediaItemCount, MAX_QUEUE_SCAN)
		if (count == 0) return false
		val first = GainExtras.albumId(player.getMediaItemAt(0).mediaMetadata.extras) ?: return false
		return (1 until count).all { GainExtras.albumId(player.getMediaItemAt(it).mediaMetadata.extras) == first }
	}

	private companion object {
		const val MAX_QUEUE_SCAN = 2000
	}
}
