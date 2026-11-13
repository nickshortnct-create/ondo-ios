package com.nickshortnct.ondo.ondo

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import com.nickshortnct.ondo.data.database.foldForSearch
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSong

/**
 * Output of the weekly curator on the server (server/curator): Claude-made mixes and a category index
 * for every song in the library. Mixes become real playlists through [CurationManager].
 */
@Immutable
@Serializable
data class Curation(
	val version: Int = 1,
	val week: String? = null,
	val generatedAt: String? = null,
	val model: String? = null,
	val categories: List<CurationCategory> = emptyList(),
	val mixes: List<CuratedMix> = emptyList(),
	/** Song id → indexes into [categories]. */
	val songCategories: Map<String, List<Int>> = emptyMap()
)

@Immutable
@Serializable
data class CurationCategory(
	val key: String,
	val label: String,
	val emoji: String = "",
	/** "mood" or "genre". */
	val kind: String = "mood"
)

@Immutable
@Serializable
data class CuratedMix(
	val slot: String,
	val section: String,
	val title: String,
	val description: String = "",
	val emoji: String = "🎶",
	val color: String = "#7C4DFF",
	val songIds: List<String> = emptyList(),
	/** Version of the generated cover art (served at /v1/covers/<slot>.jpg); null when there's none. */
	val cover: String? = null
)

/** The marker a mix playlist carries at the end of its comment, so every device recognises it. */
@Immutable
data class MixTag(val slot: String, val week: String, val emoji: String, val color: String) {
	val isMood get() = slot.startsWith("mood")
}

private val mixTagPattern = Regex("""\s*\[ondo-mix:([a-z]+-\d+):([^:\]]*):([^:\]]*):(#[0-9A-Fa-f]{6})]\s*$""")

fun parseMixTag(comment: String?): MixTag? {
	val match = mixTagPattern.find(comment ?: return null) ?: return null
	val (slot, week, emoji, color) = match.destructured
	return MixTag(slot, week, emoji, color)
}

fun mixComment(mix: CuratedMix, week: String): String {
	val emoji = mix.emoji.filterNot { it == ':' || it == ']' }
	val color = mix.color.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) } ?: "#7C4DFF"
	return "${mix.description.trim()}\n[ondo-mix:${mix.slot}:$week:$emoji:$color]"
}

val DomainPlaylist.mixTag: MixTag? get() = parseMixTag(comment)

/** The comment as people should see it: without the mix marker. */
val DomainPlaylist.displayComment: String?
	get() = comment?.replace(mixTagPattern, "")?.trim()?.ifBlank { null }

/** "Oct 12" for "2026-W42": the Monday the mix week starts. Falls back to the raw id. */
fun mixWeekLabel(week: String): String {
	val match = Regex("""(\d{4})-W(\d{1,2})""").matchEntire(week) ?: return week
	val (year, number) = match.destructured
	return runCatching {
		val jan4 = kotlinx.datetime.LocalDate(year.toInt(), 1, 4)
		val monday = jan4.toEpochDays() - jan4.dayOfWeek.ordinal + (number.toInt() - 1) * 7
		val date = kotlinx.datetime.LocalDate.fromEpochDays(monday)
		val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
		"${months[date.month.ordinal]} ${date.day}"
	}.getOrDefault(week)
}

fun displayPlaylistComment(comment: String?): String? = comment?.replace(mixTagPattern, "")?.trim()?.ifBlank { null }

// ----------------------------------------------------------------- filtering

/** A chip in the playlist filter bar: a mood or genre with how many of the playlist's songs it matches. */
@Immutable
data class SongFilterChip(val key: String, val label: String, val emoji: String, val kind: String, val count: Int)

/** Category keys for each song: the server's index first, then a guess from the song's own genre tags. */
class SongCategoryIndex(private val curation: Curation?) {
	private val byKey = curation?.categories.orEmpty().associateBy { it.key }

	fun keysFor(song: DomainSong): Set<String> {
		val indexes = curation?.songCategories?.get(song.id)
		if (indexes != null) {
			return indexes.mapNotNullTo(mutableSetOf()) { curation.categories.getOrNull(it)?.key }
		}
		return guessCategories(song)
	}

	fun category(key: String): CurationCategory = byKey[key] ?: FALLBACK_CATEGORIES.first { it.key == key }

	val categories: List<CurationCategory>
		get() = curation?.categories?.takeIf { it.isNotEmpty() } ?: FALLBACK_CATEGORIES
}

/**
 * Chips worth showing for [songs]: categories matching at least two songs but not every song (a chip that
 * keeps everything filters nothing). Moods come before genres, each ordered by how many songs they match.
 */
fun buildFilterChips(songs: List<DomainSong>, index: SongCategoryIndex, maxChips: Int = 16): List<SongFilterChip> {
	if (songs.size < 4) return emptyList()
	val counts = mutableMapOf<String, Int>()
	songs.forEach { song -> index.keysFor(song).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
	val order = index.categories.withIndex().associate { (i, c) -> c.key to i }
	return counts.filter { (key, count) -> count >= 2 && count < songs.size && key != "other" && key in order }
		.map { (key, count) ->
			val category = index.category(key)
			SongFilterChip(key, category.label, category.emoji, category.kind, count)
		}
		.sortedWith(compareBy<SongFilterChip> { it.kind != "mood" }.thenByDescending { it.count }.thenBy { order[it.key] })
		.take(maxChips)
}

/** Whether [song] matches a playlist search: every word must appear in the title, artist or album. */
fun songMatchesQuery(song: DomainSong, query: String): Boolean = songMatchesWords(song, searchWords(query))

private fun searchWords(query: String) = foldForSearch(query).split(' ').filter { it.isNotBlank() }

private fun songMatchesWords(song: DomainSong, words: List<String>): Boolean {
	if (words.isEmpty()) return true
	val haystack = foldForSearch(
		listOfNotNull(song.title, song.artistName, song.albumTitle, song.artists.joinToString(" ") { it.name })
			.joinToString(" ")
	)
	return words.all { it in haystack }
}

/** Songs from [songs] (paired with their position in the full list) that pass the search and the chip. */
fun filterPlaylistSongs(
	songs: List<DomainSong>,
	query: String,
	categoryKey: String?,
	index: SongCategoryIndex
): List<IndexedValue<DomainSong>> {
	val words = searchWords(query)
	return songs.withIndex().filter { (_, song) ->
		(categoryKey == null || categoryKey in index.keysFor(song)) && songMatchesWords(song, words)
	}
}

// Used before the server has categorised a song (new downloads, or the server is unreachable).
internal val FALLBACK_CATEGORIES = listOf(
	CurationCategory("chill", "Chill", "🌊"),
	CurationCategory("relax", "Relaxing", "🛋️"),
	CurationCategory("hype", "Hype", "🔥"),
	CurationCategory("gym", "Gym", "💪"),
	CurationCategory("party", "Party", "🎉"),
	CurationCategory("feel-good", "Feel Good", "☀️"),
	CurationCategory("focus", "Focus", "🎧"),
	CurationCategory("late-night", "Late Night", "🌙"),
	CurationCategory("drive", "Drive", "🚗"),
	CurationCategory("sad", "In My Feels", "🌧️"),
	CurationCategory("love", "Love", "❤️"),
	CurationCategory("throwback", "Throwback", "📼"),
	CurationCategory("hip-hop", "Hip-Hop", "🎤", "genre"),
	CurationCategory("rnb", "R&B", "💜", "genre"),
	CurationCategory("pop", "Pop", "✨", "genre"),
	CurationCategory("rock", "Rock", "🎸", "genre"),
	CurationCategory("alternative", "Alternative", "🌀", "genre"),
	CurationCategory("indie", "Indie", "🪴", "genre"),
	CurationCategory("electronic", "Electronic", "🎛️", "genre"),
	CurationCategory("dance", "Dance", "🪩", "genre"),
	CurationCategory("country", "Country", "🤠", "genre"),
	CurationCategory("latin", "Latin", "💃", "genre"),
	CurationCategory("afrobeats", "Afrobeats", "🌍", "genre"),
	CurationCategory("soul", "Soul & Funk", "🎷", "genre"),
	CurationCategory("jazz", "Jazz", "🎺", "genre"),
	CurationCategory("metal", "Metal", "🤘", "genre"),
	CurationCategory("punk", "Punk", "⚡", "genre"),
	CurationCategory("folk", "Folk & Acoustic", "🪕", "genre"),
	CurationCategory("classical", "Classical", "🎻", "genre"),
	CurationCategory("soundtrack", "Soundtrack", "🎬", "genre"),
	CurationCategory("reggae", "Reggae", "🌴", "genre"),
)

// Ordered: the first family whose keywords match wins.
private val GENRE_KEYWORDS = listOf(
	"hip-hop" to listOf("hip hop", "hip-hop", "rap", "trap", "drill", "grime", "boom bap", "plugg", "rage"),
	"rnb" to listOf("r&b", "rnb", "r & b", "neo soul", "neo-soul"),
	"metal" to listOf("metal", "core"),
	"punk" to listOf("punk", "emo"),
	"electronic" to listOf("electronic", "edm", "techno", "dubstep", "drum and bass", "dnb", "idm", "synth", "ambient", "garage"),
	"dance" to listOf("house", "dance", "disco", "club"),
	"indie" to listOf("indie", "bedroom pop", "shoegaze", "dream pop"),
	"alternative" to listOf("alternative", "alt "),
	"rock" to listOf("rock", "grunge"),
	"country" to listOf("country", "americana", "bluegrass"),
	"latin" to listOf("latin", "reggaeton", "salsa", "bachata", "corrido", "cumbia"),
	"afrobeats" to listOf("afro", "amapiano"),
	"reggae" to listOf("reggae", "dancehall", "dub"),
	"jazz" to listOf("jazz", "bossa"),
	"soul" to listOf("soul", "funk", "motown", "gospel"),
	"folk" to listOf("folk", "acoustic", "singer-songwriter"),
	"classical" to listOf("classical", "orchestral", "baroque", "piano"),
	"soundtrack" to listOf("soundtrack", "score", "musical"),
	"pop" to listOf("pop"),
)

private val MOOD_HINTS = listOf(
	"hype" to listOf("trap", "drill", "rage", "hardcore", "metal", "phonk", "crunk"),
	"gym" to listOf("trap", "drill", "rage", "phonk", "metal", "drum and bass", "hardstyle"),
	"party" to listOf("dance", "house", "club", "reggaeton", "disco", "edm", "dancehall", "amapiano"),
	"chill" to listOf("lo-fi", "lofi", "chill", "downtempo", "neo soul", "bedroom", "cloud rap", "trip hop"),
	"relax" to listOf("ambient", "acoustic", "classical", "jazz", "new age", "piano"),
	"focus" to listOf("ambient", "lo-fi", "lofi", "instrumental", "idm", "post-rock", "classical"),
	"sad" to listOf("sad", "emo", "slowcore"),
	"late-night" to listOf("trip hop", "alternative r&b", "darkwave", "synthwave"),
)

internal fun guessCategories(song: DomainSong): Set<String> {
	val tags = (song.genres + listOfNotNull(song.genre) + song.moods).map { " ${it.lowercase()} " }
	if (tags.isEmpty()) return emptySet()
	val result = mutableSetOf<String>()
	GENRE_KEYWORDS.firstOrNull { (_, words) -> tags.any { tag -> words.any { it in tag } } }?.let { result += it.first }
	MOOD_HINTS.forEach { (mood, words) -> if (tags.any { tag -> words.any { it in tag } }) result += mood }
	song.moods.map { it.lowercase() }.forEach { mood ->
		FALLBACK_CATEGORIES.firstOrNull { it.kind == "mood" && (it.key == mood || it.label.lowercase() == mood) }?.let { result += it.key }
	}
	return result
}
