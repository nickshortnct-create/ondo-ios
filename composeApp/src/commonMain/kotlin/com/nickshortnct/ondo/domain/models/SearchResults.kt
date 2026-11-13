package com.nickshortnct.ondo.domain.models

import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.str

/** Merge provider duplicates while preserving library identities and recording versions. */
fun mergeSearchResults(
	query: String,
	libraryItems: List<Any>,
	discoveryHits: List<MusicResult>
): List<UnifiedSearchResult> {
	val library = libraryItems.mapNotNull { item ->
		when (item) {
			is DomainArtist -> UnifiedSearchResult(ResultKind.ARTIST, item.name, item.name, true, library = item)
			is DomainAlbum -> UnifiedSearchResult(ResultKind.ALBUM, item.name, item.artistName, true, library = item)
			is DomainSong -> UnifiedSearchResult(ResultKind.SONG, item.title, item.artistName, true, library = item)
			is DomainPlaylist -> UnifiedSearchResult(ResultKind.PLAYLIST, item.name, item.owner, true, library = item)
			else -> null
		}
	}.distinctBy { it.key }
	val libraryIdentities = library.groupBy(::matchIdentity)
	val discovered = linkedMapOf<String, UnifiedSearchResult>()
	val discoveredIdentities = mutableMapOf<String, MutableList<String>>()
	for (hit in discoveryHits) {
		val kind = when (hit.kind.lowercase()) {
			"artist" -> ResultKind.ARTIST
			"album" -> ResultKind.ALBUM
			"track", "song" -> ResultKind.SONG
			else -> continue
		}
		if (hit.title.isBlank()) continue
		val result = UnifiedSearchResult(kind, hit.title, hit.artist, false, discovery = hit)
		val identity = matchIdentity(result)
		val remoteId = musicBrainzIdentity(result)
		if (identity != null && libraryIdentities[identity].orEmpty().any { local ->
			val localId = musicBrainzIdentity(local)
			remoteId.isEmpty() || localId.isEmpty() || remoteId == localId
		}) continue
		// Missing credits cannot safely identify a song or album across sources.
		val existingKey = identity?.let { discoveredIdentities[it] }.orEmpty().firstOrNull { candidateKey ->
			val existingId = musicBrainzIdentity(discovered.getValue(candidateKey))
			remoteId.isEmpty() || existingId.isEmpty() || remoteId == existingId
		}
		val key = existingKey ?: identity?.let { "$it:$remoteId" } ?: result.key
		val existing = discovered[key]
		if (existing == null) {
			discovered[key] = result
			if (identity != null) discoveredIdentities.getOrPut(identity) { mutableListOf() }.add(key)
		} else if (hit.service != existing.discovery?.service || hit.id != existing.discovery?.id) {
			discovered[key] = existing.copy(alternativeSources = existing.alternativeSources + hit)
		}
	}
	// Compute normalization/rank once per row rather than during every comparison.
	return (library + discovered.values).map { result ->
		RankedSearchResult(result, searchRelevance(query, result), result.title.lowercase(), result.artist.lowercase(), result.key)
	}.sortedWith(
		compareByDescending<RankedSearchResult> { it.relevance }
			.thenByDescending { it.result.onDeck }
			.thenBy { it.title }
			.thenBy { it.artist }
			.thenBy { it.key }
	).map { it.result }
}

private data class RankedSearchResult(
	val result: UnifiedSearchResult,
	val relevance: Int,
	val title: String,
	val artist: String,
	val key: String
)

private val musicBrainzIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

internal fun musicBrainzIdentity(result: UnifiedSearchResult): String {
	val id = when (val local = result.library) {
		is DomainArtist -> local.musicBrainzId
		is DomainAlbum -> local.musicBrainzId
		is DomainSong -> local.musicBrainzId
		else -> result.discovery?.raw?.let { raw ->
			val foreignKey = when (result.kind) {
				ResultKind.ARTIST -> "foreignArtistId"
				ResultKind.ALBUM -> "foreignAlbumId"
				else -> "musicBrainzId"
			}
			raw.str(foreignKey, raw.str("musicBrainzId", raw.str("mbid")))
		}
	}.orEmpty().lowercase()
	return id.takeIf(musicBrainzIdPattern::matches).orEmpty()
}

private fun matchIdentity(result: UnifiedSearchResult): String? {
	val title = normalizeForMatch(result.title)
	val artist = normalizeForMatch(result.artist)
	if (title.isBlank()) return null
	if (result.kind != ResultKind.ARTIST && artist.isBlank()) return null
	return "${result.kind}:$title:${if (result.kind == ResultKind.ARTIST) "" else artist}"
}

/** An exact name/title outranks a cached result that only contains the query. */
fun searchRelevance(query: String, result: UnifiedSearchResult): Int {
	val normalized = normalizeForMatch(query)
	if (normalized.isEmpty()) return 0
	val title = normalizeForMatch(result.title)
	val artist = normalizeForMatch(result.artist)
	val tokens = normalized.split(' ')
	val titleWords = title.split(' ')
	val allWords = titleWords + artist.split(' ')
	return when {
		title == normalized -> 1_000
		title.startsWith("$normalized ") -> 900
		artist == normalized -> 850
		title.startsWith(normalized) -> 800
		tokens.all { token -> titleWords.any { it.startsWith(token) } } -> 750
		tokens.all { token -> allWords.any { it.startsWith(token) } } -> 700
		title.contains(normalized) -> 650
		artist.startsWith(normalized) -> 600
		artist.contains(normalized) -> 550
		else -> 0
	}
}
