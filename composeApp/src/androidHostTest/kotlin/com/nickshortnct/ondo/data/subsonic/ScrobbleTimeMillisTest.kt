package com.nickshortnct.ondo.data.subsonic

import com.sun.net.httpserver.HttpServer
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.http.URLBuilder
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class ScrobbleTimeMillisTest {
	/** Runs the real Subsonic client, because the bug is in the query it builds. */
	@Test fun scrobbleReachesTheServerWithEpochMillis() = runBlocking {
		var query: String? = null
		val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/") { exchange ->
			if (exchange.requestURI.path.substringAfterLast('/').startsWith("scrobble")) {
				query = exchange.requestURI.rawQuery
			}
			val body = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""".toByteArray()
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
		val client = SubsonicClient.Companion(
			baseUrl = "http://127.0.0.1:${server.address.port}",
			auth = SubsonicAuth.Token(username = "nick", password = "password"),
			client = SubsonicClientNames.METADATA,
			clientConfig = { install(ScrobbleTimeMillis) }
		)
		try {
			val playedAt = Instant.parse("2026-11-05T20:13:12.213879Z")
			client.scrobble("song", playedAt, true)
			val time = assertNotNull(query).split('&').first { it.startsWith("time=") }
			assertEquals("time=${playedAt.toEpochMilliseconds()}", time)
		} finally {
			client.close()
			server.stop(0)
		}
	}

	@Test fun otherRequestsKeepTheirParameters() {
		val url = URLBuilder("http://localhost/rest/getCoverArt?time=2026-11-05T20:13:12Z")
		url.useEpochMillisForScrobbleTime()
		assertEquals("2026-11-05T20:13:12Z", url.parameters["time"])
	}

	@Test fun timesThatAreAlreadyMillisAreLeftAlone() {
		val url = URLBuilder("http://localhost/rest/scrobble?time=1793909592213")
		url.useEpochMillisForScrobbleTime()
		assertEquals("1793909592213", url.parameters["time"])
	}
}
