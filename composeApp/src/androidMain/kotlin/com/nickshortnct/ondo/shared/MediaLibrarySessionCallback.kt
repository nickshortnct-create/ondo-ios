package com.nickshortnct.ondo.shared

import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.nickshortnct.ondo.domain.repositories.PlayerStateRepository
import com.nickshortnct.ondo.domain.models.PlaybackSearchRequest
import com.nickshortnct.ondo.domain.models.ResultKind
import com.nickshortnct.ondo.domain.models.searchResultPage
import com.nickshortnct.ondo.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Browse + play implementation for Android Auto. Wired into [PlaybackService] via
 * [MediaLibrarySession].
 */
@OptIn(UnstableApi::class)
class MediaLibrarySessionCallback(
	private val player: androidx.media3.common.Player,
	private val libraryTree: MediaLibraryTree,
	private val stateRepository: PlayerStateRepository,
	private val scope: CoroutineScope,
	private val onToggleFavorite: () -> Unit = {},
	private val onRemoteControllerConnected: () -> Unit = {},
	private val onPlaylistPlayed: (String) -> Unit = {},
	/** Nick's own apps (see [PartnerApps]) get the full library, like Android Auto. */
	private val isPartnerApp: (MediaSession.ControllerInfo) -> Boolean = { false },
) : MediaLibrarySession.Callback {
	private var pauseAtQueueEnd = false
	private fun updateQueueEndPause() {
		if (!pauseAtQueueEnd) return
		val exo = player as ExoPlayer
		val index = exo.currentMediaItemIndex
		exo.setPauseAtEndOfMediaItems(index >= 0 && if (exo.shuffleModeEnabled)
			exo.shuffleOrder.getNextIndex(index) == C.INDEX_UNSET else index == exo.mediaItemCount - 1)
	}
	init {
		player.addListener(object : androidx.media3.common.Player.Listener {
			override fun onMediaItemTransition(item: MediaItem?, reason: Int) { updateQueueEndPause() }
			override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { updateQueueEndPause() }
			override fun onShuffleModeEnabledChanged(enabled: Boolean) { updateQueueEndPause() }
		})
	}


	override fun onConnect(
		session: MediaSession,
		controller: MediaSession.ControllerInfo
	): MediaSession.ConnectionResult {
		Logger.i(
			"MediaLibrarySessionCallback",
			"onConnect from ${controller.packageName} (uid=${controller.uid}, trusted=${controller.isTrusted})"
		)
		return try {
			val own = controller.packageName == session.token.packageName
			val trusted = own || controller.isTrusted || session.isMediaNotificationController(controller) ||
				session.isAutoCompanionController(controller) || session.isAutomotiveController(controller) ||
				controller.packageName in TRUSTED_REMOTE_PACKAGES || isPartnerApp(controller)
			if (!trusted) return MediaSession.ConnectionResult.accept(
				androidx.media3.session.SessionCommands.EMPTY,
				androidx.media3.common.Player.Commands.Builder().addAll(
					androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
					androidx.media3.common.Player.COMMAND_STOP,
					androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
					androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
					androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
			)
			val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
				.buildUpon()
				.add(SessionCommand(PlaybackService.COMMAND_SHUFFLE, Bundle.EMPTY))
				.add(SessionCommand(PlaybackService.COMMAND_REPEAT, Bundle.EMPTY))
				.add(SessionCommand(PlaybackService.COMMAND_PLAY_NEXT, Bundle.EMPTY))
				.add(SessionCommand(PlaybackService.COMMAND_SLEEP_BOUNDARY, Bundle.EMPTY))
				.add(SessionCommand(PlaybackService.COMMAND_FAVORITE, Bundle.EMPTY))
				.build()

			MediaSession.ConnectionResult.accept(
				sessionCommands,
				MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
			).also {
				Logger.i("MediaLibrarySessionCallback", "onConnect accepted ${controller.packageName}")
				if (!own && (controller.packageName in TRUSTED_REMOTE_PACKAGES ||
						session.isAutoCompanionController(controller) || session.isAutomotiveController(controller))) {
					onRemoteControllerConnected()
				}
			}
		} catch (ex: Exception) {
			// An uncaught exception on this binder callback is worse than a
			// well-defined refusal -- reject cleanly instead of letting it
			// propagate and possibly take the whole connection attempt down
			// silently from Auto's side.
			Logger.e("MediaLibrarySessionCallback", "onConnect failed for ${controller.packageName}", ex)
			MediaSession.ConnectionResult.reject()
		}
	}

	/**
	 * Called by Media3 whenever a controller (Android Auto/Automotive on car
	 * connect, the system after the app process was killed and restarted, ...)
	 * connects to a session whose player has no current media item. Without
	 * this override the default implementation just fails, which is why the
	 * car previously looked like Ondo had never been opened: [PlaybackService]
	 * builds a brand new, empty ExoPlayer every time the process starts, and
	 * nothing else in the service layer ever re-populates it -- the in-app
	 * queue restore (`MediaPlayerViewModel.restoreState()`) only runs from the
	 * Activity, which Auto never launches. This is the equivalent restore for
	 * a headless start: read the same persisted [PlayerStateRepository] state
	 * the app itself resumes from, and hand it back as the queue to resume.
	 */
	override fun onPlaybackResumption(
		mediaSession: MediaSession,
		controller: MediaSession.ControllerInfo
	): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
		onPlaybackResumption(mediaSession, controller, isForPlayback = true)

	override fun onPlaybackResumption(
		mediaSession: MediaSession,
		controller: MediaSession.ControllerInfo,
		isForPlayback: Boolean
	): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
		Logger.i("MediaLibrarySessionCallback", "onPlaybackResumption from ${controller.packageName}")
		val saved = withTimeoutOrNull(2000) { stateRepository.readState() }
		val song = saved?.queue?.getOrNull(saved.currentIndex) ?: saved?.currentSong
		if (saved == null || song == null || saved.queue.isEmpty()) {
			Logger.i("MediaLibrarySessionCallback", "onPlaybackResumption: no saved queue to resume")
			throw UnsupportedOperationException("Nothing to resume")
		}
		// Resume a bounded window around the current song; a huge queue would blow the binder limit.
		val savedIndex = saved.currentIndex.coerceIn(0, saved.queue.lastIndex)
		val windowStart = (savedIndex - MAX_RESUMED_QUEUE / 2).coerceAtLeast(0)
		val windowSongs = saved.queue.subList(windowStart, (windowStart + MAX_RESUMED_QUEUE).coerceAtMost(saved.queue.size))
		val items = windowSongs.map { libraryTree.toPlayableMediaItem(it) }
		val startIndex = savedIndex - windowStart
		val startPositionMs = (saved.progress.coerceIn(0f, 1f) * song.duration.inWholeMilliseconds).toLong()
		CarPlaybackBridge.notifySelectionPlayed(windowSongs, startIndex)
		// Media3 decides whether playback starts (isForPlayback); never touch the player from this thread.
		Logger.i(
			"MediaLibrarySessionCallback",
			"onPlaybackResumption resuming ${items.size} item(s) at index $startIndex / ${startPositionMs}ms"
		)
		MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs)
	}

	override fun onCustomCommand(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
		customCommand: SessionCommand,
		args: Bundle
	): ListenableFuture<SessionResult> {
		Logger.i("MediaLibrarySessionCallback", "onCustomCommand ${customCommand.customAction} from ${controller.packageName}")
		if (customCommand.customAction in setOf(PlaybackService.COMMAND_PLAY_NEXT, PlaybackService.COMMAND_SLEEP_BOUNDARY) &&
			controller.packageName != session.token.packageName) {
			return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
		}
		return try {
			when (customCommand.customAction) {
				PlaybackService.COMMAND_SLEEP_BOUNDARY -> {
					val enabled = args.getBoolean("enabled")
					pauseAtQueueEnd = enabled && args.getBoolean("queueEndOnly")
					(player as ExoPlayer).setPauseAtEndOfMediaItems(enabled && !pauseAtQueueEnd)
					updateQueueEndPause()
				}
				PlaybackService.COMMAND_PLAY_NEXT -> {
					val exo = player as ExoPlayer
					@Suppress("DEPRECATION")
					val items = args.getParcelableArrayList<Bundle>("items").orEmpty().map(MediaItem::fromBundle)
					val current = exo.currentMediaItemIndex
					val insertAt = (current + 1).coerceIn(0, exo.mediaItemCount)
					val order = exo.shuffleOrder
					val previousOrder = buildList {
						var next = order.firstIndex
						while (next != C.INDEX_UNSET && size < exo.mediaItemCount) { add(next); next = order.getNextIndex(next) }
					}
					exo.addMediaItems(insertAt, items)
					if (exo.shuffleModeEnabled) {
						exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(
							playNextShuffleOrder(previousOrder, current, insertAt, items.size).toIntArray(), System.nanoTime()))
					}
				}
				PlaybackService.COMMAND_FAVORITE -> onToggleFavorite()

				PlaybackService.COMMAND_SHUFFLE -> {
					player.shuffleModeEnabled = !player.shuffleModeEnabled
				}

				PlaybackService.COMMAND_REPEAT -> {
					player.repeatMode = when (player.repeatMode) {
						androidx.media3.common.Player.REPEAT_MODE_OFF ->
							androidx.media3.common.Player.REPEAT_MODE_ALL

						androidx.media3.common.Player.REPEAT_MODE_ALL ->
							androidx.media3.common.Player.REPEAT_MODE_ONE

						else -> androidx.media3.common.Player.REPEAT_MODE_OFF
					}
				}
			}

			Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		} catch (ex: Exception) {
			Logger.e("MediaLibrarySessionCallback", "onCustomCommand ${customCommand.customAction} failed", ex)
			Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
		}
	}

	override fun onGetLibraryRoot(
		session: MediaLibrarySession,
		controller: MediaSession.ControllerInfo,
		params: MediaLibraryService.LibraryParams?
	): ListenableFuture<LibraryResult<MediaItem>> {
		Logger.i(
			"MediaLibrarySessionCallback",
			"onGetLibraryRoot from ${controller.packageName} (recent=${params?.isRecent}, suggested=${params?.isSuggested})"
		)
		params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 0)
			?.takeIf { it > 0 }
			?.let { libraryTree.rootChildrenLimit = it }
		return try {
			Futures.immediateFuture(
				LibraryResult.ofItem(
					libraryTree.rootItem(),
					MediaLibraryService.LibraryParams.Builder()
						.setExtras(libraryTree.rootExtras())
						.build()
				)
			).also {
				Logger.i("MediaLibrarySessionCallback", "onGetLibraryRoot succeeded")
			}
		} catch (ex: Exception) {
			Logger.e("MediaLibrarySessionCallback", "onGetLibraryRoot failed", ex)
			Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
		}
	}

	override fun onGetItem(
		session: MediaLibrarySession,
		controller: MediaSession.ControllerInfo,
		mediaId: String
	): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
		Logger.i("MediaLibrarySessionCallback", "onGetItem $mediaId")
		val item = libraryTree.item(mediaId)
			?: return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED)
		LibraryResult.ofItem(item, MediaLibraryService.LibraryParams.Builder().build())
	}

	override fun onGetChildren(
		session: MediaLibrarySession,
		controller: MediaSession.ControllerInfo,
		parentId: String,
		page: Int,
		pageSize: Int,
		params: MediaLibraryService.LibraryParams?
	): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
		Logger.i("MediaLibrarySessionCallback", "onGetChildren $parentId page=$page pageSize=$pageSize")
		if (page < 0 || pageSize <= 0) return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, params)
		val children = searchResultPage(libraryTree.children(parentId), page, pageSize)
		Logger.i("MediaLibrarySessionCallback", "onGetChildren $parentId -> ${children.size} items")
		LibraryResult.ofItemList(
			ImmutableList.copyOf(children),
			MediaLibraryService.LibraryParams.Builder().build()
		)
	}

	override fun onSearch(
		session: MediaLibrarySession,
		controller: MediaSession.ControllerInfo,
		query: String,
		params: MediaLibraryService.LibraryParams?
	): ListenableFuture<LibraryResult<Void>> = scope.future {
		val results = libraryTree.search(query)
		withContext(Dispatchers.Main.immediate) {
			session.notifySearchResultChanged(controller, query, results.size, params)
		}
		LibraryResult.ofVoid(params)
	}

	override fun onGetSearchResult(
		session: MediaLibrarySession,
		controller: MediaSession.ControllerInfo,
		query: String,
		page: Int,
		pageSize: Int,
		params: MediaLibraryService.LibraryParams?
	): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
		if (page < 0 || pageSize <= 0) {
			return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, params)
		}
		val results = libraryTree.search(query)
		LibraryResult.ofItemList(
			ImmutableList.copyOf(searchResultPage(results, page, pageSize)),
			params
		)
	}

	override fun onAddMediaItems(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
		mediaItems: MutableList<MediaItem>
	): ListenableFuture<MutableList<MediaItem>> = scope.future {
		Logger.i("MediaLibrarySessionCallback", "onAddMediaItems ${mediaItems.size} item(s) from ${controller.packageName}")
		resolveForPlayback(mediaItems).items.toMutableList()
	}

	override fun onSetMediaItems(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
		mediaItems: MutableList<MediaItem>,
		startIndex: Int,
		startPositionMs: Long
	): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
		Logger.i("MediaLibrarySessionCallback", "onSetMediaItems ${mediaItems.size} item(s) from ${controller.packageName}, startIndex=$startIndex")
		val resolved = resolveForPlayback(
			mediaItems,
			startIndex = startIndex.takeIf { it != C.INDEX_UNSET } ?: 0,
			startPositionMs = startPositionMs.takeIf { it != C.TIME_UNSET }
		)
		MediaSession.MediaItemsWithStartPosition(
			resolved.items,
			resolved.startIndex,
			resolved.startPositionMs ?: startPositionMs
		)
	}

	private suspend fun resolveForPlayback(
		mediaItems: List<MediaItem>,
		startIndex: Int = 0,
		startPositionMs: Long? = null
	): ResolvedPlaybackItems {
		// Items built by OndoMediaItemFactory already know how to play (they carry a
		// URI); never re-resolve those. In particular a single "Add to queue" /
		// "Play next" from the app must not be treated as an Android Auto browse
		// tap, which would replace the app's queue mirror and drop uncached songs.
		if (mediaItems.isNotEmpty() && mediaItems.all { isReadyToPlay(it) }) {
			return ResolvedPlaybackItems(
				mediaItems,
				startIndex.coerceIn(0, mediaItems.lastIndex),
				startPositionMs
			)
		}

		// More than one item that still needs resolving: an external controller sent a
		// queue of bare ids. Keep order, use the tree's playable item for each id and
		// drop anything that still has no URI.
		if (mediaItems.size > 1) {
			val resolved = mediaItems.mapNotNull { item ->
				item.takeIf { isReadyToPlay(it) }
					?: libraryTree.item(item.mediaId)?.takeIf { it.localConfiguration != null }
			}
			return ResolvedPlaybackItems(
				resolved,
				startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0)),
				startPositionMs
			)
		}

		val first = mediaItems.firstOrNull()
			?: return ResolvedPlaybackItems(emptyList(), 0)

		// Media3 maps legacy playFromSearch/prepareFromSearch into this field.
		first.requestMetadata.searchQuery?.let { query ->
			val extras = first.requestMetadata.extras
			val focus = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
				MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> ResultKind.SONG
				MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> ResultKind.ARTIST
				MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> ResultKind.ALBUM
				MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> ResultKind.PLAYLIST
				else -> null
			}
			val request = PlaybackSearchRequest(
				query = query,
				focus = focus,
				title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE).orEmpty(),
				artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST).orEmpty(),
				album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM).orEmpty(),
				playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST).orEmpty()
			)
			if (request.lookupTerm.isBlank()) {
				val current = withContext(Dispatchers.Main.immediate) {
					if (player.mediaItemCount == 0) null else ResolvedPlaybackItems(
						(0 until player.mediaItemCount).map(player::getMediaItemAt),
						player.currentMediaItemIndex.coerceAtLeast(0),
						player.currentPosition
					)
				}
				if (current != null) return current
				val saved = withTimeoutOrNull(2_000) { stateRepository.readState() }
				if (saved != null && saved.queue.isNotEmpty()) {
					val index = saved.currentIndex.coerceIn(0, saved.queue.lastIndex)
					CarPlaybackBridge.notifySelectionPlayed(saved.queue, index)
					return ResolvedPlaybackItems(
						saved.queue.map { libraryTree.toPlayableMediaItem(it) },
						index,
						(saved.progress.coerceIn(0f, 1f) * saved.queue[index].duration.inWholeMilliseconds).toLong()
					)
				}
			}
			val selection = libraryTree.resolveSearch(request)
				?: throw UnsupportedOperationException("No matching music is available in your library")
			CarPlaybackBridge.notifySelectionPlayed(selection.songs, selection.startIndex)
			return ResolvedPlaybackItems(selection.songs.map { libraryTree.toPlayableMediaItem(it) }, selection.startIndex)
		}

		val selection = libraryTree.resolve(first.mediaId)
		if (selection != null) {
			CarPlaybackBridge.notifySelectionPlayed(selection.songs, selection.startIndex)
			(MediaId.playlistIdOf(first.mediaId) ?: (MediaId.parseTrack(first.mediaId)?.first as? TrackParent.Playlist)?.id)
				?.let(onPlaylistPlayed)
			val items = selection.songs.map { song ->
				libraryTree.toPlayableMediaItem(song, song.id)
			}
			return ResolvedPlaybackItems(items, selection.startIndex)
		}

		val item = libraryTree.item(first.mediaId)
		if (item != null) {
			return ResolvedPlaybackItems(listOf(item), 0)
		}

		Logger.w("MediaLibrarySessionCallback", "could not resolve ${first.mediaId}")
		return ResolvedPlaybackItems(emptyList(), 0)
	}

	/**
	 * Hierarchical ids (`ondo/track/...`) are browse nodes from Auto: even if one
	 * carries a URI it must still be expanded into its containing album/playlist.
	 */
	private fun isReadyToPlay(item: MediaItem): Boolean =
		isPlayableAsIs(item.mediaId, item.localConfiguration != null)

	private data class ResolvedPlaybackItems(
		val items: List<MediaItem>,
		val startIndex: Int,
		val startPositionMs: Long? = null,
	)

	private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
		val future = SettableFuture.create<T>()
		val job = launch {
			try {
				future.set(block())
			} catch (error: CancellationException) {
				future.cancel(false)
				throw error
			} catch (error: Exception) {
				Logger.e("MediaLibrarySessionCallback", "library callback failed", error)
				future.setException(error)
			}
		}
		future.addListener({ if (future.isCancelled) job.cancel() }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
		return future
	}
}

private const val MAX_RESUMED_QUEUE = 500

/** Google surfaces that render our full player (car, assistant, watch); untrusted apps get transport only. */
private val TRUSTED_REMOTE_PACKAGES = setOf(
	"com.google.android.projection.gearhead",
	"com.google.android.googlequicksearchbox",
	"com.google.android.carassistant",
	"com.google.android.wearable.app",
	"com.google.android.apps.wearables.maestro.companion",
)

/** Pure decision behind pass-through of app-built items; see [MediaLibrarySessionCallback]. */
internal fun isPlayableAsIs(mediaId: String, hasUri: Boolean): Boolean =
	hasUri && MediaId.parseTrack(mediaId) == null
