package com.nickshortnct.ondo.data.database

import androidx.room3.RoomRawQuery
import com.nickshortnct.ondo.domain.models.normalizeSearchQuery

enum class LibrarySearchTable(
	internal val entity: String,
	internal val title: String,
	internal val id: String,
	internal val fields: List<String>,
	internal val limit: Int
) {
	SONG("SongEntity", "title", "songId", listOf("title", "artistName", "albumTitle"), 200),
	ALBUM("AlbumEntity", "name", "albumId", listOf("name", "artistName"), 100),
	ARTIST("ArtistEntity", "name", "artistId", listOf("name"), 100),
	PLAYLIST("PlaylistEntity", "name", "playlistId", listOf("name"), 100)
}

// Each word can match a different field, so "Daft Punk Get Lucky" finds
// Get Lucky by Daft Punk even when offline. All words must be present.
fun librarySearchQuery(query: String, table: LibrarySearchTable): RoomRawQuery {
	val cleaned = normalizeSearchQuery(query)
	val tokens = foldForSearch(cleaned).split(' ').filter(String::isNotEmpty).distinct()
	if (tokens.isEmpty()) return RoomRawQuery("SELECT * FROM ${table.entity} WHERE 0")
	// Keep parameter counts and SQL size bounded even for pasted paragraphs.
	val terms = if (tokens.size > 64) tokens.take(63) + tokens.drop(63).joinToString(" ") else tokens
	val text = "searchText"
	val where = terms.joinToString(" AND ") { "($text) LIKE ? ESCAPE '\\' COLLATE NOCASE" }
	val sql = "SELECT * FROM ${table.entity} WHERE $where " +
		"ORDER BY CASE WHEN ${table.title} LIKE ? ESCAPE '\\' THEN 0 " +
		"WHEN ${table.title} LIKE ? ESCAPE '\\' THEN 1 ELSE 2 END, " +
		"${table.title} COLLATE NOCASE, ${table.id} LIMIT ${table.limit}"
	val args = terms.map { "%${escapeSearchLike(it)}%" } +
		listOf(escapeSearchLike(cleaned), "${escapeSearchLike(cleaned)}%")
	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, value -> statement.bindText(index + 1, value) }
	}
}

private fun escapeSearchLike(query: String): String = query
	.replace("\\", "\\\\")
	.replace("%", "\\%")
	.replace("_", "\\_")
