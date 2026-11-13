package com.nickshortnct.ondo.data.database

import com.nickshortnct.ondo.domain.models.DomainSongArtist
import com.nickshortnct.ondo.domain.models.DomainContributor
import kotlin.test.Test
import kotlin.test.assertEquals

class ListConvertersTest {
	private val converters = Converters()
	@Test fun punctuationSurvivesJsonRoundTrip() {
		val artists = listOf(DomainSongArtist("id;^", "A;B^C||D"))
		val contributors = listOf(DomainContributor("composer", null, "id^;", "A^;B"))
		assertEquals(artists, converters.toDomainSongArtistList(converters.fromDomainSongArtistList(artists)))
		assertEquals(contributors, converters.toContributorList(converters.fromContributorList(contributors)))
	}
	@Test fun legacyRowsRemainReadable() {
		assertEquals(listOf(DomainSongArtist("id", "Artist")), converters.toDomainSongArtistList("id^Artist"))
		assertEquals(listOf(DomainContributor("composer", null, "id", "Artist")), converters.toContributorList("composer^^id^Artist"))
	}
}
