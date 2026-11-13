package com.nickshortnct.ondo.domain.models

import kotlin.test.*

class LocalServerUrlTest {
	@Test fun recognizesLocalHostsWithoutPromptingForPublicServers() {
		listOf("localhost", "nas.local", "http://192.168.1.20:4533", "10.0.0.1", "172.16.0.1", "172.31.255.1", "http://[::1]:4533", "http://[fd00::1]:4533").forEach { assertTrue(isLocalServerUrl(it), it) }
		listOf("https://decknas.duckdns.org", "172.15.0.1", "172.32.0.1", "fc-music.example", "192.168.999.1").forEach { assertFalse(isLocalServerUrl(it), it) }
	}
}
