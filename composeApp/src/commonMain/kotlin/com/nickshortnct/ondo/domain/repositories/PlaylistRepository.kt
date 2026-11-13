package com.nickshortnct.ondo.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nickshortnct.ondo.data.database.dao.DownloadDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainPlaylistListType
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ondo.mixTag

class PlaylistRepository(
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val downloadDao: DownloadDao
) {
	private suspend fun getLocalData(listType: DomainPlaylistListType, reversed: Boolean,
		filters: Set<DomainFilter> = emptySet(),
		rows: List<com.nickshortnct.ondo.data.database.relations.PlaylistSummary>? = null
	): ImmutableList<DomainPlaylist> {
		val downloaded = if (DomainFilter.Downloaded in filters) downloadDao.getAllDownloadsList()
			.filter { it.status == DownloadStatus.DOWNLOADED }.map { it.songId }.toSet() else emptySet()
		val filtered = (rows ?: playlistDao.getPlaylistSummaries()).filter { row -> filters.all { filter -> when (filter) {
			DomainFilter.Starred -> false
			DomainFilter.Downloaded -> row.songIds.orEmpty().size == row.playlist.songCount && downloaded.containsAll(row.songIds.orEmpty())
		} } }
		val sorted = when (listType) {
			DomainPlaylistListType.Name -> filtered.sortedBy { it.playlist.name.lowercase() }
			DomainPlaylistListType.DateAdded -> filtered.sortedByDescending { it.playlist.createdAt }
			DomainPlaylistListType.ReleaseYear -> filtered.sortedWith(compareBy<com.nickshortnct.ondo.data.database.relations.PlaylistSummary> { it.latestSongYear == null }.thenByDescending { it.latestSongYear }.thenBy { it.playlist.name.lowercase() })
			DomainPlaylistListType.Duration -> filtered.sortedByDescending { it.playlist.duration }
			DomainPlaylistListType.Random -> filtered.shuffled()
		}.map { it.toDomainModel() }
		val ordered = if (reversed) sorted.asReversed() else sorted
		// The user's own playlists come first; the weekly mixes follow in their home-screen order.
		val (mixes, own) = ordered.partition { it.mixTag != null }
		val mixOrder = compareBy<DomainPlaylist>({ it.mixTag?.isMood != true }, { it.mixTag?.slot?.substringAfterLast('-')?.toIntOrNull() ?: 0 })
		return (own + mixes.sortedWith(mixOrder)).toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainPlaylist> {
		dbRepository.syncPlaylists().getOrThrow().forEach { playlist ->
			dbRepository.syncPlaylistSongs(playlist.playlistId).getOrThrow()
		}
		return getLocalData(listType, reversed, filters)
	}

	fun getPlaylistsFlow(
		fullRefresh: Boolean,
		listType: DomainPlaylistListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): Flow<UiState<ImmutableList<DomainPlaylist>>> = flow {
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
		playlistDao.observePlaylistSummaries().collect { rows ->
			emit(UiState.Success(getLocalData(listType, reversed, filters, rows)))
		}
	}.flowOn(Dispatchers.IO)
}
