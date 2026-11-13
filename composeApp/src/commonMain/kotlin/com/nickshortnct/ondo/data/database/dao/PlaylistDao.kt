package com.nickshortnct.ondo.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistSongCrossRef
import com.nickshortnct.ondo.data.database.relations.PlaylistWithSongs
import com.nickshortnct.ondo.util.Logger

@Dao
interface PlaylistDao {
	@Query("SELECT PlaylistEntity.*, (SELECT GROUP_CONCAT(songId, '||') FROM PlaylistSongCrossRef WHERE playlistId = PlaylistEntity.playlistId) AS songIds, (SELECT MAX(year) FROM SongEntity INNER JOIN PlaylistSongCrossRef ON SongEntity.songId = PlaylistSongCrossRef.songId WHERE PlaylistSongCrossRef.playlistId = PlaylistEntity.playlistId) AS latestSongYear FROM PlaylistEntity")
	suspend fun getPlaylistSummaries(): List<com.nickshortnct.ondo.data.database.relations.PlaylistSummary>

	@Query("SELECT PlaylistEntity.*, (SELECT GROUP_CONCAT(songId, '||') FROM PlaylistSongCrossRef WHERE playlistId = PlaylistEntity.playlistId) AS songIds, (SELECT MAX(year) FROM SongEntity INNER JOIN PlaylistSongCrossRef ON SongEntity.songId = PlaylistSongCrossRef.songId WHERE PlaylistSongCrossRef.playlistId = PlaylistEntity.playlistId) AS latestSongYear FROM PlaylistEntity")
	fun observePlaylistSummaries(): Flow<List<com.nickshortnct.ondo.data.database.relations.PlaylistSummary>>

	@Query("SELECT * FROM PlaylistEntity ORDER BY name COLLATE NOCASE LIMIT :limit")
	suspend fun browsePlaylists(limit: Int): List<PlaylistEntity>

	// Never use REPLACE for playlists: SQLite implements it as delete + insert, and
	// PlaylistSongCrossRef has ON DELETE CASCADE, so replacing a playlist row would
	// silently empty that playlist's song list.
	@Upsert
	suspend fun insertPlaylist(playlist: PlaylistEntity)

	@Upsert
	suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

	@Query("SELECT * FROM PlaylistEntity")
	suspend fun getAllPlaylistEntities(): List<PlaylistEntity>

	@Query("SELECT songId FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
	suspend fun getPlaylistSongIds(playlistId: String): List<String>

	@Query("SELECT customCoverPath FROM PlaylistEntity WHERE playlistId = :playlistId")
	suspend fun getCustomCoverPath(playlistId: String): String?

	@Query("UPDATE PlaylistEntity SET customCoverPath = :path WHERE playlistId = :playlistId")
	suspend fun setCustomCoverPath(playlistId: String, path: String?)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertPlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY name ASC")
	suspend fun getAllPlaylistsByName(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY createdAt DESC")
	suspend fun getAllPlaylistsByDateAdded(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY createdAt DESC")
	suspend fun getAllPlaylistsForReleaseYearSort(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY duration DESC")
	suspend fun getAllPlaylistsByDuration(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY RANDOM()")
	suspend fun getAllPlaylistsRandom(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY name ASC")
	fun getAllPlaylistsFlow(): Flow<List<PlaylistWithSongs>>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity WHERE playlistId = :playlistId LIMIT 1")
	suspend fun getPlaylistById(playlistId: String): PlaylistWithSongs?

	@Query("DELETE FROM PlaylistEntity WHERE playlistId = :playlistId")
	suspend fun deletePlaylist(playlistId: String)

	@Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
	suspend fun deletePlaylistSongCrossRefs(playlistId: String)

	/** Updates the playlist row and swaps its song list in one transaction. */
	@Transaction
	suspend fun replacePlaylistWithSongs(playlist: PlaylistEntity, crossRefs: List<PlaylistSongCrossRef>) {
		insertPlaylist(playlist)
		deletePlaylistSongCrossRefs(playlist.playlistId)
		insertPlaylistSongCrossRefs(crossRefs)
	}

	@Transaction
	suspend fun replacePlaylistSongs(playlistId: String, crossRefs: List<PlaylistSongCrossRef>) {
		deletePlaylistSongCrossRefs(playlistId)
		insertPlaylistSongCrossRefs(crossRefs)
	}

	@Query("SELECT COUNT(*) FROM PlaylistEntity")
	suspend fun getPlaylistCount(): Int

	@Query("DELETE FROM PlaylistEntity")
	suspend fun clearAllPlaylists()

	@Query("SELECT playlistId FROM PlaylistEntity")
	suspend fun getAllPlaylistIds(): List<String>

	@RawQuery
	suspend fun searchPlaylistsList(query: RoomRawQuery): List<PlaylistEntity>

	@Transaction
	suspend fun updateAllPlaylists(remotePlaylists: List<PlaylistEntity>) {
		val remoteIds = remotePlaylists.map { it.playlistId }.toSet()
		getAllPlaylistIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("PlaylistDao", "playlist $localId no longer exists remotely")
				deletePlaylist(localId)
			}
		}
		insertPlaylists(remotePlaylists)
	}

	@Transaction
	suspend fun deleteObsoletePlaylists(remoteIds: Set<String>) {
		getAllPlaylistIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("PlaylistDao", "playlist $localId no longer exists remotely")
				deletePlaylist(localId)
			}
		}
	}
}
