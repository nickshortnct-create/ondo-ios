package com.nickshortnct.ondo.ui.screens.playlist.viewmodels

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_created_playlist
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.ui.core.UiState

class PlaylistCreateDialogViewModel(
	private val songs: List<DomainSong>,
	private val playlistDao: PlaylistDao,
	private val playlistSyncFetcher: PlaylistSyncFetcher,
	private val dbRepository: DbRepository,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	val creationState: StateFlow<UiState<Nothing?>>
		field = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	val name = TextFieldState()

	private var createdPlaylistId: String? = null

	fun create() {
		if (creationState.value is UiState.Loading) return
		creationState.value = UiState.Loading()
		viewModelScope.launch {
			creationState.value = UiState.Loading()
			try {
				val ids = songs.map { it.id }.distinct()
				val id = createdPlaylistId ?: sessionManager.api.createPlaylist(
					name = name.text.toString(), songIds = ids.take(PLAYLIST_BATCH_SIZE)
				).also {
					createdPlaylistId = it.id
					playlistDao.insertPlaylist(it.toEntity())
				}.id
				val existing = playlistSyncFetcher.fetch(id).entries.mapTo(mutableSetOf()) { it.id }
				appendPlaylistBatches(ids.filterNot(existing::contains)) { batch ->
					sessionManager.api.updatePlaylist(id, songIdsToAdd = batch)
				}
				dbRepository.syncPlaylistSongs(id, tolerateDeserializeError = false).getOrThrow()

				_events.send(
					Event.Dismiss(
						playlistDao.getPlaylistById(id)!!.toDomainModel()
					)
				)
				creationState.value = UiState.Success(null)
				snackBarManager.notify(Res.string.notice_created_playlist, name.text.toString())
			} catch (e: Exception) {
				creationState.value = UiState.Error(e)
			}
		}
	}

	sealed class Event {
		data class Dismiss(val playlist: DomainPlaylist) : Event()
	}
}
