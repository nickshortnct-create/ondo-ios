package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.data.subsonic.ScrobbleTimeMillis
import com.nickshortnct.ondo.data.subsonic.SubsonicClientNames
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager
) {
	val isLoggedIn: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val username: String get() = settings.getString("username", "")
	/** The music-server password, for signing in to Ondo Connect with the same account. */
	val password: String get() = settings.getString("password", "")

	var api: SubsonicClient = createClient(
		instanceUrl = settings.getString("instanceUrl", ""),
		username = settings.getString("username", ""),
		password = settings.getString("password", ""),
	)
		private set

	init {
		isLoggedIn.value = settings.getStringOrNull("username") != null
	}

	private fun createClient(
		instanceUrl: String,
		username: String,
		password: String,
	) = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(
			username = username,
			password = password,
		),
		client = SubsonicClientNames.METADATA,
		clientConfig = {
			install(UserAgent) {
				agent = SubsonicClientNames.METADATA
			}
			install(ScrobbleTimeMillis)

			defaultRequest {
				preferenceManager.customHeadersMap().forEach { (key, value) -> header(key, value) }
			}
		}
	)

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String
	) {
		val client = createClient(instanceUrl, username, password)

		try {
			client.ping()
		} catch (e: Exception) {
			client.close()
			if (e is CancellationException) throw e
			throw Exception(
				"Failed to connect to the instance. Please check your credentials and try again.",
				e
			)
		}

		settings["instanceUrl"] = instanceUrl
		settings["username"] = username
		settings["password"] = password

		api.close()
		api = client
		isLoggedIn.value = true
	}

	fun logout() {
		settings["username"] = null
		settings["password"] = null
		isLoggedIn.value = false
		refreshClient()
	}

	fun refreshClient() {
		api.close()
		api = createClient(
			instanceUrl = settings.getString("instanceUrl", ""),
			username = settings.getString("username", ""),
			password = settings.getString("password", ""),
		)
	}

	fun getCoverArtUrl(coverArtId: String) = api.getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${preferenceManager.coverArtQuality.value}"
	)
}
