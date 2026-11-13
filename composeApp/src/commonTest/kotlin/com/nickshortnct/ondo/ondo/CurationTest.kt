package com.nickshortnct.ondo.ondo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainSong

class CurationTest {
	private fun song(id: String, title: String = id, artist: String = "Artist", genres: List<String> = emptyList()) = DomainSong(
		id = id, title = title, artistName = artist, artistId = "a", albumTitle = "Album", albumId = "al",
		parentId = null, comment = null, trackNumber = null, discNumber = null, isrc = emptyList(), year = null,
		genre = genres.firstOrNull(), genres = genres, moods = emptyList(), duration = 180.seconds, bpm = null,
		contributors = emptyList(), userRating = null, averageRating = null, bitRate = null, bitDepth = null,
		sampleRate = null, audioChannelCount = null, replayGain = null, fileSize = 0, fileExtension = "flac",
		mimeType = "audio/flac", filePath = null, starredAt = null, coverArtId = null, musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown, artists = emptyList(), albumArtists = emptyList()
	)

	private val curation = Curation(
		week = "2026-W42",
		categories = listOf(
			CurationCategory("chill", "Chill", "🌊"),
			CurationCategory("gym", "Gym", "💪"),
			CurationCategory("hip-hop", "Hip-Hop", "🎤", "genre"),
			CurationCategory("rnb", "R&B", "💜", "genre"),
		),
		songCategories = mapOf(
			"1" to listOf(0, 3), "2" to listOf(0, 2), "3" to listOf(1, 2), "4" to listOf(1, 2), "5" to listOf(1, 2)
		)
	)

	@Test fun mixMarkerRoundTripsAndIsHiddenFromTheComment() {
		val mix = CuratedMix("mood-2", "mood", "After Midnight", "Slow cuts for late nights.", "🌙", "#7C4DFF")
		val comment = mixComment(mix, "2026-W42")
		assertEquals(MixTag("mood-2", "2026-W42", "🌙", "#7C4DFF"), parseMixTag(comment))
		assertEquals("Slow cuts for late nights.", displayPlaylistComment(comment))
		assertNull(parseMixTag("Just a normal playlist [ondo-mix:bad]"))
		assertEquals("Road trip", displayPlaylistComment("Road trip"))
	}

	@Test fun chipsSkipCategoriesThatMatchEverythingOrOneSong() {
		val songs = (1..5).map { song(it.toString()) }
		val chips = buildFilterChips(songs, SongCategoryIndex(curation))
		// gym (3 songs) and chill (2) are moods, so they come first; hip-hop matches 4 of 5; R&B only 1.
		assertEquals(listOf("gym", "chill", "hip-hop"), chips.map { it.key })
		assertEquals(3, chips.first().count)
	}

	@Test fun filteringKeepsPositionsAndCombinesSearchWithChip() {
		val songs = listOf(song("1", "Sunday Morning"), song("2", "Night Drive"), song("3", "Lift Off", "Ye"), song("4"), song("5"))
		val index = SongCategoryIndex(curation)
		val gym = filterPlaylistSongs(songs, "", "gym", index)
		assertEquals(listOf(2, 3, 4), gym.map { it.index })
		assertEquals(listOf("3"), filterPlaylistSongs(songs, "lift ye", "gym", index).map { it.value.id })
		assertEquals(listOf("1"), filterPlaylistSongs(songs, "SUNDAY", null, index).map { it.value.id })
		assertTrue(filterPlaylistSongs(songs, "nothing like this", null, index).isEmpty())
	}

	@Test fun songsTheServerHasNotSeenFallBackToTheirGenreTags() {
		val index = SongCategoryIndex(curation)
		assertEquals(setOf("hip-hop", "hype", "gym"), index.keysFor(song("new", genres = listOf("Trap"))))
		assertEquals(setOf("rnb"), index.keysFor(song("new2", genres = listOf("Contemporary R&B"))))
		assertTrue(index.keysFor(song("new3")).isEmpty())
	}

	@Test fun weekIdBecomesTheMondayItStarts() {
		assertEquals("Oct 12", mixWeekLabel("2026-W42"))
		assertEquals("Dec 29", mixWeekLabel("2026-W01"))
		assertEquals("soon", mixWeekLabel("soon"))
	}
}
