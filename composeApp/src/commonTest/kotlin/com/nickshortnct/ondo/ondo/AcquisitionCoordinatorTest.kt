package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AcquisitionCoordinatorTest {
    @Test
    fun scrollingAwayDoesNotCancelTheSharedServerRequest() = runTest {
        val coordinator = AcquisitionCoordinator(backgroundScope)
        val finish = CompletableDeferred<Unit>()
        var requests = 0
        val firstRow = launch {
            coordinator.submit("item:song", "track:song:artist") {
                requests++
                finish.await()
                "Accepted"
            }
        }
        runCurrent()
        assertEquals(setOf("track:song:artist"), coordinator.pendingItems.value)
        firstRow.cancelAndJoin()

        val reopenedRow = async {
            coordinator.submit("item:song", "track:song:artist") { error("A duplicate request must not run") }
        }
        runCurrent()
        finish.complete(Unit)
        assertEquals("Accepted", reopenedRow.await())
        runCurrent()
        assertEquals(1, requests)
        assertTrue(coordinator.pendingItems.value.isEmpty())
    }

    @Test
    fun failedRequestsAreRemovedSoTheUserCanRetry() = runTest {
        val coordinator = AcquisitionCoordinator(backgroundScope)
        assertFailsWith<IllegalStateException> {
            coordinator.submit("retry:task", "track:song:artist") { error("Source unavailable") }
        }
        runCurrent()
        assertTrue(coordinator.pendingItems.value.isEmpty())
        assertEquals("Accepted", coordinator.submit("retry:task", "track:song:artist") { "Accepted" })
        runCurrent()
        assertTrue(coordinator.pendingItems.value.isEmpty())
    }

    @Test
    fun finishingOneOperationDoesNotClearAnotherOperationForTheSameSong() = runTest {
        val coordinator = AcquisitionCoordinator(backgroundScope)
        val finishSecond = CompletableDeferred<Unit>()
        val second = async { coordinator.submit("retry:task", "track:song:artist") { finishSecond.await(); "Accepted" } }
        runCurrent()
        assertEquals("Accepted", coordinator.submit("item:song", "track:song:artist") { "Accepted" })
        runCurrent()
        assertEquals(setOf("track:song:artist"), coordinator.pendingItems.value)
        finishSecond.complete(Unit)
        second.await()
        runCurrent()
        assertTrue(coordinator.pendingItems.value.isEmpty())
    }
}
