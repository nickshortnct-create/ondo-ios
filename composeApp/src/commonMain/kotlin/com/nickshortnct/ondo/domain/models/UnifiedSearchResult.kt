package com.nickshortnct.ondo.domain.models

import com.nickshortnct.ondo.ondo.MusicResult

enum class ResultKind { ARTIST, ALBUM, SONG, PLAYLIST }

// One row in the unified search screen -- either something already on decknas
// (`library` holds a DomainArtist/DomainAlbum/DomainSong) or something a
// downloader found that isn't (`discovery` holds the raw hit to hand to
// OndoDownloads). Never both: a discovery hit that matches a library item is
// dropped in favor of the library row, see UnifiedSearchRepository.
data class UnifiedSearchResult(
	val kind: ResultKind,
	val title: String,
	val artist: String,
	val onDeck: Boolean,
	val library: Any? = null,
	val discovery: MusicResult? = null,
	// Keep alternate sources when two providers find the same recording.
	val alternativeSources: List<MusicResult> = emptyList()
) {
	val key: String
		get() = when (val item = library) {
			is DomainArtist -> "artist:library:${item.id}"
			is DomainAlbum -> "album:library:${item.id}"
			is DomainSong -> "song:library:${item.id}"
			is DomainPlaylist -> "playlist:library:${item.id}"
			else -> if (normalizeForMatch(title).isNotEmpty() && (kind == ResultKind.ARTIST || artist.isNotBlank())) {
				"$kind:discovery:${normalizeForMatch(title)}:${normalizeForMatch(artist)}:${musicBrainzIdentity(this)}"
			} else {
				"$kind:${discovery?.service}:${discovery?.id.orEmpty()}:${title.lowercase()}"
			}
		}
}

// Preserve word boundaries and version labels: "A B" is not "AB", and a
// live recording must not be collapsed into the studio version.
private val matchApostrophes = Regex("['’]")
private val matchSeparators = Regex("[^\\p{L}\\p{N}]+")
private val queryWhitespace = Regex("\\s+")

fun normalizeForMatch(s: String): String = s.lowercase()
	.replace(matchApostrophes, "")
	.replace(matchSeparators, " ")
	.trim()

fun normalizeSearchQuery(query: String): String = query.trim().replace(queryWhitespace, " ")

fun recentSearchesWith(history: List<String>, query: String): List<String> {
	val cleaned = normalizeSearchQuery(query)
	if (cleaned.isEmpty()) return history
	return (listOf(cleaned) + history.filterNot { it.equals(cleaned, ignoreCase = true) }).take(10)
}
