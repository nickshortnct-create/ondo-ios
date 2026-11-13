package com.nickshortnct.ondo.androidApp.wear

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.nickshortnct.ondo.shared.MediaId
import com.nickshortnct.ondo.shared.PlaybackServiceExtension
import com.nickshortnct.ondo.shared.PlaybackServiceExtensions
import com.nickshortnct.ondo.util.Logger
import java.io.ByteArrayOutputStream

/**
 * Mirrors the phone's Now Playing state to a paired watch. Runs inside
 * [com.nickshortnct.ondo.shared.PlaybackService] for as long as it lives and
 * only writes on real changes (track, play/pause, seek, modes, favourite); the
 * watch extrapolates progress from position + timestamp.
 */
@OptIn(UnstableApi::class)
class WearStatePublisher(
	private val context: Context,
	private val session: MediaLibrarySession,
) : PlaybackServiceExtension {
	private val player = session.player
	private val dataClient = Wearable.getDataClient(context)
	private val handler = Handler(Looper.getMainLooper())
	private val audioManager = context.getSystemService(AudioManager::class.java)
	private var disabled = false
	private var lastArtKey: String? = null
	private var lastArt: Asset? = null
	private var lastStarred = PlaybackServiceExtensions.isCurrentStarred

	private val publishRunnable = Runnable { publish() }

	private val listener = object : Player.Listener {
		override fun onEvents(player: Player, events: Player.Events) {
			if (events.containsAny(
					Player.EVENT_MEDIA_ITEM_TRANSITION,
					Player.EVENT_MEDIA_METADATA_CHANGED,
					Player.EVENT_IS_PLAYING_CHANGED,
					Player.EVENT_PLAY_WHEN_READY_CHANGED,
					Player.EVENT_PLAYBACK_STATE_CHANGED,
					Player.EVENT_POSITION_DISCONTINUITY,
					Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
					Player.EVENT_REPEAT_MODE_CHANGED,
					Player.EVENT_TIMELINE_CHANGED,
				)
			) schedule()
		}
	}

	// The favourite state lives in the service, not the player; poll it cheaply.
	private val starWatcher = object : Runnable {
		override fun run() {
			val starred = PlaybackServiceExtensions.isCurrentStarred
			if (starred != lastStarred) {
				lastStarred = starred
				schedule()
			}
			handler.postDelayed(this, 1_000)
		}
	}

	init {
		active = this
		player.addListener(listener)
		handler.postDelayed(starWatcher, 1_000)
		schedule()
	}

	fun schedule() {
		if (disabled) return
		handler.removeCallbacks(publishRunnable)
		handler.postDelayed(publishRunnable, 250)
	}

	private fun publish() {
		if (disabled) return
		val item = player.currentMediaItem
		val metadata = player.mediaMetadata
		val songId = item?.mediaId?.let { MediaId.parseTrack(it)?.second ?: it }
		val artKey = metadata.artworkUri?.toString() ?: metadata.artworkData?.size?.toString()
		if (item != null && artKey != null && artKey != lastArtKey) {
			loadArtwork(metadata, artKey)
		}
		val request = PutDataMapRequest.create(WearContract.PATH_NOW_PLAYING).apply {
			dataMap.putBoolean(WearContract.KEY_HAS_ITEM, item != null)
			dataMap.putString(WearContract.KEY_SONG_ID, songId.orEmpty())
			dataMap.putString(WearContract.KEY_TITLE, metadata.title?.toString().orEmpty())
			dataMap.putString(WearContract.KEY_ARTIST, (metadata.artist ?: metadata.subtitle)?.toString().orEmpty())
			dataMap.putString(WearContract.KEY_ALBUM, metadata.albumTitle?.toString().orEmpty())
			dataMap.putBoolean(WearContract.KEY_PLAYING, player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING))
			dataMap.putBoolean(WearContract.KEY_BUFFERING, player.playbackState == Player.STATE_BUFFERING)
			dataMap.putBoolean(WearContract.KEY_SHUFFLE, player.shuffleModeEnabled)
			dataMap.putInt(WearContract.KEY_REPEAT, player.repeatMode)
			dataMap.putBoolean(WearContract.KEY_STARRED, PlaybackServiceExtensions.isCurrentStarred)
			dataMap.putBoolean(WearContract.KEY_CAN_FAVORITE, songId != null && !songId.startsWith("radio_"))
			dataMap.putLong(WearContract.KEY_POSITION, player.currentPosition.coerceAtLeast(0))
			dataMap.putLong(WearContract.KEY_DURATION, player.duration.takeIf { it > 0 } ?: 0L)
			dataMap.putLong(WearContract.KEY_POSITION_AT, System.currentTimeMillis())
			dataMap.putInt(WearContract.KEY_QUEUE_INDEX, player.currentMediaItemIndex)
			dataMap.putInt(WearContract.KEY_QUEUE_SIZE, player.mediaItemCount)
			dataMap.putInt(WearContract.KEY_VOLUME, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
			dataMap.putInt(WearContract.KEY_MAX_VOLUME, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
			if (artKey != null && artKey == lastArtKey) lastArt?.let { dataMap.putAsset(WearContract.KEY_ART, it) }
			dataMap.putString(WearContract.KEY_ART_ID, if (artKey == lastArtKey) artKey.orEmpty() else "")
		}.asPutDataRequest().setUrgent()
		dataClient.putDataItem(request).addOnFailureListener { error ->
			if (error is ApiException) {
				// No Wear OS / Play services Data Layer on this phone: stop trying.
				Logger.i("WearStatePublisher", "Wear Data Layer unavailable (${error.statusCode}); watch sync off")
				disabled = true
			} else {
				Logger.e("WearStatePublisher", "could not publish now playing", error)
			}
		}
	}

	private fun loadArtwork(metadata: MediaMetadata, key: String) {
		val future = session.bitmapLoader.loadBitmapFromMetadata(metadata) ?: return
		Futures.addCallback(future, object : FutureCallback<Bitmap> {
			override fun onSuccess(bitmap: Bitmap) {
				val scaled = Bitmap.createScaledBitmap(bitmap, ART_SIZE, (ART_SIZE * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(1), true)
				val bytes = ByteArrayOutputStream().use { out ->
					scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
					out.toByteArray()
				}
				handler.post {
					lastArtKey = key
					lastArt = Asset.createFromBytes(bytes)
					schedule()
				}
			}

			override fun onFailure(t: Throwable) {
				Logger.w("WearStatePublisher", "artwork for watch failed: ${t.message}")
			}
		}, MoreExecutors.directExecutor())
	}

	override fun release() {
		if (active === this) active = null
		handler.removeCallbacksAndMessages(null)
		player.removeListener(listener)
		if (!disabled) {
			// Tell the watch the phone stopped playing rather than leaving a frozen "playing" state.
			val request = PutDataMapRequest.create(WearContract.PATH_NOW_PLAYING).apply {
				dataMap.putBoolean(WearContract.KEY_HAS_ITEM, false)
				dataMap.putBoolean(WearContract.KEY_PLAYING, false)
				dataMap.putLong(WearContract.KEY_POSITION_AT, System.currentTimeMillis())
			}.asPutDataRequest().setUrgent()
			dataClient.putDataItem(request)
		}
	}

	companion object {
		private const val ART_SIZE = 256

		/** The publisher of the running playback service, if any. */
		@Volatile
		var active: WearStatePublisher? = null
			private set
	}
}
