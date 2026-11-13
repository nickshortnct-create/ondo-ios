package com.nickshortnct.ondo.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SyncMergeTest {
	@Test
	fun successfulPlaylistsMergeWithoutLoss() = runTest {
		val outcomes = (1..50).map { "p$it" to Result.success(setOf("s$it", "shared")) }

		val merged = mergePlaylistOutcomes(outcomes, cachedSongIds = { emptyList() })

		assertTrue(merged.complete)
		assertEquals(51, merged.songIds.size)
	}

	@Test
	fun failedPlaylistKeepsItsCachedSongsAndMarksTheRunIncomplete() = runTest {
		val outcomes = listOf(
			"ok" to Result.success(setOf("a")),
			"broken" to Result.failure<Set<String>>(IllegalStateException("offline"))
		)
		val failures = mutableListOf<String>()

		val merged = mergePlaylistOutcomes(
			outcomes,
			cachedSongIds = { if (it == "broken") listOf("b", "c") else emptyList() },
			onFailure = { id, _ -> failures += id }
		)

		assertFalse(merged.complete)
		assertEquals(setOf("a", "b", "c"), merged.songIds)
		assertEquals(listOf("broken"), failures)
	}
}
