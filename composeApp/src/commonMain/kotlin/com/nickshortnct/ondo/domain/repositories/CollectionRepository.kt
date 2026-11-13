package com.nickshortnct.ondo.domain.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.needsRemoteSongSync
import com.nickshortnct.ondo.ui.core.UiState
import dev.zt64.subsonic.api.model.AlbumInfo as ApiAlbumInfo

class CollectionRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val dbRepository: DbRepository,
	private val sessionManager: SessionManager
) {
	suspend fun getLocalData(collectionId: String): DomainSongCollection {
		return albumDao.getAlbumById(collectionId)?.toDomainModel()
			?: playlistDao.getPlaylistById(collectionId)?.toDomainModel()
			?: throw IllegalStateException("Collection ID $collectionId is neither a known album or playlist")
	}

	private suspend fun getLocalDataOrFetch(collectionId: String): DomainSongCollection {
		try {
			return getLocalData(collectionId)
		} catch (_: IllegalStateException) {
			// A collection can be opened from a restored navigation route or a
			// newly synced list before its local row exists. Rehydrate playlists
			// first (the raw fetcher handles minimal-client responses), then try
			// the album endpoint and cache the result.
		}

		try {
			dbRepository.syncPlaylistSongs(collectionId, tolerateDeserializeError = false).getOrThrow()
			return getLocalData(collectionId)
		} catch (error: CancellationException) {
			throw error
		} catch (_: Exception) {
			// Not a playlist; fall through to the album endpoint.
		}

		try {
			val album = sessionManager.api.getAlbum(collectionId)
			songDao.updateSongsByAlbumId(album.id, album.songs.map { it.toEntity() })
			albumDao.insertAlbum(album.toEntity())
			return getLocalData(collectionId)
		} catch (error: CancellationException) {
			throw error
		} catch (error: Exception) {
			throw IllegalStateException(
				"Collection $collectionId was not found in the local library or on the server",
				error
			)
		}
	}

	private suspend fun refreshLocalData(collectionId: String): DomainSongCollection {
		when (val collection = getLocalData(collectionId)) {
			is DomainAlbum -> {
				val album = sessionManager.api.getAlbum(collection.id)
				songDao.updateSongsByAlbumId(album.id, album.songs.map { it.toEntity() })
				albumDao.insertAlbum(album.toEntity())
				albumDao.getAlbumById(album.id)!!.toDomainModel()
			}

			is DomainPlaylist -> {
				dbRepository.syncPlaylistSongs(
					collection.id,
					tolerateDeserializeError = false,
				).getOrThrow()
				playlistDao.getPlaylistById(collection.id)!!.toDomainModel()
			}
		}
		return getLocalData(collectionId)
	}

	fun getCollectionFlow(
		fullRefresh: Boolean,
		collectionId: String
	): Flow<UiState<DomainSongCollection>> = flow {
		try {
			val localData = getLocalDataOrFetch(collectionId)
			val shouldRefresh = fullRefresh || localData.needsRemoteSongSync()
			if (shouldRefresh) {
				emit(UiState.Loading(data = localData))
				try {
					emit(UiState.Success(data = refreshLocalData(collectionId)))
				} catch (error: CancellationException) {
					throw error
				} catch (error: Exception) {
					emit(UiState.Error(error = error, data = localData))
				}
			} else {
				emit(UiState.Success(data = localData))
			}
		} catch (error: CancellationException) {
			throw error
		} catch (error: Exception) {
			emit(UiState.Error(error = error))
		}
	}.flowOn(Dispatchers.IO)

	fun getOtherAlbums(artistId: String, albumId: String) = albumDao
		.getAlbumsByArtistExcluding(artistId, albumId)
		.map { it.map { album -> album.toDomainModel() } }

	suspend fun getSongById(songId: String) = songDao
		.getSongById(songId)
		?.toDomainModel()

	suspend fun getAlbumInfo(albumId: String): ApiAlbumInfo {
		return sessionManager.api.getAlbumInfo(albumId)
	}
}
