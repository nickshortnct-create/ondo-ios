package com.nickshortnct.ondo.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.DownloadDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.entities.SyncActionType
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SyncManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.util.toSqlQuery
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class AlbumRepository(
	private val albumDao: AlbumDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainAlbum> {
		val sortedQuery = listType.toSqlQuery()
		val summaryQuery = androidx.room3.RoomRawQuery(sortedQuery.sql.replace("SELECT * FROM AlbumEntity",
			"SELECT AlbumEntity.*, (SELECT GROUP_CONCAT(songId, '||') FROM SongEntity WHERE belongsToAlbumId = AlbumEntity.albumId) AS songIds FROM AlbumEntity"),
			sortedQuery.getBindingFunction())
		val downloadedSongIds = if (filters.contains(DomainFilter.Downloaded)) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.map { it.songId }
				.toSet()
		} else null

		return albumDao
			.getAlbumSummaries(summaryQuery)
			.map { it.toDomainModel() }
			.filter { album ->
				filters.all { filter ->
					when (filter) {
						DomainFilter.Starred -> album.starredAt != null
						DomainFilter.Downloaded -> downloadedSongIds != null &&
							album.cachedSongIds.size == album.songCount &&
							downloadedSongIds.containsAll(album.cachedSongIds)
					}
				}
			}
			.let { if (reversed) it.asReversed() else it }
			.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainAlbum> = withTimeout(30.seconds) {
		dbRepository.syncLibraryAlbumSummaries().getOrThrow()
		getLocalData(listType, reversed, filters)
	}

	fun getAlbumsFlow(
		fullRefresh: Boolean,
		listType: DomainAlbumListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): Flow<UiState<ImmutableList<DomainAlbum>>> = flow {
		val localData = getLocalData(listType, reversed, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed, filters)))
			} catch (error: TimeoutCancellationException) {
				emit(UiState.Error(error = error, data = localData))
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	suspend fun isAlbumStarred(album: DomainAlbum) = albumDao.isAlbumStarred(album.id)
	suspend fun getAlbumRating(album: DomainAlbum) = albumDao.getAlbumRating(album.id) ?: 0

	suspend fun starAlbum(album: DomainAlbum) {
		albumDao.updateStarredAt(album.id, Clock.System.now())
		syncManager.enqueueAction(SyncActionType.STAR, album.id)
	}

	suspend fun unstarAlbum(album: DomainAlbum) {
		albumDao.updateStarredAt(album.id, null)
		syncManager.enqueueAction(SyncActionType.UNSTAR, album.id)
	}

	suspend fun rateAlbum(album: DomainAlbum, rating: Int) {
		albumDao.updateRating(album.id, rating)
		when (rating) {
			0 -> syncManager.enqueueAction(SyncActionType.STAR_0, album.id)
			1 -> syncManager.enqueueAction(SyncActionType.STAR_1, album.id)
			2 -> syncManager.enqueueAction(SyncActionType.STAR_2, album.id)
			3 -> syncManager.enqueueAction(SyncActionType.STAR_3, album.id)
			4 -> syncManager.enqueueAction(SyncActionType.STAR_4, album.id)
			5 -> syncManager.enqueueAction(SyncActionType.STAR_5, album.id)
		}
	}
}
