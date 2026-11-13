package com.nickshortnct.ondo.androidApp.wear

import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nickshortnct.ondo.shared.PlaybackService
import com.nickshortnct.ondo.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Receives remote-control messages from the watch. Commands go through a
 * normal Media3 controller, so they behave exactly like the notification or
 * Android Auto, and starting playback works even when the phone app is closed
 * (binding the controller starts [PlaybackService]).
 */
@OptIn(UnstableApi::class)
class WearCommandService : WearableListenerService() {
	// Runs on the listener's worker thread. Block it until the command is done:
	// the Data Layer unbinds (and may destroy) this service as soon as the
	// callback returns, which would cancel work launched in the background.
	override fun onMessageReceived(event: MessageEvent) {
		runBlocking(Dispatchers.Main.immediate) {
			withTimeoutOrNull(10_000) {
				when (event.path) {
					WearContract.PATH_REQUEST_ACCOUNT -> WearAccountPublisher.publish(applicationContext, force = true)
					WearContract.PATH_COMMAND -> {
						val parts = String(event.data, Charsets.UTF_8).split('|')
						runCatching { execute(parts.first(), parts.drop(1)) }
							.onFailure { Logger.e("WearCommandService", "watch command ${parts.first()} failed", it) }
					}
				}
			}
		}
	}

	private suspend fun execute(name: String, args: List<String>) {
		Logger.i("WearCommandService", "watch command $name")
		val audio = getSystemService(AudioManager::class.java)
		when (name) {
			WearContract.CMD_VOLUME_UP, WearContract.CMD_VOLUME_DOWN -> {
				audio.adjustStreamVolume(
					AudioManager.STREAM_MUSIC,
					if (name == WearContract.CMD_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
					0
				)
				WearStatePublisher.active?.schedule()
				return
			}
		}
		val controller = connect() ?: return
		try {
			when (name) {
				WearContract.CMD_TOGGLE -> if (controller.isPlaying || (controller.playWhenReady && controller.playbackState == Player.STATE_BUFFERING)) controller.pause() else play(controller)
				WearContract.CMD_PLAY -> play(controller)
				WearContract.CMD_PAUSE -> controller.pause()
				WearContract.CMD_NEXT -> controller.seekToNext()
				WearContract.CMD_PREVIOUS -> controller.seekToPrevious()
				WearContract.CMD_SHUFFLE -> controller.shuffleModeEnabled = !controller.shuffleModeEnabled
				WearContract.CMD_REPEAT -> controller.repeatMode = when (controller.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
				WearContract.CMD_FAVORITE -> controller.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_FAVORITE, Bundle.EMPTY), Bundle.EMPTY)
				WearContract.CMD_SEEK -> args.firstOrNull()?.toLongOrNull()?.let { controller.seekTo(it.coerceAtLeast(0)) }
				WearContract.CMD_PLAY_MEDIA -> args.firstOrNull()?.takeIf { it.isNotBlank() }?.let { mediaId ->
					// Same path as an Android Auto tap: the session resolves album /
					// playlist / artist / shuffle ids into the whole queue.
					controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
					controller.prepare()
					controller.play()
				}
				WearContract.CMD_PLAY_SONGS -> {
					val ids = args.getOrNull(0).orEmpty().split(',').filter { it.isNotBlank() }
					if (ids.isEmpty()) return
					val start = args.getOrNull(1)?.toIntOrNull()?.coerceIn(0, ids.lastIndex) ?: 0
					val items = ids.map { MediaItem.Builder().setMediaId(it).build() }
					if (items.size == 1) controller.setMediaItem(items.single()) else controller.setMediaItems(items, start, 0L)
					controller.prepare()
					controller.play()
				}
				WearContract.CMD_REFRESH -> WearStatePublisher.active?.schedule()
			}
			// Let the session process the command before the controller is released.
			delay(300)
		} finally {
			controller.release()
		}
	}

	private fun play(controller: MediaController) {
		if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
		if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
		controller.play()
	}

	private suspend fun connect(): MediaController? {
		val future = MediaController.Builder(applicationContext, PlaybackService.newSessionToken(applicationContext)).buildAsync()
		return withTimeoutOrNull(5_000) {
			suspendCancellableCoroutine { cont ->
				future.addListener({ cont.resume(runCatching { future.get() }.getOrNull()) }, ContextCompat.getMainExecutor(applicationContext))
				cont.invokeOnCancellation { MediaController.releaseFuture(future) }
			}
		}
	}

}
