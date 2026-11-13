package com.nickshortnct.ondo.ui.screens.lyrics.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.lyrics.LyricsResult
import com.nickshortnct.ondo.domain.repositories.LyricsRepository
import com.nickshortnct.ondo.ui.core.UiState

class LyricsScreenViewModel(
	private var song: DomainSong?,
	private val repository: LyricsRepository
) : ViewModel() {
	val lyricsState: StateFlow<UiState<LyricsResult?>>
		field = MutableStateFlow<UiState<LyricsResult?>>(UiState.Loading())

	private var fetchJob: Job? = null

	init {
		refreshResults()
	}

	fun setSong(value: DomainSong?) {
		if (song?.id == value?.id) return
		song = value
		refreshResults()
	}

	fun refreshResults() {
		fetchJob?.cancel()
		val requestedSong = song
		fetchJob = viewModelScope.launch {
			if (requestedSong == null) {
				lyricsState.value = UiState.Success(null)
				return@launch
			}
			lyricsState.value = UiState.Loading()
			try {
				lyricsState.value = UiState.Success(
					repository.fetchLyrics(requestedSong)
				)
			} catch (e: CancellationException) { throw e
			} catch (e: Exception) {
				lyricsState.value = UiState.Error(e)
			}
		}
	}
}
