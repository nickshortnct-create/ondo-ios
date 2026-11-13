package com.nickshortnct.ondo.ui.screens.playlist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_added_to_multiple_playlists
import ondo.composeapp.generated.resources.notice_added_to_playlist
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.ondo.PlaylistPlayStats
import com.nickshortnct.ondo.ondo.parseMixTag
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.ui.core.UiState

class PlaylistUpdateDialogViewModel(
	private val songs: List<DomainSong>,
	private val playlistToExclude: String?,
	private val playlistSyncFetcher: PlaylistSyncFetcher,
	private val dbRepository: DbRepository,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager,
	private val playlistPlayStats: PlaylistPlayStats
) : ViewModel() {
	val playlistsState: StateFlow<UiState<List<PlaylistEntity>>>
		field = MutableStateFlow<UiState<List<PlaylistEntity>>>(UiState.Loading())

	val confirmState: StateFlow<UiState<Nothing?>>
		field = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))

	val selectedPlaylists: StateFlow<Set<PlaylistEntity>>
		field = MutableStateFlow<Set<PlaylistEntity>>(emptySet())

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	init {
		refreshResults()
	}

	fun refreshResults() {
		viewModelScope.launch {
			selectedPlaylists.value = emptySet()
			playlistsState.value = UiState.Loading()
			try {
				val results = playlistSyncFetcher.fetchAllPlaylists()
				val editable = results.filter {
					it.playlistId != playlistToExclude && it.readOnly != true &&
						(it.owner.isBlank() || it.owner.equals(sessionManager.username, ignoreCase = true)) &&
						// Weekly mixes are rewritten every Monday, so songs added to them would vanish.
						parseMixTag(it.comment) == null
				}
				// The playlists played most often come first.
				playlistsState.value = UiState.Success(playlistPlayStats.sorted(editable))
			} catch (e: Exception) {
				playlistsState.value = UiState.Error(e)
			}
		}
	}

	fun togglePlaylistSelection(playlist: PlaylistEntity) {
		selectedPlaylists.value = if (playlist in selectedPlaylists.value) {
			selectedPlaylists.value - playlist
		} else {
			selectedPlaylists.value + playlist
		}
	}

	fun confirm() {
		if (confirmState.value is UiState.Loading) return
		if (songs.isEmpty()) {
			confirmState.value = UiState.Error(IllegalArgumentException("Choose at least one song to add."))
			return
		}
		confirmState.value = UiState.Loading()
		viewModelScope.launch {
			confirmState.value = UiState.Loading()
			try {
				val selected = selectedPlaylists.value
				check(selected.isNotEmpty()) { "Choose at least one playlist." }
				val songIdsToAdd = songs.map { it.id }.distinct()
				selected.forEach { playlist ->
					val existingSongIds = playlistSyncFetcher.fetch(playlist.playlistId)
						.entries.mapTo(mutableSetOf()) { it.id }
					val missingSongIds = songIdsToAdd.filterNot(existingSongIds::contains)
					if (missingSongIds.isNotEmpty()) {
						appendPlaylistBatches(missingSongIds) { batch ->
							sessionManager.api.updatePlaylist(playlist.playlistId, songIdsToAdd = batch)
						}
					}

					val updatedSongIds = dbRepository.syncPlaylistSongs(
						playlist.playlistId,
						tolerateDeserializeError = false
					).getOrThrow()
					val songsNotAdded = songIdsToAdd.filterNot(updatedSongIds::contains)
					check(songsNotAdded.isEmpty()) {
						"The server did not add all selected songs to '${playlist.name}'."
					}
				}
				confirmState.value = UiState.Success(null)
				if (selected.size == 1) {
					snackBarManager.notify(
						Res.string.notice_added_to_playlist,
						selected.first().name
					)
				} else if (selected.size > 1) {
					snackBarManager.notify(Res.string.notice_added_to_multiple_playlists)
				}
				_events.send(Event.Dismiss)
			} catch (e: Exception) {
				confirmState.value = UiState.Error(e)
			}
		}
	}

	enum class Event {
		Dismiss
	}
}
