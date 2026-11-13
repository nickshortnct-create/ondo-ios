package com.nickshortnct.ondo.domain.models

import io.ktor.http.Url

internal fun isLocalServerUrl(value: String): Boolean {
	val host = runCatching { Url(if ("://" in value) value.trim() else "https://${value.trim()}").host.lowercase().removeSurrounding("[", "]") }.getOrNull() ?: return false
	if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
	if (':' in host) return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")
	val parts = host.split('.').map { it.toIntOrNull() ?: return false }
	if (parts.size != 4 || parts.any { it !in 0..255 }) return false
	return parts[0] == 10 || parts[0] == 127 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)
}
