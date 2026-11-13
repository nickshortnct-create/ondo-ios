package com.nickshortnct.ondo.domain.manager

actual class PlaylistCoverStorage {
	actual fun save(playlistId: String, imageBytes: ByteArray): String {
		error("Custom playlist covers are not supported on this platform yet")
	}

	actual fun delete(path: String) = Unit
}
