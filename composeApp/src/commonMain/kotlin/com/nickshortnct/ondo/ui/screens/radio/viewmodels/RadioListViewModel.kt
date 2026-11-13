package com.nickshortnct.ondo.ui.screens.radio.viewmodels

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
import com.nickshortnct.ondo.domain.models.DomainRadio
import com.nickshortnct.ondo.domain.repositories.RadioRepository
import com.nickshortnct.ondo.ui.core.UiState

class RadioListViewModel(
	private val repository: RadioRepository,
	private val sessionManager: SessionManager
) : ViewModel() {
	val radiosState: StateFlow<UiState<ImmutableList<DomainRadio>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainRadio>>>(UiState.Loading())

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshRadios(false) }
		}
	}

	private var refreshJob: Job? = null

	/** One collector at a time: a new refresh replaces the previous one instead of stacking another. */
	fun refreshRadios(fullRefresh: Boolean) {
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getRadiosFlow(fullRefresh).collect {
				radiosState.value = it
			}
		}
	}

	fun clearError() {
		radiosState.value = UiState.Success(radiosState.value.data ?: persistentListOf())
	}
}
