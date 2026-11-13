package com.nickshortnct.ondo.ui.screens.artist.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_deleted_download
import ondo.composeapp.generated.resources.notice_download_started
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.domain.repositories.AlbumRepository
import com.nickshortnct.ondo.domain.repositories.ArtistRepository
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.util.Logger

@Immutable
data class ArtistState(
	val artist: DomainArtist,
	val albums: List<DomainAlbum>,
	val topSongs: List<DomainSong>,
	val similarArtists: List<DomainArtist> = emptyList(),
	// Full discography (album/EP vs. single, by Lidarr's albumType) pulled from
	// Lidarr's metadata backend -- separate from `albums` above, which is only
	// what's actually downloaded into the local library. See DiscoveryArtistScreen,
	// which builds the same kind of list for artists with no local library entry
	// at all; this merges the same data into the local artist's own profile.
	val discographyAlbums: List<MusicResult> = emptyList(),
	val discographySingles: List<MusicResult> = emptyList(),
	// Keyed by normalizeForMatch(title) -- lets a discography card show the real
	// local cover art and open the actual downloaded album instead of a plain
	// "on deck" label when the release is already in the library.
	val onDeckAlbums: Map<String, DomainAlbum> = emptyMap(),
	val discographyError: String? = null,
	// Distinguishes "fetch finished, Lidarr genuinely has nothing" from "fetch
	// hasn't run yet" -- both look like empty discographyAlbums/Singles, but only
	// the first should ever be shown as a dead end to the user.
	val discographyChecked: Boolean = false
)

class ArtistDetailViewModel(
	private val artistId: String,
	private val repository: DbRepository,
	private val artistRepository: ArtistRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val downloadManager: DownloadManager,
	private val snackBarManager: SnackBarManager,
	private val downloads: OndoDownloads,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	val artistState: StateFlow<UiState<ArtistState>>
		field = MutableStateFlow<UiState<ArtistState>>(UiState.Loading())

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedAlbum: StateFlow<DomainAlbum?>
		field = MutableStateFlow(null)

	val selectedAlbumIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedAlbumRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val isOnline = connectivityManager.isOnline

	val downloadsById = downloadManager.downloadsById

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val scrollState = ScrollState(initial = 0)
	private var artistLoadJob: Job? = null

	init {
		loadArtistData()
	}

	private fun loadArtistData() {
		artistLoadJob?.cancel()
		artistLoadJob = viewModelScope.launch {
			try {
				val artistEntity = artistDao.getArtistById(artistId)
					?: throw Exception("Artist not found in database")
				val domainArtist = artistEntity.toDomainModel()

				val libraryAlbums = combine(
					albumDao.getAlbumsByArtist(artistId),
					albumDao.getAlbumsByArtistName(domainArtist.name)
				) { byId, byName -> byId.ifEmpty { byName } }
				val albumsWithSongs = libraryAlbums.firstOrNull() ?: emptyList()

				// Servers model singles as albums.  Artist discographies should show
				// only actual multi-track albums; those singles still contribute to
				// the artist's top-song list below.
				val domainAlbums = albumsWithSongs.map { it.toDomainModel() }
					.filter { it.songs.size > 1 || it.songCount > 1 }

				val domainSongs = albumsWithSongs.flatMap { it.songs }
					.map { it.toDomainModel() }
					.sortedByDescending { it.playCount }
					.take(12)

				val initialSimilarArtists = domainArtist.similarArtistIds.mapNotNull { id ->
					artistDao.getArtistById(id)?.toDomainModel()
				}

				val onDeckAlbums = albumsWithSongs.associate { normalizeForMatch(it.album.name) to it.toDomainModel() }

				starred.value = artistRepository.isArtistStarred(domainArtist)

				artistState.value = UiState.Success(
					ArtistState(
						artist = domainArtist,
						albums = domainAlbums,
						topSongs = domainSongs,
						similarArtists = initialSimilarArtists,
						onDeckAlbums = onDeckAlbums
					)
				)

				// Imported albums and newly hydrated track lists must update a
				// profile that's still open, including its download/library badges.
				launch {
					libraryAlbums.collect { albums ->
						val currentState = artistState.value.data ?: return@collect
						artistState.value = UiState.Success(currentState.copy(
							albums = albums.map { it.toDomainModel() }
								.filter { it.songs.size > 1 || it.songCount > 1 },
							topSongs = albums.flatMap { it.songs }
								.map { it.toDomainModel() }
								.sortedByDescending { it.playCount }
								.take(12),
							onDeckAlbums = albums.associate { normalizeForMatch(it.album.name) to it.toDomainModel() }
						))
					}
				}

				try {
					val (albumsAndEps, singles) = downloads.discography(domainArtist.musicBrainzId.orEmpty(), domainArtist.name)
					val currentState = (artistState.value as? UiState.Success)?.data
					if (currentState != null) {
						artistState.value = UiState.Success(
							currentState.copy(discographyAlbums = albumsAndEps, discographySingles = singles, discographyChecked = true)
						)
					}
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					val currentState = (artistState.value as? UiState.Success)?.data
					if (currentState != null) {
						artistState.value = UiState.Success(currentState.copy(discographyError = e.message, discographyChecked = true))
					}
				}

				repository.fetchArtistMetadata(artistId)
					.onSuccess { updatedArtist ->
						val currentState = (artistState.value as? UiState.Success)?.data
						if (currentState != null) {

							val updatedSimilarArtists =
								updatedArtist.similarArtistIds.mapNotNull { id ->
									artistDao.getArtistById(id)?.toDomainModel()
								}

							artistState.value = UiState.Success(
								currentState.copy(
									artist = updatedArtist,
									similarArtists = updatedSimilarArtists
								)
							)
						}
					}
					.onFailure { error ->
						Logger.e("ArtistDetailViewModel", "Failed to fetch artist metadata", error)
					}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				artistState.value = UiState.Error(e)
			}
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			selectedSongIsStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatchingCancellable {
				selectedAlbumRating.value = rating
				albumRepository.rateAlbum(selection, rating)
			}
		}
	}

	fun clearAlbumSelection() {
		selectedAlbum.value = null
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				selectedSongIsStarred.value = true
				songRepository.starSong(selection)
				updateSongStar(selection.id, true)
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				selectedSongIsStarred.value = false
				songRepository.unstarSong(selection)
				updateSongStar(selection.id, false)
			}
		}
	}

	private fun updateSongStar(id: String, starred: Boolean) {
		val at = if (starred) Clock.System.now() else null
		selectedSong.value = selectedSong.value?.let { if (it.id == id) it.copy(starredAt = at) else it }
		val current = artistState.value.data ?: return
		artistState.value = UiState.Success(current.copy(
			topSongs = current.topSongs.map { if (it.id == id) it.copy(starredAt = at) else it }
		))
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				selectedSongRating.value = rating
				songRepository.rateSong(selection, rating)
			}
		}
	}

	fun starArtist(isStarred: Boolean) {
		val artist = (artistState.value as? UiState.Success)?.data?.artist ?: return
		viewModelScope.launch {
			runCatchingCancellable {
				if (isStarred) {
					artistRepository.starArtist(artist)
				} else {
					artistRepository.unstarArtist(artist)
				}
				starred.value = isStarred
			}
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatchingCancellable {
				if (starred) {
					albumRepository.starAlbum(selection)
				} else {
					albumRepository.unstarAlbum(selection)
				}
				selectedAlbumIsStarred.value = starred
			}
		}
	}

	fun playArtistAlbums(player: MediaPlayerViewModel) {
		(artistState.value as? UiState.Success)?.data?.let { state ->
			player.clearQueue()
			state.albums.forEach { album ->
				player.addToQueue(album, notify = false)
			}
			player.playAt(0)
		}
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
		snackBarManager.notify(Res.string.notice_download_started)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
		snackBarManager.notify(Res.string.notice_deleted_download)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	private val sharedDownloadStatus: StateFlow<DownloadStatus> = artistState
		.map { it.data?.albums.orEmpty().flatMap { album -> album.songs.map { song -> song.id } } }
		.distinctUntilChanged()
		.flatMapLatest { downloadManager.getCollectionDownloadStatus(it) }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadStatus.NOT_DOWNLOADED)

	fun collectionDownloadStatus(): StateFlow<DownloadStatus> = sharedDownloadStatus

}
