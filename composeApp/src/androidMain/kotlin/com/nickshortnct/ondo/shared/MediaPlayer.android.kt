package com.nickshortnct.ondo.shared

import com.nickshortnct.ondo.ondo.ConnectRouting

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.compose.runtime.snapshotFlow
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.wav.WavExtractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import coil3.imageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.di.ResourceProvider
import com.nickshortnct.ondo.domain.manager.AppVisibility
import com.nickshortnct.ondo.domain.manager.AndroidScrobbleManager
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.EqualiserManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.manager.SyncManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainRadio
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.settings.EqualiserMode
import com.nickshortnct.ondo.domain.repositories.PlayerStateRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.domain.repositories.SongStarEvents
import com.nickshortnct.ondo.exoplayer.AudioGainProcessor
import com.nickshortnct.ondo.ui.core.entryIds
import com.nickshortnct.ondo.ui.core.PlayerUiState
import com.nickshortnct.ondo.util.Logger
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import coil3.PlatformContext as CoilPlatformContext

@OptIn(UnstableApi::class)
/**
 * What the notification, quick-settings player, lock screen, headset buttons and Bluetooth
 * controls drive. While another Ondo Connect device is playing they control that device
 * (like Spotify Connect) instead of starting this phone's own player, which used to pull
 * playback back to the phone. Play and pause both toggle the other device, because the
 * notification still shows this phone's own (paused) player state.
 */
private class ConnectAwarePlayer(player: Player, private val isExternalRequest: () -> Boolean) : ForwardingPlayer(player) {
	// Only requests from outside the app: Ondo's own controller (its UI, and Connect pausing
	// this phone to follow another device) already goes through Connect and must reach the
	// real player.
	private fun remote() = if (isExternalRequest()) ConnectRouting.remoteState.value else null
	private fun routed(action: String, value: Float = 0f) = remote() != null && ConnectRouting.route(action, value = value)
	private fun toggleRemote(): Boolean {
		val state = remote() ?: return false
		return ConnectRouting.route(if (state.isPaused) "play" else "pause")
	}
	override fun play() { if (!toggleRemote()) super.play() }
	override fun pause() { if (!toggleRemote()) super.pause() }
	override fun setPlayWhenReady(playWhenReady: Boolean) { if (!toggleRemote()) super.setPlayWhenReady(playWhenReady) }
	override fun seekToNext() { if (!routed("skipNext")) super.seekToNext() }
	override fun seekToNextMediaItem() { if (!routed("skipNext")) super.seekToNextMediaItem() }
	override fun seekToPrevious() { if (!routed("skipPrevious")) super.seekToPrevious() }
	override fun seekToPreviousMediaItem() { if (!routed("skipPrevious")) super.seekToPreviousMediaItem() }
	override fun seekTo(positionMs: Long) {
		val durationMs = remote()?.currentSong?.duration?.inWholeMilliseconds ?: 0L
		if (durationMs > 0 && routed("seek", positionMs.toFloat() / durationMs)) return
		super.seekTo(positionMs)
	}
}

class PlaybackService : MediaLibraryService(), KoinComponent {
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	private var mediaLibrarySession: MediaLibrarySession? = null
	/** While another Ondo Connect device plays: its own session (notification, quick settings). */
	private var connectSession: MediaSession? = null
	private var connectPlayer: ConnectRemotePlayer? = null
	private val libraryTree: MediaLibraryTree by inject()
	private val playlistPlayStats: com.nickshortnct.ondo.ondo.PlaylistPlayStats by inject()
	private val playerStateRepository: PlayerStateRepository by inject()
	private val audioGainProcessor: AudioGainProcessor by inject()
	private val serviceScope = MainScope()
	private var scrobbleManager: AndroidScrobbleManager? = null
	private var gainController: GainController? = null
	private var widgetUpdater: WidgetUpdater? = null
	private val resourceProvider: ResourceProvider by inject()

	private val connectivityManager: ConnectivityManager by inject()

	private val syncManager: SyncManager by inject()
	private val sessionManager: SessionManager by inject()
	private val preferenceManager: PreferenceManager by inject()
	private val equaliserManager: EqualiserManager by inject()
	private val songRepository: SongRepository by inject()

	/** Whether the current track is favourited; drives the heart in the notification, Auto and watch. */
	private var currentStarred = false
	private var starLookupJob: Job? = null
	private val serviceExtensions = mutableListOf<PlaybackServiceExtension>()

	private var equaliser: Equalizer? = null
	private var audioEffectSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var equaliserMode: EqualiserMode = EqualiserMode.Disabled

	override fun onCreate() {
		super.onCreate()
		Logger.i("PlaybackService", "onCreate starting")
		// Fetch audio in a few large bursts rather than a trickle. Each fetch keeps the cellular radio
		// powered for several seconds afterwards; refilling a 32-64 s buffer meant a fetch about every
		// 30 s, so on 5G the radio never got to sleep during playback. Now it tops up to 10 minutes
		// (or 40 MB, about 4.5 minutes of FLAC) and waits until under 2 minutes remain.
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				/* minBufferMs = */ 120_000,
				/* maxBufferMs = */ 600_000,
				/* bufferForPlaybackMs = */ 2_500,
				/* bufferForPlaybackAfterRebufferMs = */ 5_000
			)
			.setTargetBufferBytes(40 * 1024 * 1024)
			.setBackBuffer(10_000, true)
			.build()

		val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build().apply {
				setSmallIcon(resourceProvider.icNavic)
			}

		val httpDataSourceFactory = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { spec ->
			spec.withAdditionalHeaders(preferenceManager.customHeadersMap())
		}
		val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

		val extractorsFactory = ExtractorsFactory {
			arrayOf(
				FlacExtractor(),
				WavExtractor(),
				FragmentedMp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				Mp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				OggExtractor(),
				MatroskaExtractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
				Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
			)
		}

		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

		val audioRenderer = RenderersFactory { handler, _, audioListener, _, _ ->
			arrayOf<BaseRenderer>(
				MediaCodecAudioRenderer(
					applicationContext,
					MediaCodecSelector.DEFAULT,
					handler,
					audioListener,
					DefaultAudioSink.Builder(applicationContext)
						.setAudioProcessors(arrayOf(audioGainProcessor))
						.build()
				)
			)
		}

		val player = ExoPlayer.Builder(this, audioRenderer)
			.setLoadControl(loadControl)
			.setMediaSourceFactory(mediaSourceFactory)
			.setHandleAudioBecomingNoisy(true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
						.build(),
					true
				)
				setMediaNotificationProvider(notificationProvider)
			}

		serviceScope.launch {
			snapshotFlow { preferenceManager.gaplessPlayback to preferenceManager.audioOffload }
				.collect { (gapless, offload) ->
					player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
						.setAudioOffloadPreferences(TrackSelectionParameters.AudioOffloadPreferences.Builder()
							.setIsGaplessSupportRequired(gapless)
							.setAudioOffloadMode(if (offload) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
								else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
							.build()).build()
				}
		}

		gainController = GainController(player, audioGainProcessor, preferenceManager).also { it.start(serviceScope) }
		widgetUpdater = WidgetUpdater(applicationContext, player, serviceScope).also { it.start() }

		scrobbleManager =
			AndroidScrobbleManager(
				player,
				serviceScope,
				connectivityManager,
				syncManager,
				sessionManager,
				preferenceManager
			)

		val sessionIntent = applicationContext.packageManager
			.getLaunchIntentForPackage(applicationContext.packageName)
			?.apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
					Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

		val sessionPendingIntent = PendingIntent.getActivity(
			this,
			0,
			sessionIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		mediaLibrarySession = try {
			MediaLibrarySession.Builder(
				this,
				ConnectAwarePlayer(player) {
					val session = mediaLibrarySession
					val caller = session?.controllerForCurrentRequest
					caller != null && (session.isMediaNotificationController(caller) || caller.packageName != packageName)
				},
				MediaLibrarySessionCallback(
					player, libraryTree, playerStateRepository, scope,
					onToggleFavorite = ::toggleFavorite,
					onRemoteControllerConnected = { preloadLastQueueForHeadlessStart(player) },
					onPlaylistPlayed = { playlistPlayStats.record(it) },
					isPartnerApp = { PartnerApps.isTrusted(applicationContext, it.packageName) },
				)
			)
				.setBitmapLoader(CoilBitmapLoader(applicationContext) { preferenceManager.customHeadersMap() })
				.setSessionActivity(sessionPendingIntent)
				.setMediaButtonPreferences(makeButtons(player, currentStarred))
				.build()
				.also { Logger.i("PlaybackService", "MediaLibrarySession built") }
		} catch (ex: Exception) {
			// onGetSession() must return non-null or Media3 refuses the
			// connection outright -- this is the single most consequential
			// place a silent failure here could turn into "<app> doesn't seem
			// to be working right now" for Android Auto.
			Logger.e("PlaybackService", "failed to build MediaLibrarySession", ex)
			throw ex
		}

		// The other device's session: shown instead of this phone's (stale, paused) one
		// whenever another Connect device is the player.
		val remotePlayer = ConnectRemotePlayer { id -> id?.let { sessionManager.getCoverArtUrl(it).toUri() } }
		connectPlayer = remotePlayer
		serviceScope.launch {
			ConnectRouting.remoteState.collect { state ->
				if (state?.currentSong != null) {
					if (connectSession == null) {
						connectSession = runCatching {
							MediaSession.Builder(this@PlaybackService, remotePlayer)
								.setId("ondo-connect")
								.setSessionActivity(sessionPendingIntent)
								.setBitmapLoader(CoilBitmapLoader(applicationContext) { preferenceManager.customHeadersMap() })
								.build()
								.also { addSession(it) }
						}.onFailure { Logger.e("PlaybackService", "failed to build Connect session", it) }.getOrNull()
					}
					remotePlayer.update(state)
				} else if (connectSession != null) {
					remotePlayer.update(null)
					connectSession?.let { removeSession(it); it.release() }
					connectSession = null
					// Bring this phone's own notification back.
					mediaLibrarySession?.let { onUpdateNotification(it, false) }
				}
			}
		}

		currentAudioSessionId = player.audioSessionId
		equaliserMode = equaliserManager.config.value.mode
		applyEqualiserMode(equaliserMode, currentAudioSessionId)

		player.addListener(object : Player.Listener {
			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
				refreshButtons()
			}

			override fun onRepeatModeChanged(repeatMode: Int) {
				refreshButtons()
			}

			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				refreshStarred()
			}

			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				currentAudioSessionId = audioSessionId
				applyEqualiserMode(equaliserMode, audioSessionId)
			}
		})

		serviceScope.launch {
			SongStarEvents.events.collect { (songId, starred) ->
				if (songId == currentSongId()) {
					currentStarred = starred
					refreshButtons()
				}
			}
		}

		PlaybackServiceExtensions.toggleFavorite = ::toggleFavorite

		mediaLibrarySession?.let { session ->
			PlaybackServiceExtensions.factories.forEach { factory ->
				runCatching { factory(this, session) }
					.onSuccess { serviceExtensions += it }
					.onFailure { Logger.e("PlaybackService", "service extension failed to start", it) }
			}
		}

		scope.launch(Dispatchers.Main) {
			equaliserManager.config.collect { config ->
				if (config.mode != equaliserMode) {
					equaliserMode = config.mode
					applyEqualiserMode(equaliserMode, currentAudioSessionId)
				} else {
					updateEqualiser()
				}
			}
		}
	}

	override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
		// While another device plays, only its session shows: this phone's own notification
		// would show the last song it played here.
		if (connectSession != null && session !== connectSession) return
		super.onUpdateNotification(session, startInForegroundRequired)
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
		Logger.i(
			"PlaybackService",
			"onGetSession from ${controllerInfo.packageName}, session=${if (mediaLibrarySession != null) "ready" else "NULL"}"
		)
		return mediaLibrarySession
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		val session = mediaLibrarySession
		val player = session?.player
		// Swiping the app away used to stop the service whenever playback was
		// paused -- even while Android Auto, a watch or another remote was
		// attached. With no foreground service left, Android then kills the
		// process on task removal, and Auto loses Ondo mid-drive: the app
		// vanishes from the launcher or Now Playing goes blank until the phone
		// app is reopened. Keep running while anything outside the app is
		// connected; the service stops normally once those controllers leave.
		val externalControllers = session?.connectedControllers.orEmpty().filter { controller ->
			controller.packageName != packageName && !session!!.isMediaNotificationController(controller)
		}
		Logger.i(
			"PlaybackService",
			"onTaskRemoved (external controllers: ${externalControllers.joinToString { it.packageName }.ifEmpty { "none" }})"
		)
		if (player == null || ((!player.playWhenReady || player.mediaItemCount == 0) && externalControllers.isEmpty())) {
			PlaybackControllerRegistry.releaseController?.invoke()
			pauseAllPlayersAndStopSelf()
		}
	}

	override fun onDestroy() {
		Logger.i("PlaybackService", "onDestroy")
		serviceExtensions.forEach { runCatching { it.release() } }
		serviceExtensions.clear()
		PlaybackServiceExtensions.toggleFavorite = null
		connectSession?.let { runCatching { removeSession(it) }; it.release() }
		connectSession = null
		closeAudioEffectSession(audioEffectSessionId)
		releaseEqualiser()
		scrobbleManager?.release()
		gainController?.release()
		widgetUpdater?.release()
		serviceScope.cancel()
		scope.cancel()
		stopForeground(STOP_FOREGROUND_REMOVE)
		mediaLibrarySession?.run {
			player.stop()
			player.release()
			release()
		}
		mediaLibrarySession = null
		super.onDestroy()
	}

	private fun currentSongId(): String? {
		val mediaId = mediaLibrarySession?.player?.currentMediaItem?.mediaId ?: return null
		return (MediaId.parseTrack(mediaId)?.second ?: mediaId).takeUnless { it.startsWith("radio_") || it.isBlank() }
	}

	private fun refreshButtons() {
		val session = mediaLibrarySession ?: return
		PlaybackServiceExtensions.isCurrentStarred = currentStarred
		session.setMediaButtonPreferences(makeButtons(session.player, currentStarred, currentSongId() != null))
	}

	private fun refreshStarred() {
		val songId = currentSongId()
		starLookupJob?.cancel()
		if (songId == null) {
			currentStarred = false
			refreshButtons()
			return
		}
		starLookupJob = serviceScope.launch {
			val starred = withContext(Dispatchers.IO) {
				runCatching { songRepository.isSongStarred(songId) }.getOrDefault(false)
			}
			if (songId == currentSongId()) {
				currentStarred = starred
				refreshButtons()
			}
		}
	}

	/** Favourite / unfavourite the current track; shared by the notification, Android Auto and the watch. */
	internal fun toggleFavorite() {
		val songId = currentSongId() ?: return
		val starred = !currentStarred
		currentStarred = starred
		refreshButtons()
		serviceScope.launch(Dispatchers.IO) {
			runCatching { songRepository.setSongStarred(songId, starred) }
				.onFailure { Logger.e("PlaybackService", "could not change favourite for $songId", it) }
		}
	}

	/**
	 * When Android Auto (or a watch / Assistant) connects while the service
	 * has no app open, the player is empty and Auto shows a blank
	 * Now Playing screen until something is picked again. Load the last saved
	 * queue paused, exactly where it left off, so the car shows what was
	 * playing and one tap on play resumes it. Skipped whenever the app itself
	 * is connected: its own restore owns the queue in that case.
	 */
	private var preloadJob: Job? = null

	private fun preloadLastQueueForHeadlessStart(player: ExoPlayer) {
		if (preloadJob?.isActive == true || player.mediaItemCount > 0) return
		preloadJob = serviceScope.launch {
			// Give the app's own controller a moment to connect if the phone
			// app is what started us; its restore takes precedence.
			delay(500)
			val session = mediaLibrarySession ?: return@launch
			if (player.mediaItemCount > 0 || appControllerConnected(session)) return@launch
			val saved = withTimeoutOrNull(3_000) { playerStateRepository.readState() } ?: return@launch
			if (saved.queue.isEmpty()) return@launch
			val index = saved.currentIndex.coerceIn(0, saved.queue.lastIndex)
			val windowStart = (index - MAX_PRELOADED_QUEUE / 2).coerceAtLeast(0)
			val window = saved.queue.subList(windowStart, (windowStart + MAX_PRELOADED_QUEUE).coerceAtMost(saved.queue.size))
			val items = withContext(Dispatchers.Default) { window.map { libraryTree.toPlayableMediaItem(it) } }
			// Re-check on the main thread: the app or a controller may have
			// started playback while the saved state was being read.
			if (player.mediaItemCount > 0 || appControllerConnected(session)) return@launch
			val startIndex = index - windowStart
			val positionMs = (saved.progress.coerceIn(0f, 1f) * saved.queue[index].duration.inWholeMilliseconds).toLong()
			player.setMediaItems(items, startIndex, positionMs)
			player.shuffleModeEnabled = saved.isShuffleEnabled
			player.repeatMode = saved.repeatMode
			player.playWhenReady = false
			player.prepare()
			CarPlaybackBridge.notifySelectionPlayed(window, startIndex)
			Logger.i("PlaybackService", "preloaded ${items.size} saved item(s) for a headless start")
		}
	}

	private fun appControllerConnected(session: MediaSession) = session.connectedControllers.any {
		it.packageName == packageName && !session.isMediaNotificationController(it)
	}

	// Applies the chosen equaliser mode
	private fun applyEqualiserMode(mode: EqualiserMode, sessionId: Int) {
		closeAudioEffectSession(audioEffectSessionId)
		releaseEqualiser()

		when (mode) {
			EqualiserMode.BuiltIn -> makeEqualiser(sessionId)
			EqualiserMode.External -> openAudioEffectSession(sessionId)
			EqualiserMode.Disabled -> Unit
		}
	}

	private fun releaseEqualiser() {
		equaliser?.release()
		equaliser = null
	}

	// Announces our audio session to the system so external equalizer apps can attach effects to it
	private fun openAudioEffectSession(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
		audioEffectSessionId = sessionId
		sendBroadcast(
			Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
				putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			}
		)
	}

	// Tells external equalizer apps our audio session is going away so they can release their effects
	private fun closeAudioEffectSession(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
		sendBroadcast(
			Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
			}
		)
		audioEffectSessionId = C.AUDIO_SESSION_ID_UNSET
	}

	private fun makeEqualiser(sessionId: Int) {
		releaseEqualiser()
		try {
			val equaliser = Equalizer(0, sessionId).apply {
				enabled = true
			}

			this.equaliser = equaliser

			val bandLowerRange = equaliser.bandLevelRange.firstOrNull()?.toFloat() ?: -1500f
			val bandUpperRange = equaliser.bandLevelRange.lastOrNull()?.toFloat() ?: 1500f
			val bandCount = equaliser.numberOfBands.toInt()

			scope.launch {
				equaliserManager.setConfig(
					equaliserManager.config.value.copy(
						bandLowerRange = bandLowerRange,
						bandUpperRange = bandUpperRange,
						bandCount = bandCount
					)
				)
			}

			updateEqualiser()
		} catch (ex: Exception) {
			Logger.e("PlaybackService", "error while configuring eq", ex)
		}
	}

	private fun updateEqualiser() {
		val equaliser = equaliser ?: return
		val config = equaliserManager.config.value
		try {
			// reset all band levels first in case an item in
			// config.bandLevels was removed (e.g. user presses
			// reset in the equaliser settings)
			repeat(equaliser.numberOfBands.toInt()) { band ->
				equaliser.setBandLevel(band.toShort(), 0)
			}
			config.bandLevels.forEach { (band, level) ->
				equaliser.setBandLevel(band.toShort(), level.toInt().toShort())
			}
		} catch (ex: Exception) {
			Logger.e("PlaybackService", "error while setting eq band levels", ex)
		}
	}

	companion object {
		const val COMMAND_SHUFFLE = "COMMAND_SHUFFLE"
		const val COMMAND_REPEAT = "COMMAND_REPEAT"
		const val COMMAND_PLAY_NEXT = "COMMAND_PLAY_NEXT"
		const val COMMAND_SLEEP_BOUNDARY = "COMMAND_SLEEP_BOUNDARY"
		const val COMMAND_FAVORITE = "COMMAND_FAVORITE"
		private const val MAX_PRELOADED_QUEUE = 500

		fun makeShuffleButton(enabled: Boolean): CommandButton {
			val icon = if (enabled) {
				CommandButton.ICON_SHUFFLE_ON
			} else {
				CommandButton.ICON_SHUFFLE_OFF
			}
			return CommandButton.Builder(icon)
				.setDisplayName(if (enabled) "Shuffle on" else "Shuffle off")
				.setSessionCommand(SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY))
				.setSlots(CommandButton.SLOT_OVERFLOW)
				.build()
		}

		fun makeRepeatButton(mode: Int): CommandButton {
			val icon = when (mode) {
				Player.REPEAT_MODE_OFF -> CommandButton.ICON_REPEAT_OFF
				Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
				else -> CommandButton.ICON_REPEAT_ONE
			}
			return CommandButton.Builder(icon)
				.setDisplayName(when (mode) {
					Player.REPEAT_MODE_OFF -> "Repeat off"
					Player.REPEAT_MODE_ALL -> "Repeat all"
					else -> "Repeat one"
				})
				.setSessionCommand(SessionCommand(COMMAND_REPEAT, Bundle.EMPTY))
				.setSlots(CommandButton.SLOT_OVERFLOW)
				.build()
		}

		fun makeFavoriteButton(starred: Boolean): CommandButton =
			CommandButton.Builder(if (starred) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
				.setDisplayName(if (starred) "Remove from favourites" else "Add to favourites")
				.setSessionCommand(SessionCommand(COMMAND_FAVORITE, Bundle.EMPTY))
				.setSlots(CommandButton.SLOT_OVERFLOW)
				.build()

		/**
		 * Order matters: the quick-settings player shows the first two custom
		 * buttons next to previous/next, so shuffle and repeat come first;
		 * Android Auto, the expanded notification and the watch show the
		 * favourite button as well.
		 */
		fun makeButtons(player: Player, starred: Boolean = false, canFavorite: Boolean = true) = buildList {
			add(makeShuffleButton(player.shuffleModeEnabled))
			add(makeRepeatButton(player.repeatMode))
			if (canFavorite && player.mediaItemCount > 0) add(makeFavoriteButton(starred))
		}

		fun newSessionToken(context: Context): SessionToken {
			return SessionToken(context, ComponentName(context, PlaybackService::class.java))
		}
	}
}

@UnstableApi
class AndroidMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
	private val application: Application,
	private val albumDao: AlbumDao,
	private val mediaItemFactory: OndoMediaItemFactory,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager
) {
	private var controller: MediaController? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null
	private var progressJob: Job? = null

	// Queue edits arrive as separate coroutines (clearQueue, addToQueue, playAt, ...) and some
	// of them suspend (building items, waiting for the controller). Serialize them so they
	// still reach the player in the order they were issued; Mutex waiters are FIFO.
	private val queueMutex = Mutex()

	override suspend fun awaitQueueIdle() { queueMutex.withLock {} }

	private fun queueCommand(block: suspend () -> Unit) {
		viewModelScope.launch { queueMutex.withLock { block() } }
	}

	init {
		CarPlaybackBridge.onSelectionPlayed = { songs, startIndex ->
			viewModelScope.launch {
				_uiState.update { state ->
					// isPaused / isLoading come from the real player callbacks.
					state.copy(
						queue = songs,
						queueEntryIds = controllerEntryIds().takeIf { it.size == songs.size }.orEmpty(),
						currentIndex = startIndex,
						currentSong = songs.getOrNull(startIndex)
					)
				}
			}
		}
		connectToService()
		start()
	}

	private fun connectToService() {
		if (controllerFuture != null) return
		PlaybackControllerRegistry.releaseController = {
			releaseController(restoreQueueOnReconnect = true)
		}
		val future = MediaController.Builder(application, PlaybackService.newSessionToken(application))
			.setListener(object : MediaController.Listener {
				override fun onDisconnected(controller: MediaController) {
					releaseController(restoreQueueOnReconnect = true)
				}
			})
			.buildAsync()
		controllerFuture = future
		future.addListener({
			val connected = try {
				future.get()
			} catch (e: Exception) {
				Logger.e("MediaPlayer", "could not connect to PlaybackService", e)
				if (controllerFuture === future) controllerFuture = null
				return@addListener
			}
			if (controllerFuture !== future) {
				connected.release()
				return@addListener
			}
			controller = connected
			setupController(connected)
		}, ContextCompat.getMainExecutor(application))
	}

	private fun releaseController(restoreQueueOnReconnect: Boolean) {
		progressJob?.cancel()
		progressJob = null
		if (restoreQueueOnReconnect && _uiState.value.queue.isNotEmpty()) {
			// The next service instance starts with an empty ExoPlayer; give it the queue back, paused.
			pendingSyncState = _uiState.value.copy(isPaused = true)
		}
		controllerFuture?.let(MediaController::releaseFuture)
		controllerFuture = null
		controller = null
	}

	/** Returns a connected controller, reconnecting if the service was released or died. */
	private suspend fun awaitController(): MediaController? {
		controller?.takeIf { it.isConnected }?.let { return it }
		if (controller != null) releaseController(restoreQueueOnReconnect = true)
		connectToService()
		val future = controllerFuture ?: return null
		return withTimeoutOrNull(5_000) {
			suspendCancellableCoroutine { cont ->
				future.addListener({
					cont.resume(runCatching { future.get() }.getOrNull()?.takeIf { it.isConnected })
				}, ContextCompat.getMainExecutor(application))
			}
		}
	}

	/** Called by the UI on start so the first tap after reopening the app doesn't wait for a connection. */
	override fun ensureConnected() {
		viewModelScope.launch(Dispatchers.Main.immediate) { awaitController() }
	}

	private fun setupController(connected: MediaController) {
		viewModelScope.launch {
			connected.apply {
				addListener(object : Player.Listener {
					override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
						if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) emitPlaybackBoundary(PlaybackBoundary.AutomaticAdvance)
						updatePlaybackState()
						skipUnavailableSong()
						checkAndAutoFillQueue()
					}

					override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
						if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
							emitPlaybackBoundary(PlaybackBoundary.PausedAtSongEnd)
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						if (isPlaying) startProgressLoop() else { progressJob?.cancel(); progressJob = null }
						updatePlaybackState()
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						if (playbackState == Player.STATE_ENDED) emitPlaybackBoundary(PlaybackBoundary.QueueEnded)
						_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
						updatePlaybackState()
					}

					override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
						_uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
					}

					override fun onRepeatModeChanged(repeatMode: Int) {
						_uiState.update { it.copy(repeatMode = repeatMode) }
					}

					override fun onTracksChanged(tracks: Tracks) {
						updatePlaybackProperties(tracks)
					}

					override fun onTimelineChanged(timeline: Timeline, reason: Int) {
						updatePlaybackState()
					}
				})
				updatePlaybackState()
				updatePlaybackProperties(currentTracks)

				downloadManager.awaitDownloadsLoaded()
				pendingSyncState?.let { state ->
					syncPlayerWithState(state)
					pendingSyncState = null
				}
				checkAndAutoFillQueue()
			}
		}
	}

	/**
	 * strategically skip around in the queue until the
	 * current song is available while avoiding infinite
	 * loops
	 *
	 * this **INTENTIONALLY** does not check for if the song
	 * is not downloaded and if the device is offline
	 *
	 * this used to check for that but because there have
	 * been cases where the device is falsely identified
	 * as being offline that's no longer the case, so we
	 * just try to play the song anyway
	 */
	private fun skipUnavailableSong() {
		val currentSong = _uiState.value.currentSong ?: return
		if (!isExplicit(currentSong)) return
		Logger.i("MediaPlayer", "trying to skip unavailable song")
		val queue = _uiState.value.queue
		val currentIdx = queue.indexOf(currentSong)

		// look for the next available song, wrapping around, but stop before
		// we loop back past our own starting point
		val nextAvailableIdx = (1..queue.size)
			.map { offset -> (currentIdx + offset) % queue.size }
			.firstOrNull { index -> !isExplicit(queue[index]) }

		if (nextAvailableIdx == null) {
			Logger.i(
				"MediaPlayer",
				"pausing because this song is unavailable and there isn't anything to skip to"
			)
			controller?.pause()
			return
		}

		if (nextAvailableIdx <= currentIdx && controller?.repeatMode == Player.REPEAT_MODE_OFF) {
			Logger.i(
				"MediaPlayer",
				"skipping and pausing because the last song in the queue was unavailable"
			)
			controller?.seekTo(nextAvailableIdx, 0L)
			controller?.pause()
		} else {
			// just skip to the next song
			controller?.seekTo(nextAvailableIdx, 0L)
		}
	}

	private fun refreshCurrentCollection(albumId: String) {
		if (loadingCollectionId == albumId) return
		loadingCollectionId = albumId

		viewModelScope.launch {
			runCatching {
				val album = albumDao.getAlbumById(albumId)

				_uiState.update { it.copy(currentCollection = album?.toDomainModel()) }
			}.onFailure {
				loadingCollectionId = null
			}
		}
	}

	private fun updatePlaybackState() {
		val controller = controller ?: return
		val index = controller.currentMediaItemIndex
		if (index == C.INDEX_UNSET) return

		// The controller is the playback authority.  Queue edits and media-item
		// transitions arrive over the Media3 controller asynchronously, so during
		// either operation the mirrored list can briefly have a different order.
		// Looking up only by index in that window made Now Playing blank (or show
		// the newly queued track) while ExoPlayer was correctly playing another
		// item.  The media ID is stable across those operations; prefer it and
		// repair the mirrored index from it.
		val state = _uiState.value
		val mediaId = controller.currentMediaItem?.mediaId
		val entryId = controller.currentMediaItem?.entryId()
		val resolvedIndex = state.queueEntryIds.indexOf(entryId).takeIf { it >= 0 }
			?: index.takeIf { state.queue.getOrNull(it)?.id == mediaId }
			?: state.queue.indexOfFirst { it.id == mediaId }
		val currentSong = state.queue.getOrNull(resolvedIndex)
		if (currentSong == null && !mediaId.isNullOrBlank()) {
			// Never substitute the controller's numeric index into a stale UI
			// queue: that is exactly how a queued track's artwork replaced the
			// currently playing one. Rebuild the mirror from Media3 instead.
			viewModelScope.launch {
				val (queue, currentIndex) = controllerQueueSnapshot()
				if (queue.isNotEmpty()) _uiState.update { latest -> latest.copy(
					queue = queue,
					queueEntryIds = controllerEntryIds(),
					currentIndex = currentIndex,
					currentSong = queue.getOrNull(currentIndex)
				) }
			}
			return
		}

		val derivedCollection = currentSong?.let { song ->
			val albumId = song.albumId ?: return@let null
			val stateCollection = _uiState.value.currentCollection

			if (stateCollection?.id == albumId) {
				stateCollection
			} else {
				refreshCurrentCollection(albumId)
				null
			}
		}
		val duration = controller.duration
		val progress = if (duration > 0L) {
			(controller.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
		} else {
			state.progress
		}

		_uiState.update { state ->
			state.copy(
				currentIndex = resolvedIndex,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = !controller.playWhenReady,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode,
				progress = progress
			)
		}
		updateProgress()
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		queueCommand {
			val player = controller

			if (player == null) {
				pendingSyncState = state
				return@queueCommand
			}

			if (state.queue.isEmpty() || player.mediaItemCount > 0) {
				updatePlaybackState()
				return@queueCommand
			}

			val mediaItems = withContext(Dispatchers.Default) {
				state.queue.map { mediaItemFactory.toMediaItem(it) }
			}

			_uiState.update { it.copy(queueEntryIds = mediaItems.map { item -> item.entryId() }) }
			player.setMediaItems(mediaItems)

			player.shuffleModeEnabled = state.isShuffleEnabled
			player.repeatMode = state.repeatMode
			player.playbackParameters = PlaybackParameters(state.playbackSpeed)

			val index = if (state.currentIndex in mediaItems.indices) state.currentIndex else 0

			val songDurationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L

			val position = if (songDurationMs > 0) {
				(state.progress * songDurationMs).toLong()
			} else {
				0L
			}

			player.seekTo(index, position)
			player.prepare()
			if (!state.isPaused) {
				player.play()
			}
		}
	}

	private fun startProgressLoop() {
		progressJob?.cancel()
		progressJob = viewModelScope.launch {
			// Restarts at once when the app comes back, so the bar never lags behind on screen.
			AppVisibility.visible.collectLatest { visible ->
				while (controller?.isPlaying == true) {
					val player = controller ?: break
					val duration = player.duration
					if (duration > 0) {
						val progress =
							(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
						_uiState.update { it.copy(progress = progress) }
					}
					// In the background this only feeds the saved resume position and Connect.
					delay(if (visible) 200.milliseconds else 5.seconds)
				}
			}
		}
	}

	private fun updateProgress() {
		controller?.let { player ->
			val duration = player.duration
			if (duration > 0) {
				val pos = player.currentPosition
				val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
			}
		}
	}

	private suspend fun rebuildMirrorFromController() {
		val (queue, currentIndex) = controllerQueueSnapshot()
		if (queue.isNotEmpty()) {
			_uiState.update {
				it.copy(queue = queue, queueEntryIds = controllerEntryIds(), currentIndex = currentIndex, currentSong = queue.getOrNull(currentIndex))
			}
		}
	}

	/**
	 * The Media3 controller can accept an album before its asynchronous timeline
	 * callback has populated our UI mirror.  Queue mutations must therefore take
	 * their baseline from the controller, not from _uiState.
	 */
	private fun MediaItem.entryId(): String = mediaMetadata.extras?.getString("ondo.queueEntryId") ?: mediaId
	private fun controllerEntryIds(): List<String> = controller?.let { player ->
		(0 until player.mediaItemCount).map { player.getMediaItemAt(it).entryId() }
	}.orEmpty()

	private suspend fun controllerQueueSnapshot(): Pair<List<DomainSong>, Int> {
		val player = controller ?: return _uiState.value.let { it.queue to it.currentIndex }
		val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
		if (ids.isEmpty()) return _uiState.value.let { it.queue to it.currentIndex }
		val songsById = songRepository.getSongsByIds(ids).associateBy { it.id }
		val queue = ids.mapNotNull(songsById::get)
		if (queue.size != ids.size) return _uiState.value.let { it.queue to it.currentIndex }
		return queue to player.currentMediaItemIndex.coerceIn(-1, queue.lastIndex)
	}

	@OptIn(UnstableApi::class)
	private fun updatePlaybackProperties(tracks: Tracks) {
		val audioGroup =
			tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
		if (audioGroup != null) {
			for (i in 0 until audioGroup.length) {
				if (audioGroup.isTrackSelected(i)) {
					val format = audioGroup.getTrackFormat(i)
					Logger.i("MediaPlayer", "Active Track Format: $format")
					_uiState.update { state ->
						state.copy(
							playbackBitrate = format.bitrate.takeIf { it > 0 },
							playbackSampleRate = format.sampleRate.takeIf { it > 0 },
							playbackMimeType = format.sampleMimeType
						)
					}
					break
				}
			}
		}
	}

	override fun addToQueueSingle(song: DomainSong, notify: Boolean) {
        if (ConnectRouting.route("enqueue", listOf(song))) return
		queueCommand {
			val item = withContext(Dispatchers.Default) { mediaItemFactory.toMediaItem(song) }
			val player = awaitController() ?: return@queueCommand
			player.addMediaItem(item)
			_uiState.update { state ->
				val newQueue = state.queue + song
				state.copy(
					queue = newQueue,
					queueEntryIds = state.entryIds() + item.entryId(),
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) song else state.currentSong
				)
			}
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun addToQueue(collection: DomainSongCollection, notify: Boolean) {
		addToQueue(
			if (collection is DomainAlbum) collection.songs.sortedWith(
				compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)
			) else collection.songs,
			notify
		)
	}

	override fun addToQueue(songs: List<DomainSong>, notify: Boolean) {
        if (ConnectRouting.route("enqueue", songs)) return
		queueCommand {
			// Building items stats a file per song; keep that off the main thread.
			val items = withContext(Dispatchers.Default) { songs.map { mediaItemFactory.toMediaItem(it) } }
			val player = awaitController() ?: return@queueCommand
			player.addMediaItems(items)
			_uiState.update { state ->
				val newQueue = state.queue + songs
				state.copy(
					queue = newQueue,
					queueEntryIds = state.entryIds() + items.map { it.entryId() },
					currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
					currentSong = if (state.currentIndex == -1) songs.firstOrNull() else state.currentSong
				)
			}
			if (notify) snackBarManager.notifyAddedToQueue()
		}
	}

	override fun removeFromQueue(index: Int) {
        if (ConnectRouting.route("remove", index = index)) return
		queueCommand {
			val player = awaitController() ?: return@queueCommand
			if (index !in 0 until player.mediaItemCount) return@queueCommand
			player.removeMediaItem(index)
			if (index !in _uiState.value.queue.indices) {
				// The UI mirror drifted from the player; rebuild it instead of throwing.
				rebuildMirrorFromController()
				return@queueCommand
			}
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply { removeAt(index) }
				val newIndex = when {
					index < state.currentIndex -> state.currentIndex - 1
					index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(
						newQueue.size - 1
					)

					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					queueEntryIds = state.entryIds().toMutableList().apply { removeAt(index) },
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (ConnectRouting.route("move", index = fromIndex, value = toIndex.toFloat())) return
		queueCommand {
			val player = awaitController() ?: return@queueCommand
			val count = player.mediaItemCount
			if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) return@queueCommand
			player.moveMediaItem(fromIndex, toIndex)
			val mirrorSize = _uiState.value.queue.size
			if (fromIndex >= mirrorSize || toIndex >= mirrorSize) {
				rebuildMirrorFromController()
				return@queueCommand
			}
			_uiState.update { state ->
				val newQueue = state.queue.toMutableList().apply {
					val item = removeAt(fromIndex)
					add(toIndex, item)
				}
				val newIndex = when (state.currentIndex) {
					fromIndex -> toIndex
					in (fromIndex + 1)..toIndex -> state.currentIndex - 1
					in toIndex until fromIndex -> state.currentIndex + 1
					else -> state.currentIndex
				}
				state.copy(
					queue = newQueue,
					queueEntryIds = state.entryIds().toMutableList().apply { add(toIndex, removeAt(fromIndex)) },
					currentIndex = newIndex,
					currentSong = if (newIndex == -1) null else newQueue[newIndex]
				)
			}
		}
	}

	override fun clearQueue() {
        if (ConnectRouting.route("clear")) return
		queueCommand {
			_uiState.update {
				it.copy(
					queue = emptyList(),
					queueEntryIds = emptyList(),
					currentSong = null,
					currentIndex = -1,
					progress = 0f
				)
			}
			awaitController()?.clearMediaItems()
		}
	}

	override fun playAt(index: Int) {
        if (ConnectRouting.route("playAt", index = index)) return
		queueCommand {
			awaitController()?.let { player ->
				if (index in 0 until player.mediaItemCount) {
					player.seekTo(index, 0L)
					player.play()
					// Media3's own onMediaItemTransition/onTimelineChanged callback will
					// confirm/correct this shortly, but it's IPC-async -- without this,
					// currentIndex/currentSong stay stale (or -1 right after playNow's
					// clearQueue()) for however long that takes, so a play-next/add-to-queue
					// issued in that window computes its insertion point from the wrong
					// index and desyncs the app queue from the controller's real playlist.
					_uiState.update { state ->
						state.copy(
							currentIndex = index,
							currentSong = state.queue.getOrNull(index)
						)
					}
				}
			}
		}
	}

	override fun playNextSingle(song: DomainSong) = playNext(listOf(song))

	override fun playNext(collection: DomainSongCollection) = playNext(
		if (collection is DomainAlbum) collection.songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
		else collection.songs
	)

	override fun playNext(songs: List<DomainSong>) {
		if (songs.isEmpty() || ConnectRouting.route("next", songs)) return
		queueCommand {
			val items = withContext(Dispatchers.Default) { songs.map { mediaItemFactory.toMediaItem(it) } }
			val player = awaitController() ?: return@queueCommand
			val (queue, currentIndex) = controllerQueueSnapshot()
			val previousEntryIds = _uiState.value.entryIds()
			val insertAt = (currentIndex + 1).coerceIn(0, queue.size)
			val args = Bundle().apply { putParcelableArrayList("items", ArrayList(items.map { it.toBundleIncludeLocalConfiguration() })) }
			val future = player.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_PLAY_NEXT, Bundle.EMPTY), args)
			suspendCancellableCoroutine<Unit> { continuation ->
				future.addListener({
					if (continuation.isActive) {
						val result = runCatching { future.get() }.getOrNull()
						if (result?.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) continuation.resume(Unit)
						else continuation.resumeWith(Result.failure(IllegalStateException("Could not insert tracks")))
					}
				}, application.mainExecutor)
				continuation.invokeOnCancellation { future.cancel(false) }
			}
			val newQueue = queue.take(insertAt) + songs + queue.drop(insertAt)
			_uiState.update { state -> state.copy(
				queue = newQueue,
				queueEntryIds = previousEntryIds.take(insertAt) + items.map { it.entryId() } + previousEntryIds.drop(insertAt),
				currentIndex = currentIndex.coerceIn(0, newQueue.lastIndex),
				currentSong = newQueue.getOrNull(currentIndex)
			) }
			snackBarManager.notifyPlayNext()
		}
	}

	override fun playRadio(radio: DomainRadio) {
        if (ConnectRouting.route("radio")) return
		queueCommand {
			val radioId = "radio_${radio.name.hashCode()}"

			val dummyRadioSong = DomainSong(
				id = radioId,
				title = radio.name,
				artistName = "Live Radio",
				albumId = "radio_album",
				albumTitle = "Live Stream",
				duration = Duration.ZERO,
				trackNumber = 1,
				coverArtId = null,
				artistId = "",
				parentId = "",
				comment = null,
				discNumber = null,
				isrc = emptyList(),
				year = null,
				genre = null,
				genres = emptyList(),
				moods = emptyList(),
				bpm = null,
				contributors = emptyList(),
				playCount = 0,
				userRating = 0,
				averageRating = null,
				bitRate = null,
				bitDepth = null,
				sampleRate = null,
				audioChannelCount = null,
				replayGain = null,
				fileSize = 0,
				fileExtension = "",
				mimeType = "",
				filePath = radio.streamUrl,
				starredAt = null,
				musicBrainzId = null,
				explicitStatus = DomainExplicitStatus.Unknown,
				artists = emptyList(),
				albumArtists = emptyList(),
				isExternal = false
			)

			val mediaItem = mediaItemFactory.toMediaItem(dummyRadioSong)

			awaitController()?.let { player ->
				player.stop()
				player.clearMediaItems()
				player.setMediaItem(mediaItem)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = listOf(dummyRadioSong),
					queueEntryIds = listOf(mediaItem.entryId()),
					currentIndex = 0,
					currentSong = dummyRadioSong,
					isLoading = true
				)
			}
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		notePlayed(collection)
        if (ConnectRouting.route("playNow", collection.songs.shuffled())) return
		queueCommand {
			val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
				val songs = collection.songs.shuffled()
				songs to songs.map { mediaItemFactory.toMediaItem(it) }
			}

			awaitController()?.let { player ->
				player.shuffleModeEnabled = false
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = shuffledSongs,
					queueEntryIds = mediaItems.map { it.entryId() },
					currentIndex = 0,
					currentSong = shuffledSongs.firstOrNull()
				)
			}
		}
	}

	override fun pauseLocal() { viewModelScope.launch { awaitController()?.pause() } }

	override fun setPauseAtSongEnd(enabled: Boolean, queueEndOnly: Boolean) {
		viewModelScope.launch {
			awaitController()?.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_SLEEP_BOUNDARY, Bundle.EMPTY),
				Bundle().apply { putBoolean("enabled", enabled); putBoolean("queueEndOnly", queueEndOnly) })
		}
	}

	override fun pause() {
        if (ConnectRouting.route("pause")) return
		viewModelScope.launch(Dispatchers.Main.immediate) {
			awaitController()?.pause()
		}
	}

	override fun resume() {
        if (ConnectRouting.route("play")) return
		viewModelScope.launch(Dispatchers.Main.immediate) {
			awaitController()?.play()
		}
	}

	override fun next() {
        if (ConnectRouting.route("skipNext")) return
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = awaitController() ?: return@launch
			if (player.hasNextMediaItem()) player.seekToNextMediaItem()
		}
	}

	override fun previous() {
        if (ConnectRouting.route("skipPrevious")) return
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val player = awaitController() ?: return@launch
			if (player.hasPreviousMediaItem() && player.currentPosition <= 1000) {
				player.seekToPreviousMediaItem()
			} else {
				player.seekTo(0)
			}
		}
	}

	override fun replaceQueue(state: PlayerUiState) {
		queueCommand {
			val items = withContext(Dispatchers.Default) { state.queue.map { mediaItemFactory.toMediaItem(it) } }
			val player = awaitController() ?: return@queueCommand
			if (items.isEmpty()) {
				player.clearMediaItems()
				_uiState.value = state
				return@queueCommand
			}
			val index = state.currentIndex.coerceIn(0, items.lastIndex)
			val durationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L
			val positionMs = if (durationMs > 0) (state.progress * durationMs).toLong() else 0L
			player.setMediaItems(items, index, positionMs)
			player.shuffleModeEnabled = state.isShuffleEnabled
			player.repeatMode = state.repeatMode
			player.setPlaybackSpeed(state.playbackSpeed)
			player.prepare()
			if (state.isPaused) player.pause() else player.play()
			_uiState.value = state.copy(queueEntryIds = items.map { it.entryId() }, currentIndex = index, currentSong = state.queue.getOrNull(index))
		}
	}

	override fun setShuffle(enabled: Boolean) {
		viewModelScope.launch { awaitController()?.shuffleModeEnabled = enabled }
	}

	override fun setRepeatMode(mode: Int) {
		viewModelScope.launch {
			awaitController()?.repeatMode = when (mode) {
				1 -> Player.REPEAT_MODE_ONE
				2 -> Player.REPEAT_MODE_ALL
				else -> Player.REPEAT_MODE_OFF
			}
		}
	}

	override fun toggleShuffle() {
        if (ConnectRouting.route("shuffle")) return
		viewModelScope.launch {
			awaitController()?.let { player ->
				player.shuffleModeEnabled = !player.shuffleModeEnabled
			}
		}
	}

	override fun toggleRepeat() {
        if (ConnectRouting.route("repeat")) return
		viewModelScope.launch {
			awaitController()?.let { player ->
				player.repeatMode = when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
			}
		}
	}

	override fun seek(normalized: Float) {
        if (ConnectRouting.route("seek", value = normalized)) return
		viewModelScope.launch(Dispatchers.Main.immediate) {
			awaitController()?.let {
				if (it.duration <= 0 || it.isCurrentMediaItemLive || !normalized.isFinite()) return@let
				val target = (it.duration * normalized.coerceIn(0f, 1f)).toLong()
				it.seekTo(target)
				_uiState.update { state ->
					state.copy(progress = normalized)
				}
			}
		}
	}

	// Note: this ViewModel is registered as a Koin `single`, so onCleared() never runs in
	// practice; reconnection after the service releases the controller goes through awaitController().
	override fun onCleared() {
		CarPlaybackBridge.onSelectionPlayed = null
		PlaybackControllerRegistry.releaseController = null
		releaseController(restoreQueueOnReconnect = false)
		super.onCleared()
	}

	override fun setPlaybackSpeed(value: Float) {
        if (ConnectRouting.route("speed", value = value)) return
		viewModelScope.launch {
			awaitController()?.setPlaybackSpeed(value)
		}
		_uiState.update { it.copy(playbackSpeed = value) }
	}
}
