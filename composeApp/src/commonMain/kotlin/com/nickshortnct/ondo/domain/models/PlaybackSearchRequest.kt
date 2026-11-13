package com.nickshortnct.ondo.domain.models

/** Structured metadata supplied by a voice assistant, independent of Android APIs. */
data class PlaybackSearchRequest(
	val query: String,
	val focus: ResultKind? = null,
	val title: String = "",
	val artist: String = "",
	val album: String = "",
	val playlist: String = ""
) {
	val lookupTerm: String
		get() {
			val focused = when (focus) {
				ResultKind.SONG -> title
				ResultKind.ARTIST -> artist
				ResultKind.ALBUM -> album
				ResultKind.PLAYLIST -> playlist
				null -> ""
			}
			return normalizeSearchQuery(
				listOf(focused, title, playlist, album, artist, query).firstOrNull { it.isNotBlank() }.orEmpty()
			)
		}
}

/** Long arithmetic prevents a remote browser's page request from overflowing. */
fun <T> searchResultPage(items: List<T>, page: Int, pageSize: Int): List<T> {
	if (page < 0 || pageSize <= 0) return emptyList()
	val first = page.toLong() * pageSize.toLong()
	if (first >= items.size) return emptyList()
	val end = (first + pageSize).coerceAtMost(items.size.toLong())
	return items.subList(first.toInt(), end.toInt())
}
