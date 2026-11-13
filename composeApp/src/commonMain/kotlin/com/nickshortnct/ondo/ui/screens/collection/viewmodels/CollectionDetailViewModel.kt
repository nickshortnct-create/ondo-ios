package com.nickshortnct.ondo.ui.screens.collection.viewmodels

import com.nickshortnct.ondo.util.runCatchingCancellable
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_deleted_download
import ondo.composeapp.generated.resources.notice_download_started
import ondo.composeapp.generated.resources.notice_playlist_changed
import ondo.composeapp.generated.resources.notice_removed_from_playlist
import ondo.composeapp.generated.resources.notice_saved_mix_copy
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.ondo.displayComment
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PLAYLIST_BATCH_SIZE
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.appendPlaylistBatches
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.manager.PlaylistCoverManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumInfo
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.needsRemoteSongSync
import com.nickshortnct.ondo.domain.repositories.AlbumRepository
import com.nickshortnct.ondo.domain.repositories.CollectionRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.util.Logger

class CollectionDetailViewModel(
	private val collectionId: String,
	private val repository: CollectionRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager,
	private val playlistCoverManager: PlaylistCoverManager,
	private val playlistSyncFetcher: PlaylistSyncFetcher,
	private val dbRepository: DbRepository,
	connectivityManager: ConnectivityManager
) : ViewModel() {

	// Starts empty; the cached copy is loaded off the main thread in init (a blocking DB read
	// here froze the UI while opening a collection).
	val collectionState: StateFlow<UiState<DomainSongCollection>>
		field = MutableStateFlow<UiState<DomainSongCollection>>(UiState.Loading())

	private var refreshJob: Job? = null

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val albumInfoState: StateFlow<UiState<DomainAlbumInfo>>
		field = MutableStateFlow<UiState<DomainAlbumInfo>>(UiState.Loading())

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

	val rating: StateFlow<Int>
		field = MutableStateFlow(0)

	val listState = LazyListState()

	val isOnline = connectivityManager.isOnline

	val downloadsById = downloadManager.downloadsById

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	// Derived from the collection state so it also works when the album wasn't cached at construction.
	@OptIn(ExperimentalCoroutinesApi::class)
	val otherAlbums: StateFlow<List<DomainAlbum>> = collectionState
		.map { (it.data as? DomainAlbum)?.let { album -> album.artistId to album.id } }
		.distinctUntilChanged()
		.flatMapLatest { ids ->
			ids?.let { (artistId, albumId) -> repository.getOtherAlbums(artistId, albumId) } ?: flowOf(emptyList())
		}
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	init {
		viewModelScope.launch {
			// Show whatever is cached right away, unless a refresh already delivered something.
			val cached = try {
				repository.getLocalData(collectionId)
			} catch (_: Exception) {
				null
			}
			if (cached != null && collectionState.value.data == null) {
				collectionState.value = if (cached.songs.isEmpty()) UiState.Loading(cached) else UiState.Success(cached)
			}
		}
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { loggedIn ->
				if (!loggedIn) return@collect
				val needsRemote = try {
					repository.getLocalData(collectionId).needsRemoteSongSync()
				} catch (_: Exception) {
					false
				}
				refreshCollection(needsRemote)
			}
		}
	}

	fun refreshCollection(fullRefresh: Boolean) {
		// One collector at a time: a new refresh replaces the previous one.
		refreshJob?.cancel()
		refreshJob = viewModelScope.launch {
			repository.getCollectionFlow(fullRefresh, collectionId).collect {
				collectionState.value = it
				if (it.data is DomainAlbum) {
					starred.value = albumRepository.isAlbumStarred(it.data as DomainAlbum)
					rating.value = albumRepository.getAlbumRating(it.data as DomainAlbum)
					try {
						val albumInfo = repository.getAlbumInfo(collectionId)
						albumInfoState.value = UiState.Success(albumInfo.toDomainModel())
					} catch (e: Exception) {
						albumInfoState.value = UiState.Error(e)
					}
				}
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

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
		selectedAlbum.value = null
	}

	fun clearError() {
		collectionState.value.data?.let {
			collectionState.value = UiState.Success(it)
		}
	}

	fun setPlaylistCover(imageBytes: ByteArray) {
		viewModelScope.launch {
			playlistCoverManager.setCustomCover(collectionId, imageBytes)
			refreshCollection(false)
		}
	}

	fun clearPlaylistCover() {
		viewModelScope.launch {
			playlistCoverManager.clearCustomCover(collectionId)
			refreshCollection(false)
		}
	}

	/**
	 * @param occurrence which copy of the song to remove when the playlist contains it more than once
	 * (0 = the first). The index sent to the server comes from a fresh copy of the playlist, never
	 * from the cached list, so a playlist changed elsewhere can't make us remove the wrong entry.
	 */
	fun removeFromPlaylist(occurrence: Int = 0) {
		val song = selectedSong.value ?: return
		viewModelScope.launch {
			try {
				val serverIds = playlistSyncFetcher.fetch(collectionId).entries.map { it.id }
				val index = nthIndexOf(serverIds, song.id, occurrence)
				if (index < 0) {
					snackBarManager.notify(Res.string.notice_playlist_changed)
					refreshCollection(true)
					return@launch
				}
				sessionManager.api.updatePlaylist(
					id = collectionId,
					songIndicesToRemove = listOf(index)
				)
				snackBarManager.notify(Res.string.notice_removed_from_playlist)
				refreshCollection(true)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("CollectionDetailViewModel", "Failed to remove song from playlist", e)
			}
		}
		clearSelection()
	}

	/** Keeps this week's mix as an ordinary playlist, since the mix itself is replaced every Monday. */
	fun saveMixCopy(weekLabel: String) {
		val playlist = collectionState.value.data as? DomainPlaylist ?: return
		val ids = playlist.songs.map { it.id }
		if (ids.isEmpty()) return
		viewModelScope.launch {
			try {
				val name = "${playlist.name} · $weekLabel"
				val created = sessionManager.api.createPlaylist(name = name, songIds = ids.take(PLAYLIST_BATCH_SIZE))
				appendPlaylistBatches(ids.drop(PLAYLIST_BATCH_SIZE)) { batch ->
					sessionManager.api.updatePlaylist(created.id, songIdsToAdd = batch)
				}
				playlist.displayComment?.let { sessionManager.api.updatePlaylist(created.id, comment = it) }
				dbRepository.syncPlaylists().getOrThrow()
				dbRepository.syncPlaylistSongs(created.id).getOrThrow()
				snackBarManager.notify(Res.string.notice_saved_mix_copy, name)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("CollectionDetailViewModel", "Failed to save a copy of the mix", e)
				collectionState.value = UiState.Error(e, collectionState.value.data)
			}
		}
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				songRepository.starSong(selection)
				selectedSongIsStarred.value = true
				refreshCollection(false)
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				songRepository.unstarSong(selection)
				selectedSongIsStarred.value = false
				refreshCollection(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatchingCancellable {
				songRepository.rateSong(selection, rating)
				selectedSongRating.value = rating
			}
		}
	}

	fun rateAlbum(newRating: Int) {
		viewModelScope.launch {
			(collectionState.value.data as? DomainAlbum)?.let { album ->
				albumRepository.rateAlbum(album, newRating)
				rating.value = newRating
			}
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatchingCancellable {
				val collection = collectionState.value.data ?: return@launch
				if (collection !is DomainAlbum) return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				refreshCollection(false)
			}
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			selectedAlbum.value?.let { album ->
				albumRepository.rateAlbum(album, rating)
				selectedAlbumRating.value = rating
			}
		}
	}

	fun starSelectedAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatchingCancellable {
				val collection = selectedAlbum.value ?: return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				selectedAlbumIsStarred.value = starred
			}
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

	fun downloadAll() {
		val collection = collectionState.value.data ?: return
		viewModelScope.launch {
			downloadManager.downloadCollection(collection)
			snackBarManager.notify(Res.string.notice_download_started)
		}
	}

	fun cancelDownloadAll() {
		collectionState.value.data?.songs?.forEach {
			downloadManager.cancelDownload(it.id)
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	private val sharedDownloadStatus: StateFlow<DownloadStatus> = collectionState
		.map { it.data?.songs.orEmpty().map { song -> song.id } }
		.distinctUntilChanged()
		.flatMapLatest { downloadManager.getCollectionDownloadStatus(it) }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadStatus.NOT_DOWNLOADED)

	fun collectionDownloadStatus(): StateFlow<DownloadStatus> = sharedDownloadStatus

}

/** Index of the [occurrence]-th (0-based) element equal to [target], or -1. */
internal fun nthIndexOf(ids: List<String>, target: String, occurrence: Int): Int {
	var seen = 0
	ids.forEachIndexed { index, id ->
		if (id == target) {
			if (seen == occurrence) return index
			seen++
		}
	}
	return -1
}
