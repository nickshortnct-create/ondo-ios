package com.nickshortnct.ondo.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Music-server login handed over by the phone app (token auth, never the password). */
data class Account(
	val url: String,
	val username: String,
	val token: String,
	val salt: String,
	val headers: Map<String, String>,
) {
	val baseUrl: String get() = url.trimEnd('/')
}

class AccountStore(context: Context) {
	private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

	private val _account = MutableStateFlow(read())
	val account: StateFlow<Account?> get() = _account

	private fun read(): Account? {
		val url = prefs.getString("url", null)?.takeIf { it.isNotBlank() } ?: return null
		return Account(
			url = url,
			username = prefs.getString("username", "").orEmpty(),
			token = prefs.getString("token", "").orEmpty(),
			salt = prefs.getString("salt", "").orEmpty(),
			headers = parseHeaders(prefs.getString("headers", "").orEmpty()),
		)
	}

	fun save(account: Account?) {
		prefs.edit().apply {
			if (account == null) clear() else {
				putString("url", account.url)
				putString("username", account.username)
				putString("token", account.token)
				putString("salt", account.salt)
				putString("headers", account.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" })
			}
		}.apply()
		_account.value = account
	}

	companion object {
		fun parseHeaders(raw: String): Map<String, String> = raw.lines().mapNotNull { line ->
			val parts = line.split(":", limit = 2)
			if (parts.size < 2) return@mapNotNull null
			val key = parts[0].trim()
			val value = parts[1].trim()
			if (key.isEmpty() || value.isEmpty()) null else key to value
		}.toMap()
	}
}
