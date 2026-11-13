package com.nickshortnct.ondo.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistSongCrossRef
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.ondo.ConnectTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaylistDaoUpsertTest {
	@Test fun refreshAndCoverUpdatePreserveMembershipAndDuplicatePositions() = runTest {
		val db = Room.inMemoryDatabaseBuilder<CacheDatabase> { CacheDatabaseConstructor.initialize() }
			.setDriver(BundledSQLiteDriver())
			.setQueryCoroutineContext(Dispatchers.IO)
			.build()
		try {
			val dao = db.playlistDao()
			val playlist = playlist()
			db.songDao().insertSongs(listOf(ConnectTrack("song").song().toEntity()))
			dao.insertPlaylist(playlist)
			dao.insertPlaylistSongCrossRefs(listOf(
				PlaylistSongCrossRef(playlist.playlistId, "song", 0),
				PlaylistSongCrossRef(playlist.playlistId, "song", 1)
			))
			dao.insertPlaylist(playlist.toDomainModel().copy(name = "Refreshed").toEntity())
			dao.setCustomCoverPath(playlist.playlistId, "/test/new-cover.jpg")
			val refreshed = dao.getPlaylistById(playlist.playlistId)!!
			assertEquals("Refreshed", refreshed.playlist.name)
			assertEquals("/test/new-cover.jpg", refreshed.playlist.customCoverPath)
			assertEquals(listOf(0, 1), refreshed.songs.map { it.crossRef.position }.sorted())
			assertEquals(listOf("song", "song"), refreshed.songs.map { it.song.songId })
			val searchResult = dao.searchPlaylistsList(librarySearchQuery("refreshed", LibrarySearchTable.PLAYLIST)).single()
			assertEquals("Refreshed", searchResult.name)
			assertEquals("/test/new-cover.jpg", searchResult.customCoverPath)
		} finally {
			db.close()
		}
	}

	private fun playlist() = PlaylistEntity(
		playlistId = "playlist", name = "Favorites", comment = null, owner = "fixture",
		coverArtId = null, songCount = 2, duration = 60.seconds, public = false,
		readOnly = false, createdAt = Instant.fromEpochMilliseconds(0),
		modifiedAt = Instant.fromEpochMilliseconds(0), validUntil = null, allowedUsers = emptyList()
	)
}
