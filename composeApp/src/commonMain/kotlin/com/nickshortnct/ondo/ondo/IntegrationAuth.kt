package com.nickshortnct.ondo.ondo

/** Validate credentials before a request so unconfigured devices do not poll protected APIs. */
internal fun integrationHeaders(
    service: String,
    config: ServiceConfig,
    navidromeAuth: Map<String, String> = emptyMap()
): Map<String, String> {
    if (config.usesNavidromeAuth) {
        val fields = mapOf("u" to "User", "t" to "Token", "s" to "Salt")
        check(fields.keys.all { !navidromeAuth[it].isNullOrBlank() }) {
            "Sign in to your music server again before using $service gateway authentication."
        }
        return fields.map { (parameter, header) -> "X-Navidrome-$header" to navidromeAuth.getValue(parameter) }.toMap()
    }

    val key = config.key.trim()
    check(key.isNotEmpty() || service !in setOf("Lidarr", "SoulSync", "YouTube", "Squid", "ListenBrainz")) {
        "Open Integrations → $service, enter its API key or token, then Save and Test connection."
    }
    if (key.isEmpty()) return emptyMap() // AudioMuse can also be hosted without token authentication.
    return when (service) {
        "SoulSync" -> mapOf("Authorization" to "Bearer $key", "X-Profile-Id" to config.profile)
        "AudioMuse" -> mapOf("Authorization" to "Bearer $key")
        "ListenBrainz" -> mapOf("Authorization" to "Token $key")
        else -> mapOf("X-Api-Key" to key)
    }
}

internal fun integrationAuthFailure(service: String, config: ServiceConfig, status: Int): String =
    if (config.usesNavidromeAuth) {
        "decknas didn't accept your music server login for $service (HTTP $status). Download services need a Navidrome admin account."
    } else {
        "Authentication rejected (HTTP $status). Open Integrations → $service, check its API key or token, then Save and Test connection."
    }
