package com.nickshortnct.ondo.di

import kotlin.test.*

class UpdateVersionTest {
	@Test fun sameNameHotfixUsesBuildNumber() {
		assertTrue(compareUpdateVersions("v1.0.60+961", "1.0.60+960") > 0)
		assertTrue(compareUpdateVersions("v1.0.61+962", "1.0.60+960") > 0)
		assertEquals(960L, releaseBuildNumber("v1.0.60+960"))
		assertNull(releaseBuildNumber("v1.0.60"))
	}
	@Test fun rejectsStaleCacheAndInstalledVersions() {
		assertFalse(isExpectedUpdateVersion(960, 959, 961))
		assertFalse(isExpectedUpdateVersion(960, 960, 960))
		assertTrue(isExpectedUpdateVersion(961, 959, 961))
	}
}
