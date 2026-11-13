package com.nickshortnct.ondo.ui.screens.song.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import kotlinx.coroutines.Job
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.domain.models.toBitmask
import com.nickshortnct.ondo.domain.models.toDomainFilters
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.ui.core.UiState

class SongListViewModel(
	initialListType: DomainSongListType = DomainSongListType.FrequentlyPlayed,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: SongRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	val songsState: StateFlow<UiState<ImmutableList<DomainSong>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainSong>>>(UiState.Loading())

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = persistentListOf()
		)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedSorting: StateFlow<DomainSongListType>
		field = MutableStateFlow(initialListType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.songFilters.toDomainFilters()
		)

	val isOnline = connectivityManager.isOnline

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshSongs(false) }
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			starred.value = repository.isSongStarred(song)
			selectedSongRating.value = repository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	private var refreshJob: Job? = null

	/** One collector at a time: a new refresh replaces the previous one instead of stacking another. */
	fun refreshSongs(fullRefresh: Boolean) {
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getSongsFlow(
				fullRefresh,
				selectedSorting.value,
				selectedReversed.value,
				selectedFilters.value
			).collect {
				songsState.value = it
			}
		}
	}

	fun starSong(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				if (isStarred) {
					repository.starSong(selection)
				} else {
					repository.unstarSong(selection)
				}
				starred.value = isStarred
				refreshSongs(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				repository.rateSong(selection, rating)
				selectedSongRating.value = rating
			}
		}
	}

	fun setSorting(sorting: DomainSongListType) {
		selectedSorting.value = sorting
		refreshSongs(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshSongs(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.songFilters = newFilters.toBitmask()
		refreshSongs(false)
	}

	fun clearError() {
		songsState.value = UiState.Success(songsState.value.data ?: persistentListOf())
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
	}
}
