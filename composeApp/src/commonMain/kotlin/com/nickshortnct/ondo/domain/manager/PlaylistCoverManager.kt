package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nickshortnct.ondo.data.database.dao.PlaylistDao

class PlaylistCoverManager(
	private val playlistDao: PlaylistDao,
	private val storage: PlaylistCoverStorage,
) {
	private val mutex = Mutex()
	suspend fun setCustomCover(playlistId: String, imageBytes: ByteArray) = withContext(Dispatchers.IO) {
		mutex.withLock {
			val previous = playlistDao.getCustomCoverPath(playlistId)
			val savedPath = storage.save(playlistId, imageBytes)
			try { playlistDao.setCustomCoverPath(playlistId, savedPath) }
			catch (e: Exception) { storage.delete(savedPath); throw e }
			previous?.let(storage::delete)
		}
	}

	suspend fun clearCustomCover(playlistId: String) = withContext(Dispatchers.IO) {
		mutex.withLock {
			val previous = playlistDao.getCustomCoverPath(playlistId)
			playlistDao.setCustomCoverPath(playlistId, null)
			previous?.let(storage::delete)
		}
	}

}

expect class PlaylistCoverStorage {
	fun save(playlistId: String, imageBytes: ByteArray): String
	fun delete(path: String)
}
