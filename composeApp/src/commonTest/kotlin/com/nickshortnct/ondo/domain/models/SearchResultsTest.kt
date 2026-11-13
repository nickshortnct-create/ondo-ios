package com.nickshortnct.ondo.domain.models

import com.nickshortnct.ondo.ondo.MusicResult
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class SearchResultsTest {
	private fun hit(title: String, artist: String = "Artist", service: String = "SoulSync", id: String = title, kind: String = "track") =
		MusicResult(service, kind, id, title, artist, JsonNull)

	@Test
	fun exactDiscoveryTitleOutranksBroadLibraryArtistMatch() {
		val results = mergeSearchResults(
			"hello",
			listOf(DomainArtist("broad", "Oh Hello")),
			listOf(hit("Hello"), hit("Hello Again"))
		)
		assertEquals("Hello", results.first().title)
		assertFalse(results.first().onDeck)
		assertEquals("Oh Hello", results.last().title)
	}

	@Test
	fun legitimateArtistNamesWithCollaborationPunctuationStaySearchable() {
		val names = listOf("Earth, Wind & Fire", "Florence and the Machine", "AC/DC", "SLAYR & Chris Marek")
		val artists = names.mapIndexed { index, name -> DomainArtist(index.toString(), name) }
		assertEquals(names.toSet(), mergeSearchResults("fire", artists, emptyList()).map { it.title }.toSet())
	}

	@Test
	fun libraryArtistWinsOverDuplicateDiscoveryButDoesNotEraseAnotherArtist() {
		val library = DomainArtist("library", "Earth, Wind & Fire")
		val results = mergeSearchResults("earth", listOf(library), listOf(
			hit("EARTH, WIND & FIRE", "", kind = "artist"),
			hit("Earth", "Earth, Wind & Fire", kind = "artist")
		))
		assertEquals(2, results.size)
		assertEquals(1, results.count { it.onDeck })
		assertTrue(results.any { it.title == "Earth" && !it.onDeck })
	}

	@Test
	fun providerDuplicatesKeepAlternateSourcesAndRecordingVersions() {
		val results = mergeSearchResults("hello", emptyList(), listOf(
			hit("Hello", service = "SoulSync"),
			hit("HELLO", service = "Squid"),
			hit("Hello (Live)", service = "Squid"),
			hit("Hello", artist = "Another Artist", service = "Squid")
		))
		assertEquals(3, results.size)
		val studio = results.single { it.title == "Hello" && it.artist == "Artist" }
		assertEquals(listOf("Squid"), studio.alternativeSources.map { it.service })
		assertTrue(results.any { it.title == "Hello (Live)" })
	}

	@Test
	fun missingArtistCreditsAndWordBoundariesDoNotCreateFalseDuplicates() {
		val results = mergeSearchResults("song", emptyList(), listOf(
			hit("Song", artist = "", service = "SoulSync", id = "one"),
			hit("Song", artist = "", service = "Squid", id = "two"),
			hit("A B", artist = "Artist"),
			hit("AB", artist = "Artist")
		))
		assertEquals(4, results.size)
		assertEquals(4, results.map { it.key }.distinct().size)
		assertNotEquals(normalizeForMatch("A B"), normalizeForMatch("AB"))
	}

	@Test
	fun stableKeysSurviveProviderCompletionOrder() {
		val early = mergeSearchResults("hello", emptyList(), listOf(hit("Hello", service = "Squid"))).single()
		val completed = mergeSearchResults("hello", emptyList(), listOf(
			hit("Hello", service = "SoulSync"), hit("Hello", service = "Squid")
		)).single()
		assertEquals(early.key, completed.key)
	}

	@Test
	fun knownMusicBrainzIdentitiesKeepSameNamedArtistsDistinct() {
		val modernId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
		val historicalId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
		val library = DomainArtist("historical", "Ken Carson", musicBrainzId = historicalId)
		val discovery = hit("Ken Carson", "Ken Carson", "Lidarr", kind = "artist").copy(
			raw = JsonObject(mapOf("foreignArtistId" to JsonPrimitive(modernId)))
		)
		val results = mergeSearchResults("ken carson", listOf(library), listOf(discovery))
		assertEquals(2, results.size)
		assertEquals(2, results.map { it.key }.distinct().size)
		val duplicateFromAnotherSource = hit("Ken Carson", "Ken Carson", "SoulSync", kind = "artist")
		assertEquals(1, mergeSearchResults("ken carson", emptyList(), listOf(discovery, duplicateFromAnotherSource)).size)
	}

	@Test
	fun singleTrackAlbumsAndPlaylistsAreNotSilentlyDropped() {
		val album = DomainAlbum(
			"single", "Only One", "Artist", "artist", 2026, "cover",
			null, emptyList(), 1, null, Instant.fromEpochMilliseconds(0), null,
			null, 0, null, null, null, emptyList()
		)
		val playlist = DomainPlaylist(
			"playlist", "Family Mix", "Family", null, null, null, 0, Duration.ZERO,
			Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0),
			false, false, emptyList(), null, emptyList()
		)
		val results = mergeSearchResults("family", listOf(album, playlist), emptyList())
		assertEquals(setOf(ResultKind.ALBUM, ResultKind.PLAYLIST), results.map { it.kind }.toSet())
		assertTrue(results.all { it.onDeck })
	}

	@Test
	fun historyStoresIntentionalQueriesWithoutCaseOrWhitespaceDuplicates() {
		val history = recentSearchesWith(listOf("Taylor Swift", "Radiohead"), "  taylor   swift ")
		assertEquals(listOf("taylor swift", "Radiohead"), history)
		assertEquals(history, recentSearchesWith(history, "   "))
		assertEquals(10, recentSearchesWith((1..20).map(Int::toString), "new").size)
	}
}
