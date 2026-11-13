package com.nickshortnct.ondo.domain.manager


import io.ktor.utils.io.ByteReadChannel

/** A file in the downloads folder, as found on disk. */
data class StoredFile(val path: String, val name: String, val lastModifiedMs: Long)

expect class StorageManager {
	/** Every file currently in the downloads folder (used to reconcile it with the database). */
	fun listDownloadedFiles(): List<StoredFile>
	fun getDownloadPath(songId: String, extension: String): String
	fun deleteFile(path: String): Boolean
	fun getFileSize(path: String): Long
	suspend fun saveFile(path: String, channel: ByteReadChannel)
	fun clearDownloads()
}
