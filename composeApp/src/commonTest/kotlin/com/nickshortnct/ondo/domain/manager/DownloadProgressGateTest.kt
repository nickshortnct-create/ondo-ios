package com.nickshortnct.ondo.domain.manager

import kotlin.test.*

class DownloadProgressGateTest {
	@Test fun persistsByProgressOrTimeAndAlwaysFinishes() {
		val gate = DownloadProgressGate(0)
		assertFalse(gate.shouldPersist(0.01f, 100))
		assertTrue(gate.shouldPersist(0.1f, 200))
		assertFalse(gate.shouldPersist(0.11f, 300))
		assertTrue(gate.shouldPersist(0.12f, 5200))
		assertTrue(gate.shouldPersist(1f, 5300))
	}
}
