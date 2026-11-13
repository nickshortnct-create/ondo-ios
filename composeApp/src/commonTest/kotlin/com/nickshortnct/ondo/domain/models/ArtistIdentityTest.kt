package com.nickshortnct.ondo.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistIdentityTest {
	@Test
	fun differentKnownArtistsWithTheSameNameRemainDistinct() {
		assertFalse(artistIdentityMatches("John Williams", "composer-id", "John Williams", "guitarist-id"))
	}

	@Test
	fun matchingIdsAllowChangedNames() {
		assertTrue(artistIdentityMatches("Former name", "SAME-ID", "New name", "same-id"))
	}

	@Test
	fun missingMetadataFallsBackToNormalizedName() {
		assertTrue(artistIdentityMatches("Guns N’ Roses", null, "guns n roses", "artist-id"))
		assertTrue(artistIdentityMatches("Prince", "artist-id", "PRINCE", " "))
		assertFalse(artistIdentityMatches("Prince", null, "Prince Royce", null))
		assertFalse(artistIdentityMatches("", null, "", null))
	}
}
