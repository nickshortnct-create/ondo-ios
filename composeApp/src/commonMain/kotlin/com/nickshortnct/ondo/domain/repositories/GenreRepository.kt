package com.nickshortnct.ondo.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nickshortnct.ondo.data.database.dao.GenreDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.models.DomainGenre
import com.nickshortnct.ondo.ui.core.UiState

class GenreRepository(
	private val genreDao: GenreDao,
	private val albumDao: com.nickshortnct.ondo.data.database.dao.AlbumDao,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(): ImmutableList<DomainGenre> {
		val albums = albumDao.getAlbumEntities().map { it.toDomainModel() }
		return genreDao.getGenres().filter { it.albumCount > 0 }.map { genre ->
			DomainGenre(genre.genreName, genre.albumCount, genre.songCount,
				albums.filter { it.genre == genre.genreName || genre.genreName in it.genres }.take(2))
		}.toImmutableList()
	}

	private suspend fun refreshLocalData(): ImmutableList<DomainGenre> {
		dbRepository.syncGenres().getOrThrow()
		return getLocalData()
	}

	fun getGenresFlow(
		fullRefresh: Boolean
	): Flow<UiState<ImmutableList<DomainGenre>>> = flow {
		val localData = getLocalData()
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData()))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)
}
