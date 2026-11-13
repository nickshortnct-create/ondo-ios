package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSourcesTest {
	private fun hit(source: String) = MusicResult(source, "track", source, "Song", "Artist", JsonNull)

	@Test
	fun usefulMatchesAppearBeforeSlowSourcesFinish() = runTest {
		val slow = CompletableDeferred<Unit>()
		val snapshots = mutableListOf<DownloadSearchOutcome>()
		val collector = launch {
			searchSources(listOf("Slow", "Fast")) { source ->
				if (source == "Slow") slow.await()
				listOf(hit(source))
			}.toList(snapshots)
		}
		runCurrent()
		assertEquals(listOf("Fast"), snapshots.last().results.map { it.service })
		assertTrue(snapshots.last().isSearching)
		slow.complete(Unit)
		collector.join()
		assertEquals(listOf("Slow", "Fast"), snapshots.last().results.map { it.service })
		assertFalse(snapshots.last().isSearching)
	}

	@Test
	fun timeoutAndFailurePreserveHealthyResultsAndHideTransportDetails() = runTest {
		val snapshots = searchSources(listOf("Healthy", "Broken", "Slow"), timeoutMillis = 100) { source ->
			when (source) {
				"Broken" -> error("https://private.example/api?key=secret")
				"Slow" -> { delay(200); listOf(hit(source)) }
				else -> listOf(hit(source))
			}
		}.toList()
		assertEquals(listOf("Healthy"), snapshots.last().results.map { it.service })
		assertEquals(2, snapshots.last().failures.size)
		assertTrue(snapshots.last().failures.any { "Slow" in it && "timed out" in it })
		assertTrue(snapshots.last().failures.none { "secret" in it || "https://" in it })
		assertFalse(snapshots.last().isSearching)
	}

	@Test
	fun cancellingAnOldQueryCancelsEveryProviderWithoutLateResults() = runTest {
		val started = mutableSetOf<String>()
		val cancelled = mutableSetOf<String>()
		val snapshots = mutableListOf<DownloadSearchOutcome>()
		val collector = launch {
			searchSources(listOf("One", "Two")) { source ->
				started += source
				try {
					awaitCancellation()
				} finally {
					cancelled += source
				}
			}.toList(snapshots)
		}
		runCurrent()
		assertEquals(setOf("One", "Two"), started)
		collector.cancelAndJoin()
		advanceUntilIdle()
		assertEquals(started, cancelled)
		assertEquals(1, snapshots.size)
		assertTrue(snapshots.single().results.isEmpty())
	}

	@Test
	fun anEmptyProviderListCompletesWithoutAnIndefiniteSpinner() = runTest {
		val snapshots = searchSources(emptyList()) { error("No request expected") }.toList()
		assertEquals(1, snapshots.size)
		assertFalse(snapshots.single().isSearching)
	}
}
