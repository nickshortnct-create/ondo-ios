package com.nickshortnct.ondo.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.nickshortnct.ondo.ondo.ConnectRouting
import com.nickshortnct.ondo.ondo.PlaylistPlayStats
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainRadio
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import com.nickshortnct.ondo.domain.repositories.PlayerStateRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.ui.core.PlayerUiState
import kotlin.time.Duration.Companion.seconds

enum class PlaybackBoundary { AutomaticAdvance, PausedAtSongEnd, QueueEnded }

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val songRepository: SongRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	protected val preferenceManager: PreferenceManager
) : ViewModel(), KoinComponent {

	@Suppress("PropertyName")
	protected val _uiState = MutableStateFlow(PlayerUiState())
	val localUiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val uiState: StateFlow<PlayerUiState> = combine(_uiState, ConnectRouting.remoteState) { local, remote -> remote ?: local }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

	protected fun isExplicit(song: DomainSong): Boolean {
		return song.explicitStatus == DomainExplicitStatus.Explicit
			&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	}

	private var started = false
	private var autoFillJob: kotlinx.coroutines.Job? = null

	protected fun start() {
		if (started) return
		started = true
		viewModelScope.launch {
			restoreState()
			observeAndSaveState()
		}
	}

	private val _playbackBoundaries = kotlinx.coroutines.flow.MutableSharedFlow<PlaybackBoundary>(extraBufferCapacity = 8)
	val playbackBoundaries = _playbackBoundaries as kotlinx.coroutines.flow.SharedFlow<PlaybackBoundary>
	protected fun emitPlaybackBoundary(value: PlaybackBoundary) { _playbackBoundaries.tryEmit(value) }
	open fun setPauseAtSongEnd(enabled: Boolean, queueEndOnly: Boolean = false) {}
	open fun pauseLocal() = pause()

	abstract fun addToQueueSingle(song: DomainSong, notify: Boolean = true)
	abstract fun addToQueue(collection: DomainSongCollection, notify: Boolean = true)
	abstract fun addToQueue(songs: List<DomainSong>, notify: Boolean = true)
	abstract fun removeFromQueue(index: Int)
	abstract fun moveQueueItem(fromIndex: Int, toIndex: Int)
	abstract fun clearQueue()
	abstract fun playAt(index: Int)
	abstract fun playNextSingle(song: DomainSong)
	abstract fun playNext(collection: DomainSongCollection)
	open fun playNext(songs: List<DomainSong>) { songs.asReversed().forEach(::playNextSingle) }
	abstract fun playRadio(radio: DomainRadio)
	abstract fun pause()
	abstract fun resume()
	abstract fun seek(normalized: Float)
	abstract fun next()
	abstract fun previous()
	abstract fun toggleShuffle()
	abstract fun toggleRepeat()
	abstract fun shufflePlay(collection: DomainSongCollection)

	private val playlistPlayStats: PlaylistPlayStats by inject()

	/** Counts playlist plays, so "Add to playlist" can list the most played playlists first. */
	protected fun notePlayed(collection: DomainSongCollection) {
		if (collection is DomainPlaylist) playlistPlayStats.record(collection.id)
	}
	abstract fun setPlaybackSpeed(value: Float)

	fun playNow(song: DomainSong) {
        if (ConnectRouting.route("playNow", listOf(song))) return
		clearQueue()
		addToQueueSingle(song, notify = false)
		playAt(0)
		checkAndAutoFillQueue()
	}

	fun playNow(collection: DomainSongCollection, startIndex: Int = 0) {
		notePlayed(collection)
        if (ConnectRouting.route("playNow", collection.songs, startIndex)) return
		clearQueue()
		addToQueue(collection, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun playNow(songs: List<DomainSong>, startIndex: Int = 0) {
        if (ConnectRouting.route("playNow", songs, startIndex)) return
		clearQueue()
		addToQueue(songs, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun togglePlay() {
		if (!uiState.value.isPaused) {
			pause()
		} else {
			resume()
		}
	}

	/**
	 * Replaces the whole queue, position, shuffle, repeat and speed in a single ordered step (used by
	 * Connect transfers), so a clear can never race the load of the new queue.
	 */
	abstract fun replaceQueue(state: PlayerUiState)

	/** Explicit setters (Connect's remote commands name the target value rather than toggling). */
	open fun setShuffle(enabled: Boolean) {}
	open fun setRepeatMode(mode: Int) {}

	abstract fun syncPlayerWithState(state: PlayerUiState)

	/** Platform hook: make sure the playback backend is connected (no-op where it always is). */
	open fun ensureConnected() {}

	open suspend fun clearSession() {
		pauseLocal()
		clearQueue()
		awaitQueueIdle()
		_uiState.value = PlayerUiState(isPaused = true)
		stateRepository.clear()
	}

	protected open suspend fun awaitQueueIdle() {}

	protected fun checkAndAutoFillQueue() {
		if (autoFillJob?.isActive == true) return
		autoFillJob = viewModelScope.launch {
			awaitQueueIdle()
			if (!preferenceManager.autoFillQueue || ConnectRouting.remoteState.value != null) return@launch
			val state = localUiState.value
			if (state.queue.isEmpty()) return@launch
			if (state.queue.size - state.currentIndex <= 1) {
				val songs = com.nickshortnct.ondo.util.runCatchingCancellable { songRepository.getRandomSongs(1) }.getOrNull() ?: return@launch
				val latest = localUiState.value
				if (ConnectRouting.remoteState.value == null && latest.queue == state.queue && latest.currentIndex == state.currentIndex)
					addToQueue(songs, notify = false)
			}
		}
	}

	private suspend fun restoreState() {
		val savedState = stateRepository.readState()
			?.copy(isPaused = true, isLoading = false)
		if (savedState != null) {
			_uiState.value = savedState
			syncPlayerWithState(savedState)
			checkAndAutoFillQueue()
		}
	}

	@OptIn(FlowPreview::class)
	private fun observeAndSaveState() {
		// Queue, index and flags: written only when one of them changes (also on pause, which
		// captures the position).
		viewModelScope.launch {
			localUiState
				.distinctUntilChanged { old, new ->
					old.currentIndex == new.currentIndex &&
						old.queue == new.queue &&
						old.isPaused == new.isPaused &&
						old.repeatMode == new.repeatMode &&
						old.isShuffleEnabled == new.isShuffleEnabled &&
						old.playbackSpeed == new.playbackSpeed
				}
				.collect { state ->
					stateRepository.setState(state)
				}
		}

		// Position only: at most every 10 seconds while the progress moves.
		viewModelScope.launch {
			localUiState
				.sample(10.seconds)
				.map { it.progress }
				.distinctUntilChanged()
				.collect { progress ->
					stateRepository.setPosition(progress)
				}
		}
	}
}
