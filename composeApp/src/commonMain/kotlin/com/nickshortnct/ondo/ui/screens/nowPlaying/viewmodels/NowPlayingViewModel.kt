package com.nickshortnct.ondo.ui.screens.nowPlaying.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.domain.repositories.SongStarEvents
import com.nickshortnct.ondo.shared.MediaPlayerViewModel

class NowPlayingViewModel(
	private val player: MediaPlayerViewModel,
	private val songRepository: SongRepository
) : ViewModel(), KoinComponent {
	val songIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val songRating: StateFlow<Int>
		field = MutableStateFlow(0)

	init {
		viewModelScope.launch {
			SongStarEvents.events.collect { (songId, starred) ->
				if (player.uiState.value.currentSong?.id == songId) songIsStarred.value = starred
			}
		}
		viewModelScope.launch {
			player.uiState.map { it.currentSong }.distinctUntilChanged { old, new -> old?.id == new?.id }.collect { currentSong ->
				currentSong?.let { song ->
					songIsStarred.value = songRepository.isSongStarred(song)
					songRating.value = songRepository.getSongRating(song)
				}
			}
		}
	}

	fun starSong(starred: Boolean) {
		viewModelScope.launch {
			runCatchingCancellable {
				player.uiState.value.currentSong?.let { song ->
					songIsStarred.value = starred
					if (starred) {
						songRepository.starSong(song)
					} else {
						songRepository.unstarSong(song)
					}
				}
			}
		}
	}

	fun rateSong(rating: Int) {
		viewModelScope.launch {
			runCatchingCancellable {
				player.uiState.value.currentSong?.let { song ->
					songRating.value = rating
					songRepository.rateSong(song, rating)
				}
			}
		}
	}
}
