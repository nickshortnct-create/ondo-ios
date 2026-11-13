package com.nickshortnct.ondo.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayNextOrderTest {
	@Test fun insertionPreservesTheRestOfShuffleOrder() {
		assertEquals(listOf(4, 1, 2, 3, 5, 0), playNextShuffleOrder(listOf(2, 1, 3, 0), 1, 2, 2))
		assertEquals(listOf(0, 1, 2), playNextShuffleOrder(emptyList(), -1, 0, 3))
	}
}
