package com.nickshortnct.ondo.ui.screens.playlist.viewmodels

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_cover_device_only
import ondo.composeapp.generated.resources.notice_playlist_updated
import com.nickshortnct.ondo.data.subsonic.NavidromePlaylistImages
import com.nickshortnct.ondo.domain.manager.PlaylistCoverManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.ondo.displayComment
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.util.Logger

/** Whether a playlist can be renamed, described and given a cover from this account. */
fun DomainPlaylist.isEditableBy(username: String): Boolean =
	readOnly != true && (owner.isBlank() || owner.equals(username, ignoreCase = true)) &&
		// Weekly mixes are rewritten every Monday; edits would be lost.
		com.nickshortnct.ondo.ondo.parseMixTag(comment) == null

sealed interface CoverChange {
	data object None : CoverChange
	class Replace(val jpeg: ByteArray) : CoverChange
	data object Remove : CoverChange
}

class PlaylistEditDialogViewModel(
	private val playlist: DomainPlaylist,
	private val sessionManager: SessionManager,
	private val dbRepository: DbRepository,
	private val playlistCoverManager: PlaylistCoverManager,
	private val playlistImages: NavidromePlaylistImages,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	val name = TextFieldState(playlist.name)
	val description = TextFieldState(playlist.displayComment.orEmpty())

	val isPublic: StateFlow<Boolean>
		field = MutableStateFlow(playlist.public == true)

	val coverChange: StateFlow<CoverChange>
		field = MutableStateFlow<CoverChange>(CoverChange.None)

	val saveState: StateFlow<UiState<Nothing?>>
		field = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))

	private val _events = Channel<Unit>()
	/** Emits once the changes are saved and the dialog should close. */
	val saved = _events.receiveAsFlow()

	fun setPublic(value: Boolean) { isPublic.value = value }
	fun replaceCover(jpeg: ByteArray) { coverChange.value = CoverChange.Replace(jpeg) }
	fun removeCover() { coverChange.value = CoverChange.Remove }

	fun save() {
		if (saveState.value is UiState.Loading) return
		val newName = name.text.toString().trim()
		if (newName.isBlank()) return
		saveState.value = UiState.Loading()
		viewModelScope.launch {
			try {
				val newDescription = description.text.toString().trim()
				val oldDescription = playlist.displayComment.orEmpty()
				val nameChanged = newName != playlist.name
				val descriptionChanged = newDescription != oldDescription
				val publicChanged = isPublic.value != (playlist.public == true)
				if (nameChanged || descriptionChanged || publicChanged) {
					sessionManager.api.updatePlaylist(
						playlist.id,
						newName.takeIf { nameChanged },
						// Navidrome ignores an empty comment, so a single space is how a description is cleared.
						newDescription.ifEmpty { " " }.takeIf { descriptionChanged },
						isPublic.value.takeIf { publicChanged }
					)
				}

				var coverProblem: String? = null
				when (val change = coverChange.value) {
					is CoverChange.Replace -> {
						// Shown on this device straight away; the upload makes it the cover everywhere else.
						playlistCoverManager.setCustomCover(playlist.id, change.jpeg)
						coverProblem = serverCover { playlistImages.upload(playlist.id, change.jpeg) }
					}
					CoverChange.Remove -> {
						playlistCoverManager.clearCustomCover(playlist.id)
						coverProblem = serverCover { playlistImages.remove(playlist.id) }
					}
					CoverChange.None -> Unit
				}

				dbRepository.syncPlaylists().getOrThrow()
				saveState.value = UiState.Success(null)
				if (coverProblem != null) {
					snackBarManager.notify(Res.string.notice_cover_device_only, coverProblem)
				} else {
					snackBarManager.notify(Res.string.notice_playlist_updated, newName)
				}
				_events.send(Unit)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				saveState.value = UiState.Error(e)
			}
		}
	}

	/** A cover the server won't take still applies on this device, so it's reported rather than fatal. */
	private suspend fun serverCover(block: suspend () -> Unit): String? = try {
		block()
		null
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		Logger.w("PlaylistEdit", "Couldn't change the cover on the server", e)
		e.message ?: e::class.simpleName
	}
}
