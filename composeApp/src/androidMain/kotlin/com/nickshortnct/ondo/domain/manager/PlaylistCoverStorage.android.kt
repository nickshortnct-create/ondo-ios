package com.nickshortnct.ondo.domain.manager

import android.content.Context
import java.io.File

actual class PlaylistCoverStorage(
	private val context: Context,
) {
	actual fun save(playlistId: String, imageBytes: ByteArray): String {
		val directory = File(context.filesDir, PLAYLIST_COVERS_DIR).apply { mkdirs() }
		val safeId = playlistId.filter { it.isLetterOrDigit() || it == '-' }
		val file = File(directory, "$safeId-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.jpg")
		file.writeBytes(imageBytes)
		return file.toURI().toString()
	}

	actual fun delete(path: String) {
		runCatching { File(java.net.URI(path)).delete() }
	}

	companion object {
		private const val PLAYLIST_COVERS_DIR = "playlist_covers"
	}
}
