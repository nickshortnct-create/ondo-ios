package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.data.database.entities.SyncActionEntity
import com.nickshortnct.ondo.data.database.entities.SyncActionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

private class TransientFailure : Exception("offline")
private class Rejected : Exception("no such song")

private class FakeStore : SyncActionStore {
	val removed = mutableListOf<Int>()
	val failures = mutableListOf<Int>()
	override suspend fun remove(id: Int) { removed += id }
	override suspend fun recordFailure(id: Int, error: String?) { failures += id }
}

class SyncQueueTest {
	private fun action(id: Int, attempts: Int = 0) =
		SyncActionEntity(id = id, actionType = SyncActionType.STAR, itemId = "song-$id", attempts = attempts)

	@Test
	fun aRejectedActionDoesNotBlockLaterActions() = runTest {
		val store = FakeStore()
		val sent = mutableListOf<Int>()

		drainActions(
			actions = listOf(action(1), action(2)),
			isOnline = { true },
			send = { if (it.id == 1) throw Rejected() else sent += it.id },
			store = store,
			isRejection = { it is Rejected },
			log = { _, _ -> }
		)

		assertEquals(listOf(2), sent)
		assertEquals(listOf(1), store.failures)
		assertEquals(listOf(2), store.removed)
	}

	@Test
	fun anActionIsDroppedAfterThreeRejections() = runTest {
		val store = FakeStore()

		drainActions(
			actions = listOf(action(1, attempts = MAX_ACTION_ATTEMPTS - 1)),
			isOnline = { true },
			send = { throw Rejected() },
			store = store,
			isRejection = { it is Rejected },
			log = { _, _ -> }
		)

		assertEquals(listOf(1), store.removed)
		assertEquals(emptyList(), store.failures)
	}

	@Test
	fun aNetworkFailureStopsTheRunAndKeepsEverythingQueued() = runTest {
		val store = FakeStore()
		val sent = mutableListOf<Int>()

		drainActions(
			actions = listOf(action(1), action(2)),
			isOnline = { true },
			send = { if (it.id == 1) throw TransientFailure() else sent += it.id },
			store = store,
			isRejection = { it is Rejected },
			log = { _, _ -> }
		)

		assertEquals(emptyList(), sent)
		assertEquals(emptyList(), store.removed)
		assertEquals(emptyList(), store.failures)
	}
}
