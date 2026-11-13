package com.nickshortnct.ondo.data.subsonic

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLBuilder
import kotlin.time.Instant

/**
 * The Subsonic client sends scrobble times as ISO text, but the API takes epoch
 * milliseconds. Navidrome ignores any other format and uses the time the request
 * arrived, which put queued offline plays at the time of the sync.
 */
internal val ScrobbleTimeMillis = createClientPlugin("ScrobbleTimeMillis") {
	onRequest { request, _ -> request.url.useEpochMillisForScrobbleTime() }
}

internal fun URLBuilder.useEpochMillisForScrobbleTime() {
	if (pathSegments.lastOrNull()?.removeSuffix(".view") != "scrobble") return
	val time = parameters["time"] ?: return
	val instant = runCatching { Instant.parse(time) }.getOrNull() ?: return
	parameters["time"] = instant.toEpochMilliseconds().toString()
}
