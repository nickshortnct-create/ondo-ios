package com.nickshortnct.ondo.ondo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.isEditableBy

class PlaylistPlayStatsTest {
	private data class Item(val id: String, val name: String)

	@Test fun mostPlayedFirstThenMostRecentThenAlphabetical() {
		val items = listOf(Item("a", "beta"), Item("b", "Alpha"), Item("c", "Road"), Item("d", "Gym"), Item("e", "chill"))
		val plays = mapOf(
			"c" to PlaylistPlays(count = 2, lastPlayedAt = 100),
			"d" to PlaylistPlays(count = 7, lastPlayedAt = 50),
			"e" to PlaylistPlays(count = 2, lastPlayedAt = 300),
		)
		val sorted = sortByPlays(items, plays, { it.id }, { it.name }).map { it.name }
		assertEquals(listOf("Gym", "chill", "Road", "Alpha", "beta"), sorted)
	}

	private fun playlist(owner: String, comment: String? = null, readOnly: Boolean? = false) = DomainPlaylist(
		id = "p", name = "P", owner = owner, comment = comment, coverArtId = null, songCount = 0, duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST, modifiedAt = Instant.DISTANT_PAST, public = false, readOnly = readOnly,
		allowedUsers = emptyList(), validUntil = null, songs = emptyList()
	)

	@Test fun onlyYourOwnRegularPlaylistsAreEditable() {
		assertTrue(playlist("nick").isEditableBy("Nick"))
		assertTrue(playlist("").isEditableBy("nick"))
		assertFalse(playlist("someone-else").isEditableBy("nick"))
		assertFalse(playlist("nick", readOnly = true).isEditableBy("nick"))
		assertFalse(playlist("nick", comment = "Mix\n[ondo-mix:mood-1:2026-W44:🔥:#FF0000]").isEditableBy("nick"))
	}
}
