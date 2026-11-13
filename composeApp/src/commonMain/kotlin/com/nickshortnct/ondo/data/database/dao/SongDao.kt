package com.nickshortnct.ondo.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.nickshortnct.ondo.data.database.entities.PLACEHOLDER_ARTIST_NAME
import com.nickshortnct.ondo.data.database.entities.PLACEHOLDER_MIME_TYPE
import com.nickshortnct.ondo.data.database.entities.SongEntity
import com.nickshortnct.ondo.util.Logger

data class ArtistSongId(val artistId: String, val songId: String)

@Dao
interface SongDao {
	@Query("SELECT artistId, songId FROM SongEntity")
	suspend fun getArtistSongIds(): List<ArtistSongId>

	@Query("UPDATE SongEntity SET starredAt = :at WHERE songId = :id")
	suspend fun updateStarredAt(id: String, at: kotlin.time.Instant?)

	@Query("UPDATE SongEntity SET userRating = :rating WHERE songId = :id")
	suspend fun updateRating(id: String, rating: Int)

	@Query("SELECT * FROM SongEntity WHERE songId = :songId LIMIT 1")
	suspend fun getSongById(songId: String): SongEntity?

	@Upsert
	suspend fun insertSong(song: SongEntity)

	@Upsert
	suspend fun insertSongs(songs: List<SongEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertSongsIgnoringConflicts(songs: List<SongEntity>)

	@Query("SELECT * FROM SongEntity")
	suspend fun getAllSongs(): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE belongsToAlbumId = :albumId")
	suspend fun getSongsByAlbumId(albumId: String): List<SongEntity>

	@Query("DELETE FROM SongEntity WHERE songId = :songId")
	suspend fun deleteSong(songId: String)

	// TODO
	@Query("SELECT EXISTS(SELECT 1 FROM SongEntity WHERE songId = :songId AND starredAt IS NOT NULL)")
	suspend fun isSongStarred(songId: String): Boolean

	@Query("SELECT userRating FROM SongEntity WHERE songId = :songId")
	suspend fun getSongRating(songId: String): Int?

	@Query("DELETE FROM SongEntity")
	suspend fun clearAllSongs()

	@Query("SELECT songId FROM SongEntity")
	suspend fun getAllSongIds(): List<String>

	@Query("SELECT * FROM SongEntity WHERE songId IN (:ids)")
	suspend fun getSongsByIds(ids: List<String>): List<SongEntity>

	@Query(
		"SELECT * FROM SongEntity WHERE artistName = '" + PLACEHOLDER_ARTIST_NAME +
			"' AND fileExtension = '' AND mimeType = '" + PLACEHOLDER_MIME_TYPE + "'"
	)
	suspend fun getPlaceholderSongs(): List<SongEntity>

	@Query("SELECT * FROM SongEntity ORDER BY RANDOM() LIMIT :count")
	suspend fun getRandomSongs(count: Int): List<SongEntity>

	@RawQuery
	suspend fun searchSongsList(query: RoomRawQuery): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE artistId = :artistId")
	suspend fun getSongsByArtistId(artistId: String): List<SongEntity>

	@Transaction
	suspend fun updateSongsByAlbumId(albumId: String, remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getSongsByAlbumId(albumId).forEach { localSong ->
			if (localSong.songId !in remoteIds) {
				Logger.w("SongDao", "song ${localSong.songId} no longer belongs to album $albumId")
				deleteSong(localSong.songId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun updateAllSongs(remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
		insertSongs(remoteSongs)
	}

	@Transaction
	suspend fun deleteObsoleteSongs(remoteIds: Set<String>) {
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
	}
}
