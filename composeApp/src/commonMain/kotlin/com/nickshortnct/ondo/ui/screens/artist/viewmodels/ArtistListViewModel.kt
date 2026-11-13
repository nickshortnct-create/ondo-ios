package com.nickshortnct.ondo.ui.screens.artist.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.toBitmask
import com.nickshortnct.ondo.domain.models.toDomainFilters
import com.nickshortnct.ondo.domain.repositories.ArtistRepository
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.core.UiState

class ArtistListViewModel(
	initialListType: DomainArtistListType = DomainArtistListType.AlphabeticalByName,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: ArtistRepository,
	private val albumDao: AlbumDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val artistsState: StateFlow<UiState<ImmutableList<DomainArtist>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainArtist>>>(UiState.Loading())

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedArtist: StateFlow<DomainArtist?>
		field = MutableStateFlow(null)

	val selectedArtistAlbums: StateFlow<ImmutableList<DomainAlbum>?>
		field = MutableStateFlow(null)

	val listType: StateFlow<DomainArtistListType>
		field = MutableStateFlow(initialListType)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.artistFilters.toDomainFilters()
		)

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshArtists(false) }
		}
	}

	private var refreshJob: Job? = null

	/** One collector at a time: a new refresh replaces the previous one instead of stacking another. */
	fun refreshArtists(fullRefresh: Boolean) {
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getArtistsFlow(fullRefresh, listType.value, selectedFilters.value)
				.collect {
					artistsState.value = it
				}
		}
	}

	fun selectArtist(artist: DomainArtist) {
		viewModelScope.launch {
			selectedArtist.value = artist
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			selectedArtistAlbums.value = artistAlbums.map { it.toDomainModel() }.toImmutableList()
			starred.value = repository.isArtistStarred(artist)
		}
	}

	fun clearSelection() {
		selectedArtist.value = null
	}

	fun starArtist(isStarred: Boolean) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			runCatchingCancellable {
				if (isStarred) {
					repository.starArtist(artist)
				} else {
					repository.unstarArtist(artist)
				}
				starred.value = isStarred
			}
		}
	}

	fun addArtistAlbumsToQueue(player: MediaPlayerViewModel) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			player.addToQueue(artistAlbums.flatMap { it.toDomainModel().songs })
		}
	}

	fun playArtistAlbumsNext(player: MediaPlayerViewModel) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			player.playNext(artistAlbums.flatMap { it.toDomainModel().songs })
		}
	}

	fun setListType(newListType: DomainArtistListType) {
		listType.value = newListType
		refreshArtists(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.artistFilters = newFilters.toBitmask()
		refreshArtists(false)
	}

	fun clearError() {
		artistsState.value = UiState.Success(artistsState.value.data ?: persistentListOf())
	}
}
