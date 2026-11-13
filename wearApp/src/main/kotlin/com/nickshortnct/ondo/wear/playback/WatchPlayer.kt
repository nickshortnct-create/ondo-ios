package com.nickshortnct.ondo.wear.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nickshortnct.ondo.wear.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

data class WatchPlaybackState(
	val hasItem: Boolean = false,
	val song: Song? = null,
	val title: String = "",
	val artist: String = "",
	val artworkUri: String? = null,
	val playing: Boolean = false,
	val buffering: Boolean = false,
	val shuffle: Boolean = false,
	val repeatMode: Int = Player.REPEAT_MODE_OFF,
	val positionMs: Long = 0,
	val durationMs: Long = 0,
	val queueIndex: Int = 0,
	val queueSize: Int = 0,
	/** Wants to play, but Wear OS has no headphones / suitable output yet. */
	val waitingForOutput: Boolean = false,
)

/** App-side handle on [WatchPlaybackService] with an observable state for the UI. */
class WatchPlayer(
	private val context: Context,
	private val mediaItems: WatchMediaItems,
	private val scope: CoroutineScope,
) {
	private var controller: MediaController? = null
	private var connecting: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
	private var ticker: Job? = null
	private val starredOverrides = mutableMapOf<String, Boolean>()

	private val _state = MutableStateFlow(WatchPlaybackState())
	val state: StateFlow<WatchPlaybackState> get() = _state

	private val listener = object : Player.Listener {
		override fun onEvents(player: Player, events: Player.Events) = publish(player)
	}

	suspend fun connect(): MediaController? {
		controller?.takeIf { it.isConnected }?.let { return it }
		val future = connecting ?: MediaController.Builder(
			context, SessionToken(context, ComponentName(context, WatchPlaybackService::class.java))
		).buildAsync().also { connecting = it }
		val connected = suspendCancellableCoroutine { cont ->
			future.addListener({ cont.resume(runCatching { future.get() }.getOrNull()) }, ContextCompat.getMainExecutor(context))
		}
		connecting = null
		if (connected == null) return null
		controller = connected
		connected.addListener(listener)
		publish(connected)
		return connected
	}

	/** Connect only if the service already has something to show (keeps an idle watch from starting it). */
	fun attachIfRunning() {
		scope.launch { connect() }
	}

	private fun publish(player: Player) {
		val item = player.currentMediaItem
		val song = item?.mediaMetadata?.extras?.getString(WatchMediaItems.EXTRA_SONG_JSON)
			?.let { runCatching { Song.fromJson(JSONObject(it)) }.getOrNull() }
			?.let { song -> starredOverrides[song.id]?.let { song.copy(starred = it) } ?: song }
		_state.value = WatchPlaybackState(
			hasItem = item != null,
			song = song,
			title = item?.mediaMetadata?.title?.toString().orEmpty(),
			artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
			artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
			playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING &&
				player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE),
			buffering = player.playbackState == Player.STATE_BUFFERING,
			shuffle = player.shuffleModeEnabled,
			repeatMode = player.repeatMode,
			positionMs = player.currentPosition.coerceAtLeast(0),
			durationMs = player.duration.takeIf { it > 0 } ?: 0,
			queueIndex = player.currentMediaItemIndex,
			queueSize = player.mediaItemCount,
			waitingForOutput = player.playWhenReady &&
				player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT,
		)
		if (player.isPlaying && ticker?.isActive != true) {
			ticker = scope.launch {
				while (isActive && controller?.isPlaying == true) {
					controller?.let { c -> _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
					delay(1_000)
				}
			}
		}
	}

	fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
		if (songs.isEmpty()) return
		scope.launch {
			val c = connect() ?: return@launch
			val items: List<MediaItem> = songs.map(mediaItems::build)
			c.shuffleModeEnabled = shuffle
			c.setMediaItems(items, if (shuffle) (songs.indices).random() else startIndex.coerceIn(0, songs.lastIndex), 0L)
			c.prepare()
			c.play()
		}
	}

	private fun withController(block: (MediaController) -> Unit) {
		scope.launch { connect()?.let(block) }
	}

	fun toggle() = withController { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
	fun next() = withController { it.seekToNext() }
	fun previous() = withController { it.seekToPrevious() }
	fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
	fun cycleRepeat() = withController {
		it.repeatMode = when (it.repeatMode) {
			Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
			Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
			else -> Player.REPEAT_MODE_OFF
		}
	}
	fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

	/** Updates the favourite flag carried by the current item so the UI reflects it. */
	fun markStarred(songId: String, starred: Boolean) {
		starredOverrides[songId] = starred
		val s = _state.value
		if (s.song?.id == songId) _state.value = s.copy(song = s.song.copy(starred = starred))
	}
}
