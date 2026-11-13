package com.nickshortnct.ondo.ui.screens.album.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.toBitmask
import com.nickshortnct.ondo.domain.models.toDomainFilters
import com.nickshortnct.ondo.domain.repositories.AlbumRepository
import com.nickshortnct.ondo.ui.core.UiState

class AlbumListViewModel(
	initialListType: DomainAlbumListType = DomainAlbumListType.AlphabeticalByArtist,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: AlbumRepository,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val albumsState: StateFlow<UiState<ImmutableList<DomainAlbum>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainAlbum>>>(UiState.Loading())

	val selectedAlbum: StateFlow<DomainAlbum?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val rating: StateFlow<Int>
		field = MutableStateFlow(0)

	val listType: StateFlow<DomainAlbumListType>
		field = MutableStateFlow(initialListType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.albumFilters.toDomainFilters()
		)

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshAlbums(false) }
		}
	}

	private var refreshJob: Job? = null

	/** One collector at a time: a new refresh replaces the previous one instead of stacking another. */
	fun refreshAlbums(fullRefresh: Boolean) {
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getAlbumsFlow(
				fullRefresh,
				listType.value,
				selectedReversed.value,
				selectedFilters.value
			).collect {
				albumsState.value = it
			}
		}
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			starred.value = repository.isAlbumStarred(album)
			rating.value = repository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		selectedAlbum.value = null
	}

	fun starAlbum(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatchingCancellable {
				if (isStarred) {
					repository.starAlbum(selection)
				} else {
					repository.unstarAlbum(selection)
				}
				starred.value = isStarred
			}
		}
	}

	fun setRating(newRating: Int) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatchingCancellable {
				rating.value = newRating
				repository.rateAlbum(selection, newRating)
			}
		}
	}

	fun setListType(newListType: DomainAlbumListType) {
		listType.value = newListType
		refreshAlbums(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshAlbums(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.albumFilters = newFilters.toBitmask()
		refreshAlbums(false)
	}

	fun clearError() {
		albumsState.value = UiState.Success(albumsState.value.data ?: persistentListOf())
	}
}
