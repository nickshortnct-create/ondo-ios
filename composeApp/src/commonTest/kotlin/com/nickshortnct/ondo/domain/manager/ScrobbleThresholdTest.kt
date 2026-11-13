package com.nickshortnct.ondo.domain.manager

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrobbleThresholdTest {
	@Test
	fun aTwentySecondTrackIsNotScrobbledWithTheDefaultThirtySecondMinimum() {
		assertFalse(isScrobbleEligible(durationMs = 20_000, playedMs = 20_000, requiredFraction = 0.5f, minDurationSeconds = 30f))
	}

	@Test
	fun aThreeMinuteTrackIsScrobbledOnceHalfIsPlayed() {
		assertTrue(isScrobbleEligible(durationMs = 180_000, playedMs = 90_000, requiredFraction = 0.5f, minDurationSeconds = 30f))
		assertFalse(isScrobbleEligible(durationMs = 180_000, playedMs = 60_000, requiredFraction = 0.5f, minDurationSeconds = 30f))
	}

	@Test
	fun unknownDurationIsNeverScrobbled() {
		assertFalse(isScrobbleEligible(durationMs = -1, playedMs = 60_000, requiredFraction = 0.5f, minDurationSeconds = 30f))
	}
}
