package com.nickshortnct.ondo.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.relations.AlbumWithSongs
import com.nickshortnct.ondo.util.Logger

@Dao
interface AlbumDao {
	@RawQuery
	suspend fun searchAlbumEntities(query: RoomRawQuery): List<AlbumEntity>

	@Query("SELECT * FROM AlbumEntity")
	suspend fun getAlbumEntities(): List<AlbumEntity>

	@RawQuery
	suspend fun getAlbumSummaries(query: RoomRawQuery): List<com.nickshortnct.ondo.data.database.relations.AlbumSummary>

	@Query("SELECT * FROM AlbumEntity ORDER BY name COLLATE NOCASE LIMIT :limit")
	suspend fun browseAlbums(limit: Int): List<AlbumEntity>
	@Query("SELECT * FROM AlbumEntity ORDER BY createdAt DESC LIMIT :limit")
	suspend fun browseRecentAlbums(limit: Int): List<AlbumEntity>
	@Query("SELECT * FROM AlbumEntity WHERE starredAt IS NOT NULL ORDER BY starredAt DESC LIMIT :limit")
	suspend fun browseStarredAlbums(limit: Int): List<AlbumEntity>
	@Query("SELECT * FROM AlbumEntity WHERE artistId = :id ORDER BY year DESC LIMIT :limit")
	suspend fun browseArtistAlbums(id: String, limit: Int): List<AlbumEntity>

	@Query("UPDATE AlbumEntity SET starredAt = :at WHERE albumId = :id")
	suspend fun updateStarredAt(id: String, at: kotlin.time.Instant?)

	@Query("UPDATE AlbumEntity SET userRating = :rating WHERE albumId = :id")
	suspend fun updateRating(id: String, rating: Int)

	@Transaction
	@Query(" SELECT * FROM AlbumEntity WHERE genre = :genreName OR ('||' || genres || '||') LIKE '%||' || replace(replace(replace(:genreName, '\\', '\\\\'), '%', '\\%'), '_', '\\_') || '||%' ESCAPE '\\' ORDER BY year DESC, name COLLATE NOCASE ASC")
	fun getAlbumsByGenre(genreName: String): Flow<List<AlbumWithSongs>>

	@Transaction
	@Query("SELECT * FROM AlbumEntity ORDER BY name ASC")
	fun getAllAlbums(): Flow<List<AlbumWithSongs>>

	@Transaction
	@Query("SELECT * FROM AlbumEntity ORDER BY name ASC")
	suspend fun getAllAlbumsList(): List<AlbumWithSongs>

	@RawQuery
	suspend fun getAlbumsByQuery(query: RoomRawQuery): List<AlbumWithSongs>

	@Transaction
	@Query("SELECT COUNT(albumId) FROM AlbumEntity")
	suspend fun getAlbumCount(): Int

	@Transaction
	@Query("SELECT * FROM AlbumEntity WHERE albumId = :albumId LIMIT 1")
	suspend fun getAlbumById(albumId: String): AlbumWithSongs?

	@Query("SELECT EXISTS(SELECT 1 FROM AlbumEntity WHERE albumId = :albumId AND starredAt IS NOT NULL)")
	suspend fun isAlbumStarred(albumId: String): Boolean

	@Query("SELECT userRating FROM AlbumEntity WHERE albumId = :albumId")
	suspend fun getAlbumRating(albumId: String): Int?

	@Transaction
	@Query("SELECT * FROM AlbumEntity WHERE artistId = :artistId ORDER BY year DESC")
	fun getAlbumsByArtist(artistId: String): Flow<List<AlbumWithSongs>>

	@Transaction
	@Query("SELECT * FROM AlbumEntity WHERE artistName = :artistName ORDER BY year DESC")
	fun getAlbumsByArtistName(artistName: String): Flow<List<AlbumWithSongs>>

	@Transaction
	@Query("SELECT * FROM AlbumEntity WHERE artistId = :artistId AND albumId != :albumId ORDER BY year DESC")
	fun getAlbumsByArtistExcluding(artistId: String, albumId: String): Flow<List<AlbumWithSongs>>

	@Transaction
	@RawQuery
	suspend fun searchAlbumsList(query: RoomRawQuery): List<AlbumWithSongs>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAlbum(album: AlbumEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAlbums(albums: List<AlbumEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAlbumsIgnoringConflicts(albums: List<AlbumEntity>)

	@Query("DELETE FROM AlbumEntity WHERE albumId = :albumId")
	suspend fun deleteAlbum(albumId: String)

	@Query("DELETE FROM AlbumEntity")
	suspend fun clearAllAlbums()

	@Query("SELECT albumId FROM AlbumEntity")
	suspend fun getAllAlbumIds(): List<String>

	@Query("SELECT * FROM AlbumEntity WHERE albumId IN (:ids)")
	suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>

	@Transaction
	@Query("SELECT * FROM AlbumEntity WHERE albumId IN (:ids)")
	suspend fun getAlbumsByIds(ids: List<String>): List<AlbumWithSongs>

	@Transaction
	suspend fun updateAllAlbums(remoteAlbums: List<AlbumEntity>) {
		val remoteIds = remoteAlbums.map { it.albumId }.toSet()
		getAllAlbumIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("AlbumDao", "album $localId no longer exists remotely")
				deleteAlbum(localId)
			}
		}
		insertAlbums(remoteAlbums)
	}

	@Transaction
	suspend fun deleteObsoleteAlbums(remoteIds: Set<String>) {
		getAllAlbumIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("AlbumDao", "album $localId no longer exists remotely")
				deleteAlbum(localId)
			}
		}
	}
}
