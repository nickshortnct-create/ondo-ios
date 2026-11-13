package com.nickshortnct.ondo.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.DownloadDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.entities.SyncActionType
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SyncManager
import com.nickshortnct.ondo.util.chunkedIn
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.util.sortedByListType
import kotlin.time.Clock

class SongRepository(
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val downloadDao: DownloadDao,
	private val dbRepository: DbRepository,
	private val syncManager: SyncManager
) {
	suspend fun getAllSongs(): List<DomainSong> {
		return songDao.getAllSongs().map { it.toDomainModel() }
	}

	suspend fun getSongsByIds(ids: List<String>): List<DomainSong> {
		return chunkedIn(ids) { songDao.getSongsByIds(it) }.map { it.toDomainModel() }
	}

	suspend fun getRandomSongs(count: Int): List<DomainSong> {
		return songDao.getRandomSongs(count).map { it.toDomainModel() }
	}

	private suspend fun getLocalData(
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainSong> {
		val songs = songDao
			.getAllSongs()
			.map { it.toDomainModel() }

		val downloadedIds = if (filters.contains(DomainFilter.Downloaded)) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.map { it.songId }
				.toSet()
		} else emptySet()

		val filteredByFilters = songs.filter { song ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> song.starredAt != null
					DomainFilter.Downloaded -> downloadedIds.contains(song.id)
				}
			}
		}

		val sorted = filteredByFilters
			.toImmutableList()
			.sortedByListType(
				listType,
				albums = albumDao.getAlbumEntities().map { it.toDomainModel() }
			)

		return if (reversed) {
			sorted.reversed().toImmutableList()
		} else {
			sorted
		}
	}

	private suspend fun refreshLocalData(
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter>
	): ImmutableList<DomainSong> {
		dbRepository.syncLibrarySongs().getOrThrow()
		return getLocalData(listType, reversed, filters)
	}

	fun getSongsFlow(
		fullRefresh: Boolean,
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter>
	): Flow<UiState<ImmutableList<DomainSong>>> = flow {
		val localData = getLocalData(listType, reversed, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed, filters)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	suspend fun isSongStarred(song: DomainSong) = songDao.isSongStarred(song.id)
	suspend fun getSongRating(song: DomainSong) = songDao.getSongRating(song.id) ?: 0
	suspend fun isSongStarred(songId: String) = songDao.isSongStarred(songId)
	suspend fun starSong(song: DomainSong) = setSongStarred(song.id, true)
	suspend fun unstarSong(song: DomainSong) = setSongStarred(song.id, false)

	/**
	 * Works on a bare id so the playback service (notification, Android Auto,
	 * watch) can favourite the current track without loading the whole song.
	 */
	suspend fun setSongStarred(songId: String, starred: Boolean) {
		songDao.updateStarredAt(songId, if (starred) Clock.System.now() else null)
		syncManager.enqueueAction(if (starred) SyncActionType.STAR else SyncActionType.UNSTAR, songId)
		SongStarEvents.changes.tryEmit(songId to starred)
	}

	suspend fun rateSong(song: DomainSong, rating: Int) {
		songDao.updateRating(song.id, rating)
		when (rating) {
			0 -> syncManager.enqueueAction(SyncActionType.STAR_0, song.id)
			1 -> syncManager.enqueueAction(SyncActionType.STAR_1, song.id)
			2 -> syncManager.enqueueAction(SyncActionType.STAR_2, song.id)
			3 -> syncManager.enqueueAction(SyncActionType.STAR_3, song.id)
			4 -> syncManager.enqueueAction(SyncActionType.STAR_4, song.id)
			5 -> syncManager.enqueueAction(SyncActionType.STAR_5, song.id)
		}
	}
}

/** Star changes from any surface (app, notification, Auto, watch) so the others can refresh. */
object SongStarEvents {
	val changes: MutableSharedFlow<Pair<String, Boolean>> = MutableSharedFlow(extraBufferCapacity = 16)
	val events: SharedFlow<Pair<String, Boolean>> get() = changes
}
