package com.nickshortnct.ondo.data.database.entities

// Keep in sync with SongDao.getPlaceholderSongs(), which filters on the same three values.
const val PLACEHOLDER_ARTIST_NAME = "Unknown Artist"
const val PLACEHOLDER_MIME_TYPE = "application/octet-stream"

/** Placeholder rows written from Navidrome minimal playlist entries. */
fun SongEntity.isMetadataPlaceholder(): Boolean =
	artistName == PLACEHOLDER_ARTIST_NAME &&
		fileExtension.isBlank() &&
		mimeType == PLACEHOLDER_MIME_TYPE
