package com.nickshortnct.ondo.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSearchRequestTest {
	@Test
	fun voiceFocusUsesTheStructuredAlbumOrArtistRatherThanAmbiguousSpeech() {
		assertEquals("Discovery", PlaybackSearchRequest("play discovery by daft punk", ResultKind.ALBUM,
			artist = "Daft Punk", album = "Discovery").lookupTerm)
		assertEquals("Daft Punk", PlaybackSearchRequest("play some daft punk", ResultKind.ARTIST,
			artist = "  Daft   Punk ").lookupTerm)
	}

	@Test
	fun blankRequestsRemainBlankForPlaybackResumptionAndRawQueriesStillWork() {
		assertEquals("", PlaybackSearchRequest("  ").lookupTerm)
		assertEquals("Hello Adele", PlaybackSearchRequest("  Hello   Adele ").lookupTerm)
		assertEquals("Hello", PlaybackSearchRequest("listen to hello by adele", title = "Hello", artist = "Adele").lookupTerm)
	}

	@Test
	fun pagingReturnsEachResultOnceAndHandlesTheLastPartialPage() {
		val items = (1..7).toList()
		assertEquals(listOf(1, 2, 3), searchResultPage(items, 0, 3))
		assertEquals(listOf(4, 5, 6), searchResultPage(items, 1, 3))
		assertEquals(listOf(7), searchResultPage(items, 2, 3))
		assertTrue(searchResultPage(items, 3, 3).isEmpty())
	}

	@Test
	fun invalidAndOverflowingPageRequestsCannotRepeatTheFirstPage() {
		val items = listOf(1, 2, 3)
		assertTrue(searchResultPage(items, -1, 3).isEmpty())
		assertTrue(searchResultPage(items, 0, 0).isEmpty())
		assertTrue(searchResultPage(items, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
		assertEquals(items, searchResultPage(items, 0, Int.MAX_VALUE))
	}
}
