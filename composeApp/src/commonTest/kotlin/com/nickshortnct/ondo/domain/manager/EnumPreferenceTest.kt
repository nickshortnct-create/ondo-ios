package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.domain.manager.base.enumOrdinalOrDefault
import kotlin.test.*

class EnumPreferenceTest {
	private enum class Value { First, Second }
	@Test fun obsoleteOrNegativeOrdinalsFallBack() {
		assertEquals(Value.Second, enumOrdinalOrDefault(99, Value.Second))
		assertEquals(Value.Second, enumOrdinalOrDefault(-1, Value.Second))
		assertEquals(Value.First, enumOrdinalOrDefault(0, Value.Second))
	}
}
