package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val ondoJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun JsonElement.obj() = this as? JsonObject ?: JsonObject(emptyMap())
fun JsonElement.arr() = this as? JsonArray ?: JsonArray(emptyList())
fun JsonElement.str(key: String, default: String = "") = (obj()[key] as? JsonPrimitive)?.contentOrNull ?: default
fun JsonElement.num(key: String, default: Double = 0.0) = (obj()[key] as? JsonPrimitive)?.doubleOrNull ?: default
fun JsonElement.flag(key: String) = (obj()[key] as? JsonPrimitive)?.booleanOrNull == true
fun jsonObject(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
fun String.j() = JsonPrimitive(this)
fun Number.j() = JsonPrimitive(this)
fun Boolean.j() = JsonPrimitive(this)

@Serializable
data class ServiceConfig(val url: String = "", val key: String = "", val profile: String = "1", val quality: String = "", val root: String = "", val metadata: String = "", val server: String = "", val enabled: Boolean = false, val authMode: String = "") {
    /** True when requests are authenticated with the user's music-server login through a server-side gateway instead of an API key. */
    val usesNavidromeAuth get() = authMode == AUTH_MODE_NAVIDROME
    companion object { const val AUTH_MODE_NAVIDROME = "navidrome" }
}

expect fun bundledIntegrationConfig(name: String): ServiceConfig?

private val GATEWAY_PATHS = mapOf("Lidarr" to "lidarr", "SoulSync" to "soulsync", "YouTube" to "ytfallback", "Squid" to "ytfallback")

/**
 * A download service with no API key of its own goes through decknas' Ondo gateway
 * (`tools/ondo-gateway`): it takes the music-server login and adds the service's key
 * on the server, so no keys are entered or stored on the phone. [instanceUrl] is the
 * signed-in Navidrome address; the gateway sits on the same host.
 */
fun ServiceConfig.throughGateway(service: String, instanceUrl: String): ServiceConfig {
    val path = GATEWAY_PATHS[service] ?: return this
    if (usesNavidromeAuth || key.isNotBlank()) return this
    // Ktor parses a blank address as http://localhost, so require a real host.
    val origin = instanceUrl.takeIf { it.startsWith("http") }?.let { address ->
        runCatching { URLBuilder(Url(address)).apply { encodedPath = ""; parameters.clear(); fragment = "" }.buildString().trimEnd('/') }.getOrNull()
    } ?: "https://decknas.duckdns.org"
    return copy(url = "$origin/ondo-gateway/$path", authMode = ServiceConfig.AUTH_MODE_NAVIDROME)
}

/** Default name this device shows to other Connect devices (its model), until the user renames it. */
expect fun defaultDeviceName(): String

// The Navidrome server URL to sign into, baked in at build time (same
// mechanism as bundledIntegrationConfig above) so the login screen never has
// to ask for it -- see LoginManager. Blank if not configured at build time,
// in which case the login screen falls back to showing the URL field.
expect fun bundledNavidromeUrl(): String

class OndoSettings(private val settings: Settings) {
    private fun scope() = settings.getString("instanceUrl", "").trimEnd('/') + "|" + settings.getString("username", "")
    // "Soulseek" is decknas' Soularr progress feed, served by the YouTube fallback API.
    fun get(name: String): ServiceConfig = if (name == "Soulseek") get("YouTube") else runCatching {
        ondoJson.decodeFromString<ServiceConfig>(settings.getString("ondo.${scope()}.$name", "")).let { it.copy(key = revealSecret(it.key)) }
    }.getOrElse { bundledIntegrationConfig(name) ?: ServiceConfig(url = when (name) {
        "Lidarr" -> "https://decknas.duckdns.org/lidarr"
        "SoulSync" -> "https://decknas.duckdns.org/soulsync"
        "YouTube", "Squid" -> "https://decknas.duckdns.org/ytfallback"
        "Connect" -> "https://decknas.duckdns.org/wavio-connect"
        "ListenBrainz" -> "https://api.listenbrainz.org"
        else -> ""
    }) }
    val instanceUrl get() = settings.getString("instanceUrl", "")
    fun save(name: String, config: ServiceConfig) = settings.putString("ondo.${scope()}.$name", ondoJson.encodeToString(config.copy(key = protectSecret(config.key))))
    fun markDownloadRequested(name: String) = settings.putBoolean("ondo.${scope()}.downloadRequested.$name", true)
    fun shouldPollDownloads(name: String): Boolean = get(name).enabled || settings.getBoolean("ondo.${scope()}.downloadRequested.$name", false)
    var deviceId: String
        get() = settings.getString("ondo.deviceId", "").ifBlank { kotlin.uuid.Uuid.random().toString().also { deviceId = it } }
        set(value) = settings.putString("ondo.deviceId", value)
    var deviceName: String
        get() = settings.getString("ondo.deviceName", "").ifBlank { defaultDeviceName() }
        set(value) = settings.putString("ondo.deviceName", value)
}

// Platform-specific HttpClient construction. Android needs this to also trust
// decknas' ISRG Root X2 chain at the OkHttp-engine level -- see the Android actual
// for why the network-security-config pin alone wasn't enough. Other platforms just
// forward to the normal top-level HttpClient(block).
expect fun ondoHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

class OndoHttpException(val status: Int, message: String) : IllegalStateException(message)

private object MusicBrainzRateLimiter {
    private val mutex = Mutex()
    private var lastRequest = 0L
    suspend fun <T> request(block: suspend () -> T): T = mutex.withLock {
        delay((1100L - (Clock.System.now().toEpochMilliseconds() - lastRequest)).coerceAtLeast(0))
        lastRequest = Clock.System.now().toEpochMilliseconds()
        try { block() } catch (e: OndoHttpException) {
            if (e.status != 503) throw e
            delay(2000)
            lastRequest = Clock.System.now().toEpochMilliseconds()
            block()
        }
    }
}

class OndoApi(val settings: OndoSettings) {
    /**
     * Supplies Subsonic token-auth parameters (`u`, `t`, `s`) for gateway-mode services. Set from DI so this
     * class does not depend on the session. A gateway verifies them against Navidrome's `/rest/ping` and forwards an
     * allow-listed set of calls with its own server-held keys.
     */
    var navidromeAuth: (() -> Map<String, String>)? = null
    private val http = ondoHttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 60000; connectTimeoutMillis = 10000; socketTimeoutMillis = 60000 }
        followRedirects = false
    }
    suspend fun raw(url: String, method: String = "GET", body: JsonElement? = null, headers: Map<String,String> = emptyMap(), params: Map<String,String> = emptyMap(), errorHint: String = ""): JsonElement {
        if (Url(url).host.equals("musicbrainz.org", ignoreCase = true)) {
            return MusicBrainzRateLimiter.request { rawRequest(url, method, body, headers, params, errorHint) }
        }
        return rawRequest(url, method, body, headers, params, errorHint)
    }
    private suspend fun rawRequest(url: String, method: String, body: JsonElement?, headers: Map<String,String>, params: Map<String,String>, errorHint: String): JsonElement {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Enter an HTTP or HTTPS service URL" }
        val response = http.request(url) {
            this.method = HttpMethod.parse(method)
            headers.forEach { (k,v) -> header(k,v) }
            params.forEach { (k,v) -> parameter(k,v) }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }
        val data = withContext(Dispatchers.Default) {
            val text = response.bodyAsText()
            if (text.isBlank()) JsonObject(emptyMap()) else runCatching { ondoJson.parseToJsonElement(text) }.getOrNull()
        }
        val statusHint = when (response.status.value) {
            in 300..399 -> " This service URL redirects to another address; update it in Integrations"
            429 -> " This service is rate limiting requests"
            502, 503, 504 -> " This service is temporarily unavailable"
            else -> errorHint
        }
        if (response.status.value !in 200..299) throw OndoHttpException(response.status.value, "Request failed (HTTP ${response.status.value}).$statusHint")
        return data ?: error("Service returned an unexpected response")
    }
    suspend fun call(service: String, path: String, method: String = "GET", body: JsonElement? = null, params: Map<String,String> = emptyMap(), config: ServiceConfig = settings.get(service)): JsonElement {
        @Suppress("NAME_SHADOWING") val config = config.throughGateway(service, settings.instanceUrl)
        require(config.url.isNotBlank()) { "$service is not configured" }
        val root = when(service) { "Lidarr", "SoulSync", "YouTube", "Squid" -> "/api/v1"; else -> "" }
        val headers = integrationHeaders(service, config, if (config.usesNavidromeAuth) navidromeAuth?.invoke().orEmpty() else emptyMap())
        var requestBody = body
        var query = params
        if(service == "AudioMuse" && config.server.isNotBlank() && path !in listOf("/api/health", "/api/servers")) {
            if (method == "GET") query = query + ("server" to config.server)
            else requestBody = JsonObject(body?.obj().orEmpty() + ("server" to config.server.j()))
        }
        val result = try {
            raw(
                config.url.trimEnd('/') + root + path,
                method,
                requestBody,
                headers,
                query,
                errorHint = " Check the service URL, credentials, and service status."
            )
        } catch (e: OndoHttpException) {
            if (e.status == 401 || e.status == 403) throw OndoHttpException(e.status, integrationAuthFailure(service, config, e.status))
            throw e
        }
        if(service == "SoulSync") {
            check(result.flag("success")) { "SoulSync rejected the request: ${result.obj()["error"]?.str("code") ?: "unknown error"}" }
            return result.obj()["data"] ?: JsonNull
        }
        return result
    }
    suspend fun test(service: String, config: ServiceConfig): JsonElement {
        return call(service, when(service) {
        "Lidarr" -> "/system/status"; "SoulSync" -> "/system/status"
        "AudioMuse" -> "/api/health"; "ListenBrainz" -> "/1/validate-token"; else -> "/system/status"
        }, config = config)
    }
}
