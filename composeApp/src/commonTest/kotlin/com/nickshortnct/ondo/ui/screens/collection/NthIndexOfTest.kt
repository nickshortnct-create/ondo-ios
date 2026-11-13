package com.nickshortnct.ondo.ui.screens.collection.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals

class NthIndexOfTest {
	@Test
	fun findsTheRequestedCopyOfARepeatedSong() {
		val ids = listOf("a", "b", "a", "c", "a")

		assertEquals(0, nthIndexOf(ids, "a", 0))
		assertEquals(2, nthIndexOf(ids, "a", 1))
		assertEquals(4, nthIndexOf(ids, "a", 2))
	}

	@Test
	fun aSongThatIsNoLongerThereIsNotFound() {
		assertEquals(-1, nthIndexOf(listOf("a", "b"), "z", 0))
		assertEquals(-1, nthIndexOf(listOf("a", "b"), "a", 1))
	}
}
