package com.nickshortnct.ondo.domain.repositories

import com.nickshortnct.ondo.ondo.ConnectTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreQueueTest {
	private fun songs(vararg ids: String) = ids.associateWith { ConnectTrack(it).song() }

	@Test
	fun theIndexStillPointsAtTheSameSongWhenEarlierSongsAreGone() {
		val (queue, index) = restoreQueue(listOf("a", "b", "c", "d"), savedIndex = 2, songsById = songs("b", "c", "d"))

		assertEquals(listOf("b", "c", "d"), queue.map { it.id })
		assertEquals("c", queue[index].id)
	}

	@Test
	fun aMissingCurrentSongFallsToTheNextSurvivor() {
		val (queue, index) = restoreQueue(listOf("a", "b", "c"), savedIndex = 1, songsById = songs("a", "c"))

		assertEquals("c", queue[index].id)
	}

	@Test
	fun anEmptyResultHasNoCurrentIndex() {
		val (queue, index) = restoreQueue(listOf("a"), savedIndex = 0, songsById = emptyMap())

		assertEquals(emptyList(), queue)
		assertEquals(-1, index)
	}
}
