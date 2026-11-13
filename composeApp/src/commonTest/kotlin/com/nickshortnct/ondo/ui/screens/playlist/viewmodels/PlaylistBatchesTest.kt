package com.nickshortnct.ondo.ui.screens.playlist.viewmodels

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaylistBatchesTest {
	@Test fun boundsRequestsAndPreservesOrder() = runTest {
		val ids = (0 until 251).map(Int::toString)
		val batches = mutableListOf<List<String>>()
		appendPlaylistBatches(ids) { batches += it }
		assertEquals(listOf(100, 100, 51), batches.map { it.size })
		assertEquals(ids, batches.flatten())
	}
	@Test fun reportsPartialFailureAndStops() = runTest {
		var calls = 0
		val error = assertFailsWith<IllegalStateException> {
			appendPlaylistBatches((0 until 251).map(Int::toString)) {
				calls++
				if (calls == 2) error("server unavailable")
			}
		}
		assertEquals(2, calls)
		assertTrue(error.message!!.startsWith("100 songs"))
	}
}
