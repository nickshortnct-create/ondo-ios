package com.nickshortnct.ondo.data.database

import kotlin.test.Test
import kotlin.test.assertEquals

class FoldForSearchTest {
	@Test fun foldsAccentsCaseAndPunctuation() {
		assertEquals("beyonce deja vu", foldForSearch("Beyoncé — DÉJÀ VU"))
		assertEquals("muse ac dc", foldForSearch("MÜSE: AC/DC"))
		assertEquals("%_\\", foldForSearch("%_\\"))
	}
}
