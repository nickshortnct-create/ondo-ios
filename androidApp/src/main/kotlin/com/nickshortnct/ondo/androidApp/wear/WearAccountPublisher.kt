package com.nickshortnct.ondo.androidApp.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.util.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hands the music-server login to the watch so it can browse and stream on
 * its own. Only a Subsonic token (md5(password + salt)) and salt are sent,
 * never the password itself, and the Data Layer only delivers to the Ondo
 * watch app signed with the same key.
 */
object WearAccountPublisher : KoinComponent {
	@Volatile
	private var unavailable = false

	/** Publishes now, and again whenever the user logs in or out. */
	suspend fun observe(context: Context) {
		get<SessionManager>().isLoggedIn.collect { publish(context, force = false) }
	}

	suspend fun publish(context: Context, force: Boolean) {
		if (unavailable && !force) return
		val settings = get<Settings>()
		val session = get<SessionManager>()
		val preferences = get<PreferenceManager>()
		val url = settings.getString("instanceUrl", "")
		val username = settings.getString("username", "")
		val password = settings.getString("password", "")
		val loggedIn = session.isLoggedIn.value && url.isNotBlank() && username.isNotBlank()
		val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
		val token = if (loggedIn) md5(password + salt) else ""
		val request = PutDataMapRequest.create(WearContract.PATH_ACCOUNT).apply {
			dataMap.putBoolean(WearContract.KEY_LOGGED_IN, loggedIn)
			dataMap.putString(WearContract.KEY_URL, if (loggedIn) url else "")
			dataMap.putString(WearContract.KEY_USERNAME, if (loggedIn) username else "")
			dataMap.putString(WearContract.KEY_TOKEN, token)
			dataMap.putString(WearContract.KEY_SALT, if (loggedIn) salt else "")
			dataMap.putString(
				WearContract.KEY_HEADERS,
				if (loggedIn) preferences.customHeadersMap().entries.joinToString("\n") { "${it.key}: ${it.value}" } else ""
			)
			// A changing value guarantees the watch hears an explicit request.
			if (force) dataMap.putLong(WearContract.KEY_UPDATED_AT, System.currentTimeMillis())
		}.asPutDataRequest().setUrgent()
		try {
			Wearable.getDataClient(context).putDataItem(request).await()
			unavailable = false
		} catch (e: CancellationException) {
			throw e
		} catch (e: ApiException) {
			unavailable = true
			Logger.i("WearAccountPublisher", "Wear Data Layer unavailable (${e.statusCode})")
		} catch (e: Exception) {
			Logger.e("WearAccountPublisher", "could not share login with the watch", e)
		}
	}

	private fun md5(value: String): String = MessageDigest.getInstance("MD5")
		.digest(value.toByteArray(Charsets.UTF_8))
		.joinToString("") { "%02x".format(it) }
}
