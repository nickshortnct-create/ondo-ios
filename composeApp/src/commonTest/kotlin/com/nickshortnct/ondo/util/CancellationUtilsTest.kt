package com.nickshortnct.ondo.util

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CancellationUtilsTest {
	@Test fun cancellationIsNeverConvertedToFailure() {
		assertFailsWith<CancellationException> { runCatchingCancellable { throw CancellationException() } }
		assertTrue(runCatchingCancellable { error("provider failed") }.isFailure)
	}
}
