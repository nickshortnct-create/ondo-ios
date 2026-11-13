package com.nickshortnct.ondo.domain.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeoutOrNull
import com.nickshortnct.ondo.data.database.entities.isMetadataPlaceholder
import com.nickshortnct.ondo.data.database.LibrarySearchTable
import com.nickshortnct.ondo.data.database.librarySearchQuery
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.util.chunkedIn
import com.nickshortnct.ondo.domain.models.normalizeSearchQuery
import com.nickshortnct.ondo.domain.models.mergeSearchResults
import com.nickshortnct.ondo.util.Logger

data class LibrarySearchOutcome(
	val results: List<Any>,
	val failures: List<String> = emptyList(),
	val isSearching: Boolean = false
)

class SearchRepository(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) {
	val isOnline = connectivityManager.isOnline

	suspend fun search(query: String): List<Any> = searchFlow(query).last().results

	// Cached matches are useful immediately, while the server checks for new music.
	fun searchFlow(query: String): Flow<LibrarySearchOutcome> = flow {
		val cleaned = normalizeSearchQuery(query)
		if (cleaned.isEmpty()) {
			emit(LibrarySearchOutcome(emptyList()))
			return@flow
		}
		val online = isOnline.value
		// Let discovery paint results even if the database is still reading its cache.
		emit(LibrarySearchOutcome(emptyList(), isSearching = true))
		var localFailure: List<String> = emptyList()
		val cached = try {
			performLocalSearch(cleaned)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("SearchRepository", "Cached search failed", e)
			localFailure = listOf("Your saved library could not be searched")
			emptyList()
		}
		emit(LibrarySearchOutcome(cached, localFailure, isSearching = online))
		if (!online) return@flow

		try {
			val remote = withTimeoutOrNull(10_000) { performRemoteSearch(cleaned) }
			if (remote == null) {
				emit(LibrarySearchOutcome(cached, localFailure + "Your library server timed out"))
			} else {
				// Servers can interpret compound title/artist queries differently from
				// the local cache. Keep current-query matches, deduplicated by type/ID.
				val combined = mergeSearchResults(cleaned, remote + cached, emptyList())
					.mapNotNull { it.library }
				emit(LibrarySearchOutcome(combined))
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("SearchRepository", "Server search failed; keeping cached matches", e)
			emit(LibrarySearchOutcome(cached, localFailure + "Your library server is temporarily unavailable"))
		}
	}

	private suspend fun performRemoteSearch(query: String): List<Any> {
		val data = sessionManager.api.searchID3(query, artistCount = 50, albumCount = 100, songCount = 200)
		albumDao.insertAlbumsIgnoringConflicts(data.albums.map { it.toEntity() })
		artistDao.insertArtistsIgnoringConflicts(data.artists.map { it.toEntity() })
		val incomingSongs = data.songs.map { it.toEntity() }
		val cachedSongs = chunkedIn(incomingSongs.map { it.songId }) { songDao.getSongsByIds(it) }.associateBy { it.songId }
		songDao.insertSongs(incomingSongs.filter { cachedSongs[it.songId]?.isMetadataPlaceholder() == true })
		songDao.insertSongsIgnoringConflicts(incomingSongs)

		return coroutineScope {
			val albums = async { chunkedIn(data.albums.map { it.id }) { albumDao.getAlbumsByIds(it) }.map { it.toDomainModel() } }
			val artists = async { chunkedIn(data.artists.map { it.id }) { artistDao.getArtistsByIds(it) }.map { it.toDomainModel() } }
			val songs = async { chunkedIn(data.songs.map { it.id }) { songDao.getSongsByIds(it) }.map { it.toDomainModel() } }
			val playlists = async { playlistDao.searchPlaylistsList(librarySearchQuery(query, LibrarySearchTable.PLAYLIST)).map { it.toDomainModel() } }
			albums.await() + artists.await() + songs.await() + playlists.await()
		}
	}

	private suspend fun performLocalSearch(query: String): List<Any> = coroutineScope {
		val albums = async { albumDao.searchAlbumsList(librarySearchQuery(query, LibrarySearchTable.ALBUM)).map { it.toDomainModel() } }
		val artists = async { artistDao.searchArtistsList(librarySearchQuery(query, LibrarySearchTable.ARTIST)).map { it.toDomainModel() } }
		val songs = async { songDao.searchSongsList(librarySearchQuery(query, LibrarySearchTable.SONG)).map { it.toDomainModel() } }
		val playlists = async { playlistDao.searchPlaylistsList(librarySearchQuery(query, LibrarySearchTable.PLAYLIST)).map { it.toDomainModel() } }
		albums.await() + artists.await() + songs.await() + playlists.await()
	}
}
