package com.nickshortnct.ondo.wear.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nickshortnct.ondo.wear.MainActivity
import com.nickshortnct.ondo.wear.OndoWearApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The watch's own player: streams from the music server (or plays downloads)
 * through Bluetooth headphones or the watch speaker. With
 * `setSuppressPlaybackOnUnsuitableOutput`, Wear OS offers its output switcher
 * instead of playing silently when nothing suitable is connected.
 */
@OptIn(UnstableApi::class)
class WatchPlaybackService : MediaSessionService() {
	private var session: MediaSession? = null
	private val scope = MainScope()
	private var scrobbleJob: Job? = null

	private val app get() = application as OndoWearApp

	override fun onCreate() {
		super.onCreate()
		val http = DefaultHttpDataSource.Factory().setUserAgent("OndoWatch").setAllowCrossProtocolRedirects(true)
		val resolving = ResolvingDataSource.Factory(http) { spec ->
			if (spec.uri.scheme?.startsWith("http") == true) spec.withAdditionalHeaders(app.api.headers()) else spec
		}
		val player = ExoPlayer.Builder(this)
			.setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, resolving)))
			.setAudioAttributes(
				AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
				true
			)
			.setHandleAudioBecomingNoisy(true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.setSuppressPlaybackOnUnsuitableOutput(true)
			.build()

		val activity = PendingIntent.getActivity(
			this, 0,
			Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		session = MediaSession.Builder(this, player)
			.setSessionActivity(activity)
			.setCallback(object : MediaSession.Callback {
				// Rebuild URIs on the service side: downloads may have finished, or
				// the bitrate changed, since the item was created.
				override fun onAddMediaItems(
					mediaSession: MediaSession,
					controller: MediaSession.ControllerInfo,
					mediaItems: MutableList<MediaItem>
				): ListenableFuture<MutableList<MediaItem>> =
					Futures.immediateFuture(mediaItems.map { app.mediaItems.withPlayableUri(it) }.toMutableList())
			})
			.build()

		player.addListener(object : Player.Listener {
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				mediaItem?.mediaId?.let { id -> scope.launch { runCatching { app.api.scrobble(id, submission = false) } } }
				watchForScrobble(player)
			}
		})
	}

	/** Scrobbles the current track once half of it (or four minutes) has played. */
	private fun watchForScrobble(player: Player) {
		scrobbleJob?.cancel()
		val id = player.currentMediaItem?.mediaId ?: return
		scrobbleJob = scope.launch {
			while (isActive) {
				delay(10_000)
				val duration = player.duration
				if (player.currentMediaItem?.mediaId != id) return@launch
				if (duration > 0 && (player.currentPosition >= duration / 2 || player.currentPosition >= 240_000)) {
					runCatching { app.api.scrobble(id, submission = true) }
					return@launch
				}
			}
		}
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

	override fun onTaskRemoved(rootIntent: Intent?) {
		val player = session?.player
		if (player == null || !player.playWhenReady || player.mediaItemCount == 0) pauseAllPlayersAndStopSelf()
	}

	override fun onDestroy() {
		scope.cancel()
		session?.run {
			player.release()
			release()
		}
		session = null
		super.onDestroy()
	}
}
