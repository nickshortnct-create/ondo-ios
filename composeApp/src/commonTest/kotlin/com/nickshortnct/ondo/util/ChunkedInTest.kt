package com.nickshortnct.ondo.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ChunkedInTest {
	@Test
	fun neverPassesMoreThanTheChunkSizeToOneQuery() = runTest {
		val ids = (1..40_000).map { "song-$it" }
		val sizes = mutableListOf<Int>()

		val result = chunkedIn(ids) { chunk ->
			sizes += chunk.size
			chunk
		}

		assertEquals(ids, result)
		assertTrue(sizes.all { it <= 900 })
		assertEquals(40_000, sizes.sum())
	}

	@Test
	fun duplicateIdsAreQueriedOnce() = runTest {
		val result = chunkedIn(listOf("a", "b", "a")) { it }
		assertEquals(listOf("a", "b"), result)
	}

	@Test
	fun emptyInputRunsNoQuery() = runTest {
		var calls = 0
		chunkedIn(emptyList()) { calls++; emptyList<String>() }
		assertEquals(0, calls)
	}
}
