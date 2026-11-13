package com.nickshortnct.ondo.ui.screens.genre.viewmodels

import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainGenre
import com.nickshortnct.ondo.domain.repositories.GenreRepository
import com.nickshortnct.ondo.ui.core.UiState

class GenreListViewModel(
	private val repository: GenreRepository,
	private val sessionManager: SessionManager
) : ViewModel() {
	val genresState: StateFlow<UiState<ImmutableList<DomainGenre>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainGenre>>>(UiState.Loading())

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshGenres(false) }
		}
	}

	private var refreshJob: Job? = null

	/** One collector at a time: a new refresh replaces the previous one instead of stacking another. */
	fun refreshGenres(fullRefresh: Boolean) {
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getGenresFlow(fullRefresh).collect {
				genresState.value = it
			}
		}
	}

	fun clearError() {
		genresState.value = UiState.Success(genresState.value.data ?: persistentListOf())
	}
}
