package com.nickshortnct.ondo.ondo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationAuthTest {
    @Test
    fun missingDeviceKeysRequireSetupInsteadOfAnUnauthenticatedRequest() {
        for (service in listOf("Lidarr", "SoulSync", "YouTube", "Squid", "ListenBrainz")) {
            val failure = assertFailsWith<IllegalStateException> {
                integrationHeaders(service, ServiceConfig(key = " \n "))
            }
            assertTrue(failure.message.orEmpty().contains("Integrations → $service"))
        }
    }

    @Test
    fun optionalAudioMuseAuthenticationCanStillMakeAnUnauthenticatedRequest() {
        assertEquals(emptyMap(), integrationHeaders("AudioMuse", ServiceConfig()))
    }

    @Test
    fun directAuthenticationUsesTheServiceProtocolAndTrimsPastedWhitespace() {
        val config = ServiceConfig(key = " device-key\n", profile = "7")
        assertEquals(mapOf("X-Api-Key" to "device-key"), integrationHeaders("YouTube", config))
        assertEquals(mapOf("Authorization" to "Bearer device-key", "X-Profile-Id" to "7"), integrationHeaders("SoulSync", config))
        assertEquals(mapOf("Authorization" to "Bearer device-key"), integrationHeaders("AudioMuse", config))
        assertEquals(mapOf("Authorization" to "Token device-key"), integrationHeaders("ListenBrainz", config))
    }

    @Test
    fun gatewayModeSendsOnlyLoginProofAndNeverTheStoredServiceKey() {
        val config = ServiceConfig(key = "must-not-send", authMode = ServiceConfig.AUTH_MODE_NAVIDROME)
        val proof = mapOf("u" to "listener", "t" to "login-token", "s" to "salt", "p" to "must-not-send")
        assertEquals(mapOf("X-Navidrome-User" to "listener", "X-Navidrome-Token" to "login-token", "X-Navidrome-Salt" to "salt"), integrationHeaders("YouTube", config, proof))
    }

    @Test
    fun incompleteGatewayLoginDoesNotFallBackToTheStoredServiceKey() {
        val config = ServiceConfig(key = "must-not-send", authMode = ServiceConfig.AUTH_MODE_NAVIDROME)
        for (missing in listOf("u", "t", "s")) {
            val failure = assertFailsWith<IllegalStateException> {
                integrationHeaders("YouTube", config, mapOf("u" to "listener", "t" to "token", "s" to "salt") - missing)
            }
            assertTrue(failure.message.orEmpty().contains("Sign in"))
            assertFalse(failure.message.orEmpty().contains(config.key))
        }
    }

    @Test
    fun rejectionExplainsTheSelectedAuthModeWithoutExposingCredentials() {
        val direct = ServiceConfig(key = "must-not-expose")
        val gateway = direct.copy(authMode = ServiceConfig.AUTH_MODE_NAVIDROME)
        for (status in listOf(401, 403)) {
            val directMessage = integrationAuthFailure("YouTube", direct, status)
            val gatewayMessage = integrationAuthFailure("YouTube", gateway, status)
            assertTrue(directMessage.contains("HTTP $status"))
            assertTrue(directMessage.contains("API key"))
            assertTrue(gatewayMessage.contains("Navidrome admin account"))
            assertFalse(directMessage.contains(direct.key))
            assertFalse(gatewayMessage.contains(gateway.key))
        }
    }
}

class GatewayRouteTest {
	@Test
	fun servicesWithoutAKeyGoThroughTheGatewayOnTheServersHost() {
		val routed = ServiceConfig(url = "https://decknas.duckdns.org/lidarr").throughGateway("Lidarr", "https://decknas.tail4f4d50.ts.net:10000/navidrome")
		kotlin.test.assertEquals("https://decknas.tail4f4d50.ts.net:10000/ondo-gateway/lidarr", routed.url)
		kotlin.test.assertTrue(routed.usesNavidromeAuth)
		kotlin.test.assertEquals("https://decknas.duckdns.org/ondo-gateway/ytfallback", ServiceConfig().throughGateway("Squid", "https://decknas.duckdns.org/navidrome").url)
	}

	@Test
	fun aKeyOrAnotherServiceIsLeftAlone() {
		val keyed = ServiceConfig(url = "https://x/lidarr", key = "k")
		kotlin.test.assertEquals(keyed, keyed.throughGateway("Lidarr", "https://decknas.duckdns.org/navidrome"))
		val listenBrainz = ServiceConfig(url = "https://api.listenbrainz.org")
		kotlin.test.assertEquals(listenBrainz, listenBrainz.throughGateway("ListenBrainz", "https://decknas.duckdns.org/navidrome"))
	}

	@Test
	fun fallsBackToDuckDnsWithoutAServerAddress() {
		kotlin.test.assertEquals("https://decknas.duckdns.org/ondo-gateway/soulsync", ServiceConfig().throughGateway("SoulSync", "").url)
	}
}
