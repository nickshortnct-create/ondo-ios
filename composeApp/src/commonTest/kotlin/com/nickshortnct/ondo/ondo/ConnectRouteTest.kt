package com.nickshortnct.ondo.ondo

import com.nickshortnct.ondo.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectRouteTest {
	private fun state(current: Int) = ConnectSnapshot(
		queue = listOf("a", "b", "c", "d").map { ConnectTrack(it) },
		currentIndex = current
	).state()

	@Test
	fun removingATrackBeforeTheCurrentOneKeepsTheSameSongCurrent() {
		val result = state(current = 2).withoutQueueItem(0)

		assertEquals(listOf("b", "c", "d"), result.queue.map { it.id })
		assertEquals("c", result.currentSong?.id)
		assertEquals(1, result.currentIndex)
	}

	@Test
	fun removingATrackAfterTheCurrentOneLeavesTheCurrentSongAlone() {
		val result = state(current = 1).withoutQueueItem(3)

		assertEquals("b", result.currentSong?.id)
		assertEquals(1, result.currentIndex)
	}

	@Test
	fun removingTheCurrentTrackMovesToTheNextOne() {
		val result = state(current = 1).withoutQueueItem(1)

		assertEquals("c", result.currentSong?.id)
	}

	@Test
	fun removingTheLastRemainingTrackClearsTheCurrentSong() {
		val result = PlayerUiState(
			queue = listOf(ConnectTrack("only").song()), currentIndex = 0, currentSong = ConnectTrack("only").song()
		).withoutQueueItem(0)

		assertEquals(-1, result.currentIndex)
		assertEquals(null, result.currentSong)
	}

	@Test
	fun repeatCyclesInTheSameOrderAsTheLocalPlayer() {
		// off -> all -> one -> off (Player.REPEAT_MODE_*: off 0, one 1, all 2)
		assertEquals(2, nextRepeatMode(0))
		assertEquals(1, nextRepeatMode(2))
		assertEquals(0, nextRepeatMode(1))
	}
}
