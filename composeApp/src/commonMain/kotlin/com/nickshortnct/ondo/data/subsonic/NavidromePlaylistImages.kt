package com.nickshortnct.ondo.data.subsonic

import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Sets and removes playlist cover images on the server, so a cover chosen on one device shows everywhere.
 *
 * The Subsonic API has no way to do this; Navidrome (0.60+) takes uploads through its own API, which
 * needs a session from /auth/login with the same username and password Ondo already signs in with.
 */
class NavidromePlaylistImages(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager,
) {
	private val http = HttpClient {
		install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000 }
	}
	private val json = Json { ignoreUnknownKeys = true }
	private val mutex = Mutex()
	private var session: Pair<String, String>? = null // account key to token

	private val baseUrl get() = settings.getString("instanceUrl", "").trimEnd('/')
	private val accountKey get() = baseUrl + "|" + settings.getString("username", "")

	suspend fun upload(playlistId: String, jpeg: ByteArray) {
		call { token ->
			http.submitFormWithBinaryData(
				url = "$baseUrl/api/playlist/$playlistId/image",
				formData = formData {
					append("image", jpeg, Headers.build {
						append(HttpHeaders.ContentType, "image/jpeg")
						append(HttpHeaders.ContentDisposition, "filename=\"cover.jpg\"")
					})
				}
			) { authorize(token) }
		}
	}

	suspend fun remove(playlistId: String) {
		call { token -> http.delete("$baseUrl/api/playlist/$playlistId/image") { authorize(token) } }
	}

	/** Runs [request] with a session, signing in again once if the saved one has expired. */
	private suspend fun call(request: suspend (String) -> HttpResponse) {
		val token = token(refresh = false)
		var response = try {
			request(token)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			// An upload can't be replayed by the HTTP client, so a pooled connection the server or a
			// proxy had already closed fails it outright. One fresh attempt covers that.
			request(token)
		}
		if (response.status.value == 401) response = request(token(refresh = true))
		val status = response.status.value
		check(status in 200..299) {
			when (status) {
				403 -> "The server doesn't allow this account to change cover art."
				404, 405 -> "The server doesn't support playlist covers (needs Navidrome 0.60 or newer)."
				else -> "Couldn't update the cover on the server (HTTP $status)."
			}
		}
	}

	private suspend fun token(refresh: Boolean): String = mutex.withLock {
		val key = accountKey
		session?.takeIf { !refresh && it.first == key }?.let { return it.second }
		val response = http.post("$baseUrl/auth/login") {
			preferenceManager.customHeadersMap().forEach { (name, value) -> header(name, value) }
			contentType(ContentType.Application.Json)
			setBody(buildJsonObject {
				put("username", settings.getString("username", ""))
				put("password", settings.getString("password", ""))
			}.toString())
		}
		check(response.status.value in 200..299) { "Couldn't sign in to Navidrome to change the cover (HTTP ${response.status.value})." }
		val token = json.parseToJsonElement(response.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.contentOrNull
		checkNotNull(token) { "Navidrome didn't return a session." }
		session = key to token
		token
	}

	private fun HttpRequestBuilder.authorize(token: String) {
		preferenceManager.customHeadersMap().forEach { (name, value) -> header(name, value) }
		header("X-ND-Authorization", "Bearer $token")
	}
}
