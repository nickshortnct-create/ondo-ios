package com.nickshortnct.ondo.ui.screens.artist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.artistIdentityMatches
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ui.core.UiState

data class DiscoveryArtistState(
	val redirectToLocalArtistId: String? = null,
	val albums: List<MusicResult> = emptyList(),
	val singles: List<MusicResult> = emptyList(),
	val onDeckAlbums: Map<String, DomainAlbum> = emptyMap(),
	val tracksFound: List<MusicResult> = emptyList(),
	val discographyError: String? = null,
	val discographyChecked: Boolean = false,
	val tracksChecked: Boolean = false
)

// Profile for an artist a decknas downloader found that isn't in the local
// library yet -- no local artistId to hand ArtistDetailScreen, so this builds
// a discography from Lidarr's metadata lookup instead (works even if the
// artist has never been added to Lidarr's own library).
class DiscoveryArtistViewModel(
	private val name: String,
	private val musicBrainzId: String,
	private val skipLocalRedirect: Boolean,
	private val downloads: OndoDownloads,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao
) : ViewModel() {
	val state: StateFlow<UiState<DiscoveryArtistState>>
		field = MutableStateFlow<UiState<DiscoveryArtistState>>(UiState.Loading())
	private var currentData = DiscoveryArtistState()

	private fun updateData(publish: Boolean = false, transform: (DiscoveryArtistState) -> DiscoveryArtistState) {
		// A completed import may redirect while either remote lookup is running.
		if (currentData.redirectToLocalArtistId != null) return
		currentData = transform(currentData)
		if (publish || state.value is UiState.Success) state.value = UiState.Success(currentData)
	}

	init {
		// A sync can finish *after* this screen first loads -- e.g. the user
		// downloads something for this exact artist and comes straight back
		// here instead of re-entering from search. A one-shot check on init
		// would stay stuck on the bare discovery layout forever in that case,
		// so this keeps watching the local library for as long as the screen
		// is alive and redirects the moment a match actually shows up.
		if (!skipLocalRedirect) {
			viewModelScope.launch {
				artistDao.getAllArtists().collect { artists ->
					// Prefer an exact ID over an earlier same-name row with no ID.
					val match = artists.firstOrNull {
						musicBrainzId.isNotBlank() && it.musicBrainzId?.equals(musicBrainzId, ignoreCase = true) == true
					} ?: artists.firstOrNull { artistIdentityMatches(it.name, it.musicBrainzId, name, musicBrainzId) }
					if (match != null) {
						updateData(publish = true) { it.copy(redirectToLocalArtistId = match.artistId) }
					}
				}
			}
		}
		viewModelScope.launch {
			albumDao.getAlbumsByArtistName(name).collect { albums ->
				updateData { data ->
					data.copy(onDeckAlbums = albums.associate { normalizeForMatch(it.album.name) to it.toDomainModel() })
				}
			}
		}
		load()
	}

	private fun load() {
		viewModelScope.launch {
			if (currentData.redirectToLocalArtistId != null) return@launch
			try {
				val (albums, singles) = downloads.discography(musicBrainzId, name)
				updateData(publish = true) { it.copy(albums = albums, singles = singles, discographyChecked = true) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				updateData(publish = true) { it.copy(discographyError = e.message ?: "Discography unavailable", discographyChecked = true) }
			}
		}
		// Supplemental track lookup cannot hold a ready discography behind its
		// network timeout, and tracks can also appear while metadata is loading.
		viewModelScope.launch {
			if (currentData.redirectToLocalArtistId != null) return@launch
			try {
				val tracks = downloads.search("SoulSync", name).filter { it.kind == "track" && com.nickshortnct.ondo.domain.models.artistIdentityMatches(it.artist, null, name, musicBrainzId) }
					.distinctBy { listOf(it.service, it.id, it.title) }
				updateData(publish = true) { it.copy(tracksFound = tracks, tracksChecked = true) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				updateData(publish = true) { it.copy(tracksChecked = true) }
			}
		}
	}
}
