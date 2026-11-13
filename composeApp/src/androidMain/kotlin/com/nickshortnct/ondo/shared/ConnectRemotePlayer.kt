package com.nickshortnct.ondo.shared

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.ondo.ConnectRouting
import com.nickshortnct.ondo.ui.core.PlayerUiState
import kotlin.math.abs

/**
 * The other Ondo Connect device as a Media3 player, for its own media session: the
 * notification, quick-settings player and lock screen show that device's song, cover,
 * progress and play state, and their buttons control it. This phone's own player (and
 * its session, which the app itself reads) is left untouched.
 */
@OptIn(UnstableApi::class)
class ConnectRemotePlayer(private val coverUri: (String?) -> Uri?) : SimpleBasePlayer(Looper.getMainLooper()) {
	private var remote: PlayerUiState? = null

	/** True when the shown state changed enough to redraw (song, play state, or a position jump). */
	fun update(state: PlayerUiState?): Boolean {
		val before = remote
		remote = state
		val changed = before?.currentSong?.id != state?.currentSong?.id ||
			before?.isPaused != state?.isPaused ||
			before?.isLoading != state?.isLoading ||
			before?.currentIndex != state?.currentIndex ||
			before?.queue?.size != state?.queue?.size ||
			positionMs(before) == null || positionMs(state) == null ||
			abs(positionMs(state)!! - expectedPositionMs(before)) > 2_000
		if (changed) {
			lastUpdateAt = android.os.SystemClock.elapsedRealtime()
			invalidateState()
		}
		return changed
	}

	private var lastUpdateAt = 0L

	private fun positionMs(state: PlayerUiState?): Long? {
		val song = state?.currentSong ?: return null
		return (state.progress * song.duration.inWholeMilliseconds).toLong()
	}

	/** Where the last shown state has moved to by now, for telling real jumps from ticking. */
	private fun expectedPositionMs(state: PlayerUiState?): Long {
		val base = positionMs(state) ?: return 0L
		if (state?.isPaused != false) return base
		return base + (android.os.SystemClock.elapsedRealtime() - lastUpdateAt)
	}

	private fun itemData(song: DomainSong, uid: String): MediaItemData {
		val coverId = song.albumId?.takeIf { it.isNotBlank() }?.let { "al-${it}_0" } ?: song.coverArtId
		val metadata = MediaMetadata.Builder()
			.setTitle(song.title)
			.setDisplayTitle(song.title)
			.setArtist(song.artistName)
			.setAlbumTitle(song.albumTitle)
			.setArtworkUri(coverUri(coverId))
			.build()
		val durationMs = song.duration.inWholeMilliseconds
		return MediaItemData.Builder(uid)
			.setMediaItem(MediaItem.Builder().setMediaId(song.id).setMediaMetadata(metadata).build())
			.setMediaMetadata(metadata)
			.setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET)
			.setIsSeekable(durationMs > 0)
			.build()
	}

	override fun getState(): State {
		val commands = Player.Commands.Builder().addAll(
			COMMAND_PLAY_PAUSE, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
			COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
			COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_METADATA, COMMAND_GET_TIMELINE,
		).build()
		val state = remote
		val song = state?.currentSong
			?: return State.Builder().setAvailableCommands(commands).setPlaybackState(STATE_IDLE).build()
		// The previous and next songs too, so the notification's skip buttons are enabled.
		val previous = state.queue.getOrNull(state.currentIndex - 1)
		val next = state.queue.getOrNull(state.currentIndex + 1)
		val playlist = buildList {
			previous?.let { add(itemData(it, "connect-prev:${it.id}")) }
			add(itemData(song, "connect:${song.id}"))
			next?.let { add(itemData(it, "connect-next:${it.id}")) }
		}
		val positionMs = positionMs(state) ?: 0L
		return State.Builder()
			.setAvailableCommands(commands)
			.setPlaylist(playlist)
			.setCurrentMediaItemIndex(if (previous != null) 1 else 0)
			.setPlayWhenReady(!state.isPaused, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
			.setPlaybackState(if (state.isLoading) STATE_BUFFERING else STATE_READY)
			.setContentPositionMs(
				if (state.isPaused) PositionSupplier.getConstant(positionMs)
				else PositionSupplier.getExtrapolating(positionMs, 1f)
			)
			.build()
	}

	override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
		ConnectRouting.route(if (playWhenReady) "play" else "pause")
		return Futures.immediateVoidFuture()
	}

	override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
		val state = remote
		val currentIndex = if (state?.queue?.getOrNull(state.currentIndex - 1) != null) 1 else 0
		when {
			seekCommand == COMMAND_SEEK_TO_NEXT || seekCommand == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
				mediaItemIndex > currentIndex -> ConnectRouting.route("skipNext")
			seekCommand == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM || mediaItemIndex < currentIndex ->
				ConnectRouting.route("skipPrevious")
			seekCommand == COMMAND_SEEK_TO_PREVIOUS -> ConnectRouting.route("skipPrevious")
			else -> {
				val durationMs = state?.currentSong?.duration?.inWholeMilliseconds ?: 0L
				if (durationMs > 0) ConnectRouting.route("seek", value = positionMs.toFloat() / durationMs)
			}
		}
		return Futures.immediateVoidFuture()
	}
}
