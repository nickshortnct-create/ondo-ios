package com.nickshortnct.ondo.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.dao.DownloadDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.entities.SyncActionType
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SyncManager
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.ui.core.UiState
import kotlin.time.Clock

class ArtistRepository(
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		val artists = when (listType) {
			DomainArtistListType.AlphabeticalByName -> artistDao.getArtistsAlphabeticalByName()
			DomainArtistListType.Random -> artistDao.getArtistsRandom()
		}.map { it.toDomainModel() }

		val downloadedIds = if (DomainFilter.Downloaded in filters) downloadDao.getAllDownloadsList()
			.filter { it.status == DownloadStatus.DOWNLOADED }.map { it.songId }.toSet() else emptySet()
		val songsByArtist = if (DomainFilter.Downloaded in filters) songDao.getArtistSongIds().groupBy({ it.artistId }, { it.songId }) else emptyMap()
		return artists.filter { artist ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> artist.starredAt != null
					DomainFilter.Downloaded -> songsByArtist[artist.id]?.takeIf { it.isNotEmpty() }?.all { it in downloadedIds } == true
				}
			}
		}.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		dbRepository.syncArtists().getOrThrow()
		return getLocalData(listType, filters)
	}

	fun getArtistsFlow(
		fullRefresh: Boolean,
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): Flow<UiState<ImmutableList<DomainArtist>>> = flow {
		val localData = getLocalData(listType, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, filters)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	suspend fun isArtistStarred(artist: DomainArtist) = artistDao.isArtistStarred(artist.id)

	suspend fun starArtist(artist: DomainArtist) {
		artistDao.updateStarredAt(artist.id, Clock.System.now())
		syncManager.enqueueAction(SyncActionType.STAR, artist.id)
	}

	suspend fun unstarArtist(artist: DomainArtist) {
		artistDao.updateStarredAt(artist.id, null)
		syncManager.enqueueAction(SyncActionType.UNSTAR, artist.id)
	}
}
