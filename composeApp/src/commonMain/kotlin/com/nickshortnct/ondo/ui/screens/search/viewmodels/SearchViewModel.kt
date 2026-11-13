package com.nickshortnct.ondo.ui.screens.search.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.UnifiedSearchResult
import com.nickshortnct.ondo.domain.models.normalizeSearchQuery
import com.nickshortnct.ondo.domain.models.recentSearchesWith
import com.nickshortnct.ondo.domain.repositories.UnifiedSearchRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.ui.core.UiState

class SearchViewModel(
	private val repository: UnifiedSearchRepository,
	private val songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val searchState: StateFlow<UiState<List<UnifiedSearchResult>>>
		field = MutableStateFlow<UiState<List<UnifiedSearchResult>>>(UiState.Success(emptyList()))

	val searchFailures: StateFlow<List<String>>
		field = MutableStateFlow<List<String>>(emptyList())

	val searchHistory: StateFlow<List<String>>
		field = MutableStateFlow<List<String>>(preferenceManager.getSearchHistory())

	val isSearching: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val searchQuery = TextFieldState()

	val isOnline = connectivityManager.isOnline
	val downloadedSongs = downloadManager.downloadedSongs

	val gridState = LazyGridState()

	private var searchJob: Job? = null
	private var activeQuery: String? = null
	private var searchActive = true
	private var generation = 0
	private var selectionJob: Job? = null

	init {
		viewModelScope.launch {
			snapshotFlow { normalizeSearchQuery(searchQuery.text.toString()) }
				.distinctUntilChanged()
				.collect { query ->
					if (searchActive && query != activeQuery) startSearch(query, debounce = true)
				}
		}
		viewModelScope.launch {
			repository.libraryRevision.drop(1).collect {
				if (isOnline.value && searchQuery.text.isNotBlank()) retrySearch()
			}
		}
		viewModelScope.launch {
			isOnline.drop(1).collect {
				if (searchQuery.text.isNotBlank()) retrySearch()
			}
		}
	}

	private fun startSearch(query: String, debounce: Boolean, keepResults: Boolean = false) {
		val cleaned = normalizeSearchQuery(query)
		val refreshingSameQuery = keepResults && activeQuery == cleaned
		val requestGeneration = ++generation
		activeQuery = cleaned
		// Cancellation happens on the keystroke, before waiting for the next query.
		searchJob?.cancel()
		searchFailures.value = emptyList()
		isSearching.value = cleaned.isNotEmpty()
		if (cleaned.isEmpty()) {
			searchState.value = UiState.Success(emptyList())
			return
		}
		if (!refreshingSameQuery) searchState.value = UiState.Loading(data = searchState.value.data)
		searchJob = viewModelScope.launch {
			try {
				if (debounce) delay(250)
				repository.searchFlow(cleaned).collect { outcome ->
					// Guard even if a transport finishes between the edit and snapshotFlow.
					if (requestGeneration == generation &&
						normalizeSearchQuery(searchQuery.text.toString()) == cleaned
					) {
						val retainVisibleResults = outcome.isSearching && outcome.results.isEmpty() &&
							!searchState.value.data.isNullOrEmpty()
						if (!retainVisibleResults) searchState.value = UiState.Success(outcome.results)
						searchFailures.value = outcome.failures
						isSearching.value = outcome.isSearching
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				if (requestGeneration == generation) searchState.value = UiState.Error(e)
			} finally {
				if (requestGeneration == generation) isSearching.value = false
			}
		}
	}

	fun submitSearch(query: String = searchQuery.text.toString()) {
		val cleaned = normalizeSearchQuery(query)
		if (searchQuery.text.toString() != cleaned) {
			searchQuery.edit { replace(0, length, cleaned) }
		}
		addToSearchHistory(cleaned)
		if (searchActive) startSearch(cleaned, debounce = false, keepResults = activeQuery == cleaned)
	}

	fun retrySearch() {
		if (searchActive) startSearch(searchQuery.text.toString(), debounce = false, keepResults = true)
	}

	fun setSearchActive(active: Boolean) {
		if (searchActive == active) return
		searchActive = active
		if (!active) {
			generation++
			searchJob?.cancel()
			isSearching.value = false
		} else {
			val cleaned = normalizeSearchQuery(searchQuery.text.toString())
			startSearch(cleaned, debounce = false, keepResults = activeQuery == cleaned)
		}
	}

	fun addToSearchHistory(query: String) {
		searchHistory.value = recentSearchesWith(searchHistory.value, query)
		preferenceManager.setSearchHistory(searchHistory.value)
	}

	fun removeFromSearchHistory(query: String) {
		searchHistory.value = searchHistory.value.filterNot { it.equals(query, ignoreCase = true) }
		preferenceManager.setSearchHistory(searchHistory.value)
	}

	fun clearSearchHistory() {
		searchHistory.value = emptyList()
		preferenceManager.setSearchHistory(emptyList())
	}

	fun selectSong(song: DomainSong) {
		selectionJob?.cancel()
		selectedSong.value = song
		selectedSongIsStarred.value = false
		selectedSongRating.value = 0
		selectionJob = viewModelScope.launch {
			val starred = songRepository.isSongStarred(song)
			val rating = songRepository.getSongRating(song)
			if (selectedSong.value?.id == song.id) {
				selectedSongIsStarred.value = starred
				selectedSongRating.value = rating
			}
		}
	}

	fun starSelectedSong(starred: Boolean) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				if (starred) {
					songRepository.starSong(selection)
				} else {
					songRepository.unstarSong(selection)
				}
				if (selectedSong.value?.id == selection.id) selectedSongIsStarred.value = starred
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				songRepository.rateSong(selection, rating)
				if (selectedSong.value?.id == selection.id) selectedSongRating.value = rating
			}
		}
	}

	fun clearSelectedSong() {
		selectionJob?.cancel()
		selectedSong.value = null
	}
}
