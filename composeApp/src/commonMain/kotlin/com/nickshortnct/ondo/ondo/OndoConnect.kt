package com.nickshortnct.ondo.ondo

import com.nickshortnct.ondo.domain.models.*
import com.nickshortnct.ondo.domain.manager.AppVisibility
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.core.entryIds
import com.nickshortnct.ondo.ui.core.PlayerUiState
import com.nickshortnct.ondo.util.Logger
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import kotlin.time.Clock
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Highest Connect protocol this build speaks. 2 adds explicit shuffle/repeat/playAt/remove/move commands; peers that don't advertise it get full "transfer" snapshots. */
const val CONNECT_PROTOCOL = 2

@Serializable
data class ConnectDevice(val id: String, val name: String, val protocol: Int = 1)
@Serializable
data class ConnectTrack(val id: String, val title: String = "", val artist: String = "", val album: String? = null, val duration: Double = 0.0, val coverArt: String? = null,
    /** Lets the receiving device find the album cover and open the album/artist (optional; older builds omit them). */
    val albumId: String? = null, val artistId: String? = null) {
    fun song() = DomainSong(id, title, artist, artistId ?: "", album, albumId, null, null, null, null, emptyList(), null, null, emptyList(), emptyList(), duration.seconds, null, emptyList(), userRating = null, averageRating = null, bitRate = null, bitDepth = null, sampleRate = null, audioChannelCount = null, replayGain = null, fileSize = 0, fileExtension = "", mimeType = "audio/mpeg", filePath = null, starredAt = null, coverArtId = coverArt, musicBrainzId = null, explicitStatus = DomainExplicitStatus.Unknown, artists = emptyList(), albumArtists = emptyList())
}
@Serializable
data class ConnectSnapshot(val playing: Boolean = false, val buffering: Boolean = false, val currentTime: Double = 0.0, val duration: Double = 0.0, val currentIndex: Int? = null, val repeatMode: String = "off", val shuffle: Boolean = false, val source: JsonElement = JsonNull, val queue: List<ConnectTrack> = emptyList(), val queueEntryIds: List<String> = emptyList(),
    /** When playback last started on the sender (epoch ms, sender's clock), null while paused. See [keepsPlaying]. */
    val playingSince: Long? = null,
    /** What the sender is actually streaming, for the quality line on other devices: "FLAC", kbps, Hz. */
    val format: String? = null, val bitRate: Int? = null, val sampleRate: Int? = null) {
    fun state(): PlayerUiState {
        val songs = queue.map { it.song() }
        val index = currentIndex?.takeIf { it in songs.indices } ?: -1
        return PlayerUiState(queue = songs, queueEntryIds = queueEntryIds, currentIndex = index, currentSong = songs.getOrNull(index), isPaused = !playing, isLoading = buffering, playbackMimeType = format?.let { "audio/${it.lowercase()}" }, playbackBitrate = bitRate?.takeIf { it > 0 }?.times(1000), playbackSampleRate = sampleRate?.takeIf { it > 0 }, progress = if(duration > 0) (currentTime / duration).toFloat().coerceIn(0f,1f) else 0f, repeatMode = when(repeatMode) { "one" -> 1; "all" -> 2; else -> 0 }, isShuffleEnabled = shuffle)
    }
}
/**
 * The remote device's position [elapsedMs] later. Other devices only send their position every
 * few seconds (10 s on Ondo desktop), so without this the progress bar and synced lyrics stand
 * still between updates and then jump.
 */
fun PlayerUiState.advancedBy(elapsedMs: Long): PlayerUiState {
    val durationMs = currentSong?.duration?.inWholeMilliseconds ?: 0L
    if(isPaused || isLoading || durationMs <= 0L || elapsedMs <= 0L || progress >= 1f) return this
    return copy(progress = (progress + elapsedMs.toFloat() / durationMs).coerceAtMost(1f))
}
/**
 * The snapshot as of [nowMs]: a playing device's position moves on after it reported it at
 * [receivedAtMs] (it only reports every few seconds), so taking playback over must start
 * from where it is now, not where it last said it was.
 */
fun ConnectSnapshot.at(nowMs: Long, receivedAtMs: Long?): ConnectSnapshot {
    if(!playing || receivedAtMs == null || nowMs <= receivedAtMs) return this
    val moved = currentTime + (nowMs - receivedAtMs) / 1000.0
    return copy(currentTime = if(duration > 0) moved.coerceAtMost(duration) else moved)
}
/** Same order as the local player: off -> all -> one -> off. */
fun nextRepeatMode(mode: Int): Int = when(mode) { 0 -> 2; 2 -> 1; else -> 0 }

/** Removes a track and keeps the *same* song current (or the next one when the current song itself was removed). */
fun PlayerUiState.withoutQueueItem(index: Int): PlayerUiState {
    if(index !in queue.indices) return this
    val newQueue = queue.toMutableList().apply { removeAt(index) }
    val current = when {
        newQueue.isEmpty() -> -1
        index < currentIndex -> currentIndex - 1
        else -> currentIndex.coerceAtMost(newQueue.lastIndex)
    }
    return copy(queue = newQueue, queueEntryIds = entryIds().toMutableList().apply { removeAt(index) }, currentIndex = current, currentSong = newQueue.getOrNull(current))
}

/** Devices are told apart by id; when two share a name (for example two phones of the same model) a short id suffix keeps them distinguishable. */
fun disambiguateDeviceNames(devices: List<ConnectDevice>): List<ConnectDevice> {
    val counts = devices.groupingBy { it.name.trim().lowercase() }.eachCount()
    return devices.map { if((counts[it.name.trim().lowercase()] ?: 0) > 1) it.copy(name = "${it.name} · ${it.id.take(4)}") else it }
}
fun DomainSong.connectTrack() = ConnectTrack(id, title, artistName, albumTitle, duration.inWholeMilliseconds / 1000.0, coverArtId, albumId?.takeIf { it.isNotBlank() }, artistId.takeIf { it.isNotBlank() })
/** Other devices only need a window of the queue around the current track, never a 10,000-song dump. */
const val CONNECT_MAX_QUEUE = 1000

fun PlayerUiState.snapshot(maxQueue: Int = CONNECT_MAX_QUEUE, includeCoverArt: Boolean = true, playingSince: Long? = null): ConnectSnapshot {
    val start = if (queue.size <= maxQueue) 0 else (currentIndex.coerceAtLeast(0) - maxQueue / 2).coerceIn(0, queue.size - maxQueue)
    val window = queue.subList(start, minOf(queue.size, start + maxQueue))
    val windowIndex = (currentIndex - start).takeIf { currentIndex >= 0 && it in window.indices }
    return ConnectSnapshot(!isPaused && currentSong != null, isLoading, progress * (currentSong?.duration?.inWholeMilliseconds ?: 0) / 1000.0, (currentSong?.duration?.inWholeMilliseconds ?: 0) / 1000.0, windowIndex, when(repeatMode) { 1 -> "one"; 2 -> "all"; else -> "off" }, isShuffleEnabled, queueEntryIds = entryIds().subList(start, minOf(queue.size, start + maxQueue)), queue = window.map { it.connectTrack().let { track -> if(includeCoverArt) track else track.copy(coverArt = null) } }, playingSince = playingSince.takeIf { !isPaused && currentSong != null },
        format = (playbackMimeType?.substringAfter('/')?.replace("mpeg", "mp3") ?: currentSong?.fileExtension)?.uppercase()?.ifBlank { null },
        bitRate = playbackBitrate?.div(1000) ?: currentSong?.bitRate,
        sampleRate = playbackSampleRate ?: currentSong?.sampleRate)
}

/**
 * One device plays at a time, like Spotify Connect. When two report playing, the one that
 * started last keeps playing and the other stops and follows it. Ties go by device id so both
 * sides agree; a peer that sends no start time is an older build that never stops itself.
 */
fun keepsPlaying(selfId: String, selfSince: Long?, otherId: String, otherSince: Long?): Boolean {
    if(otherSince == null) return false
    val mine = selfSince ?: 0L
    return if(mine != otherSince) mine > otherSince else selfId > otherId
}

/** After a handoff, the device that gave up playback may still send a "playing" snapshot or two; those are ignored this long. */
const val CONNECT_HANDOFF_GRACE_MS = 5_000L

/**
 * Whether a device that just reported [snapshot] should become the one this device follows:
 * it is playing, isn't followed already, isn't the device just handed off from, and either
 * started after local playback or nothing plays here and this device didn't just take over.
 */
fun shouldFollow(snapshot: ConnectSnapshot, from: String, selected: String?, selfId: String, localPlaying: Boolean, localSince: Long?, localClaimAt: Long, handoffUntil: Long, now: Long): Boolean {
    if(!snapshot.playing || from == selected || now < handoffUntil) return false
    return if(localPlaying) !keepsPlaying(selfId, localSince, from, snapshot.playingSince) else now - localClaimAt >= CONNECT_HANDOFF_GRACE_MS
}

const val CONNECT_DUCKDNS_URL = "https://decknas.duckdns.org/wavio-connect"
/** Tailscale Funnel route to the same relay; some networks block duckdns.org. */
const val CONNECT_FUNNEL_URL = "https://decknas.tail4f4d50.ts.net:10000/wavio-connect"
/** The configured relay first, then both public routes to it (same accounts and sessions). */
fun connectRelayCandidates(preferred: String): List<String> =
    listOf(preferred, CONNECT_DUCKDNS_URL, CONNECT_FUNNEL_URL).map { it.trim().trimEnd('/') }.filter { it.startsWith("http") }.distinct()

private data class PublishKey(val ids: List<String>, val index: Int, val paused: Boolean, val repeat: Int, val shuffle: Boolean, val quality: Pair<String?, Int?>)
private val CONNECT_IDLE_TIMEOUT = 5.minutes
private val CONNECT_HEARTBEAT = 4.minutes
private const val CONNECT_MAX_MESSAGE_BYTES = 512 * 1024

object ConnectRouting {
    val remoteState = MutableStateFlow<PlayerUiState?>(null)
    var dispatch: ((String, List<DomainSong>?, Int, Float) -> Boolean)? = null
    fun route(action: String, songs: List<DomainSong>? = null, index: Int = 0, value: Float = 0f) = dispatch?.invoke(action, songs, index, value) ?: false
}

class OndoConnect(private val api: OndoApi, private val player: MediaPlayerViewModel, private val session: SessionManager, private val preferences: PreferenceManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // No maxFrameSize override here: Ktor's OkHttp engine (which ondoHttpClient uses
    // on Android, for the decknas TLS trust fix) doesn't support configuring it and
    // throws "Max frame size switch is not supported in OkHttp engine." on install.
    // Outgoing message size is already capped independently in send() below.
    private val http = ondoHttpClient { install(WebSockets) }
    private var socket: DefaultClientWebSocketSession? = null
    private var connection: Job? = null
    val devices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    // Presence as the relay sent it (before display names are disambiguated).
    private val rawDevices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    val connected = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val selected = MutableStateFlow<String?>(null)
    val snapshots = MutableStateFlow<Map<String,ConnectSnapshot>>(emptyMap())
    private val snapshotTimes = mutableMapOf<String, Long>()
    private var applying = false
    /** When local audio last started (epoch ms); sent so devices agree which one keeps playing. */
    private var localPlayingSince: Long? = null
    /** When this phone last took playback (local start, "Play here", an incoming transfer). */
    private var localClaimAt = 0L
    private val handoffUntil = mutableMapOf<String, Long>()
    private fun now() = Clock.System.now().toEpochMilliseconds()
    private fun markHandoff(id: String) { handoffUntil[id] = now() + CONNECT_HANDOFF_GRACE_MS }
    // Connect is opt-in: it keeps a socket open and publishes queue snapshots.
    val enabled = MutableStateFlow(false)
    // Set when the socket was dropped because nothing was playing or controlled for a while.
    private var idleSuspended = false
    init {
        ConnectRouting.dispatch = ::route
        api.settings.get("Connect").let { preferences.migrateConnectEnabled(it.enabled && it.key.isNotBlank()) }
        enabled.value = preferences.connectEnabled
        // Turned on but never signed in (or signed out by the relay): sign in with the music-server
        // account automatically, so Connect never needs its own username and password.
        scope.launch {
            combine(session.isLoggedIn, enabled) { loggedIn, on -> loggedIn && on }.distinctUntilChanged().collect { ready ->
                if(ready && api.settings.get("Connect").key.isBlank()) runCatching { signInWithServerAccount() }
            }
        }
        scope.launch { combine(session.isLoggedIn, enabled) { loggedIn, on -> loggedIn && on }.distinctUntilChanged().collectLatest { if(it) reconnect() else disconnect() } }
        // While another device plays and Ondo is on screen, count its position forward between
        // its updates (see [advancedBy]); each snapshot resets it to the real position.
        scope.launch {
            combine(selected, AppVisibility.visible) { remote, visible -> remote != null && visible }.distinctUntilChanged().collectLatest { active ->
                if(!active) return@collectLatest
                var last = now()
                while(true) {
                    delay(250)
                    val t = now()
                    ConnectRouting.remoteState.value?.let { state ->
                        val next = state.advancedBy(t - last)
                        if(next !== state) ConnectRouting.remoteState.value = next
                    }
                    last = t
                }
            }
        }
        // Playback starting wakes an idle-suspended connection.
        scope.launch { player.localUiState.map { !it.isPaused }.distinctUntilChanged().collect { playing -> if(playing) onForeground() } }
        // Local playback starting makes this phone the player: whichever device was playing
        // stops when it sees the next snapshot, and this phone stops following it.
        scope.launch {
            player.localUiState.map { !it.isPaused && it.currentSong != null }.distinctUntilChanged().collect { playing ->
                if(playing) {
                    localPlayingSince = now(); localClaimAt = localPlayingSince!!
                    if(selected.value != null) select(null)
                } else localPlayingSince = null
            }
        }
    }
    fun setEnabled(value: Boolean) { preferences.connectEnabled = value; enabled.value = value }
    /** Reconnects if the socket was dropped for being idle; cheap no-op otherwise. */
    fun onForeground() { if(idleSuspended && enabled.value) reconnect() }
    suspend fun authenticate(url: String, username: String, password: String, register: Boolean) {
        val result = api.raw(url.trimEnd('/') + if(register) "/auth/register" else "/auth/login", "POST", jsonObject("username" to username.j(), "password" to password.j()))
        val token = result.str("token")
        check(token.isNotBlank()) { "Connect returned no session" }
        api.settings.save("Connect", ServiceConfig(url, token, server = result.str("username"), enabled = true))
        // Signing in by hand is an explicit opt-in.
        if(!enabled.value) setEnabled(true) else reconnect()
    }
    /**
     * One-tap sign-in with the account Ondo is logged in to, trying every route to the relay.
     * The relay account is created the first time (same as the desktop app). Turns Connect on.
     */
    suspend fun signInWithServerAccount() {
        val username = session.username
        val password = session.password
        check(username.isNotBlank() && password.isNotBlank()) { "Sign in to your music server first" }
        var last: Exception? = null
        for (url in connectRelayCandidates(api.settings.get("Connect").url)) {
            try {
                try { authenticate(url, username, password, register = false) }
                catch (e: OndoHttpException) { if (e.status !in listOf(401, 404)) throw e; authenticate(url, username, password, register = true) }
                return
            } catch (e: CancellationException) { throw e }
            catch (e: OndoHttpException) { throw IllegalStateException(if (e.status == 409) "A Connect account with your username already uses a different password. Sign in under Advanced." else (e.message ?: "Connect rejected the sign-in")) }
            catch (e: Exception) { last = e }
        }
        throw IllegalStateException("Couldn't reach Ondo Connect on decknas (${last?.message ?: "no connection"}).")
    }
    // Connect is opt-in. Only a missing/unauthorized relay account permits registration.
    suspend fun signInWithMainAccount(username: String, password: String) {
        if(!enabled.value) return
        val url = api.settings.get("Connect").url
        try {
            authenticate(url, username, password, register = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is OndoHttpException || e.status !in listOf(401, 404)) {
                Logger.w("OndoConnect", "Relay sign-in failed", e)
                return
            }
            try {
                authenticate(url, username, password, register = true)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                Logger.w("OndoConnect", "Auto sign-in to Connect with main account failed", e2)
            }
        }
    }
    fun disconnect() {
        connection?.cancel(); connection = null
        socket = null; connected.value = false; devices.value = emptyList(); rawDevices.value = emptyList(); snapshots.value = emptyMap(); snapshotTimes.clear()
        selected.value = null; ConnectRouting.remoteState.value = null
    }
    fun logout() { setEnabled(false); disconnect(); api.settings.save("Connect", api.settings.get("Connect").copy(key = "", enabled = false)) }
    fun reconnect() {
        disconnect()
        val config = api.settings.get("Connect")
        idleSuspended = false
        if(!enabled.value || !config.enabled || config.key.isBlank()) return
        connection = scope.launch {
            var backoff = 1000L
            // On failure the next attempt tries the relay's other public route (DuckDNS / Funnel).
            val candidates = connectRelayCandidates(config.url)
            var attempt = 0
            while(isActive) {
                try {
                    val url = candidates[attempt % candidates.size].replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/ws"
                    http.webSocket(urlString = url, request = { header("Authorization", "Bearer ${config.key}") }) {
                        socket = this; connected.value = true; error.value = null; backoff = 1000
                        hello()
                        // The relay keeps a device listed while its socket is open, and pings the socket itself.
                        // This only keeps carrier NATs from dropping a quiet connection; every message wakes the
                        // phone's radio, and a 15 s beat kept 5G from ever going idle during playback.
                        val heartbeat = launch { while(isActive) { delay(CONNECT_HEARTBEAT); hello() } }
                        // Nobody else online means nobody to show the queue to: publish nothing until a device
                        // appears, then send it the current state straight away.
                        val othersOnline = rawDevices.map { it.isNotEmpty() }.distinctUntilChanged()
                        // Publish on change, not on a timer: a 500-song queue is ~100 KB per message.
                        val publishing = launch {
                            combine(
                                player.localUiState.map { PublishKey(it.queue.map(DomainSong::id), it.currentIndex, it.isPaused, it.repeatMode, it.isShuffleEnabled, it.playbackMimeType to it.playbackBitrate) },
                                rawDevices.map { devices -> devices.map { it.id }.toSet() }
                            ) { key, others -> key to others }
                                .distinctUntilChanged()
                                .debounce(300)
                                .collect { (_, others) -> if(others.isNotEmpty()) publish() }
                        }
                        // Slow position refresh so a watching controller's progress bar keeps moving.
                        val positionBeat = launch {
                            othersOnline.collectLatest { others ->
                                if(others) while(isActive) { delay(if(player.localUiState.value.isPaused) 60_000 else 10_000); publish() }
                            }
                        }
                        // Drop the socket after IDLE_TIMEOUT with nothing playing and no remote device selected.
                        val idleWatch = launch {
                            // Following another device keeps the socket only while Ondo is on screen; in the
                            // background it reconnects (and follows again) when the app is reopened.
                            combine(player.localUiState.map { !it.isPaused }.distinctUntilChanged(), selected, AppVisibility.visible) { playing, remote, visible -> playing || (remote != null && visible) }
                                .distinctUntilChanged()
                                .collectLatest { active -> if(!active) { delay(CONNECT_IDLE_TIMEOUT); idleSuspended = true; close(CloseReason(CloseReason.Codes.NORMAL, "idle")) } }
                        }
                        try { for(frame in incoming) if(frame is Frame.Text) receive(frame.readText()) }
                        finally { heartbeat.cancel(); publishing.cancel(); positionBeat.cancel(); idleWatch.cancel() }
                    }
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    Logger.e("OndoConnect", "websocket connection failed", e)
                    attempt++
                    error.value = "Connect disconnected (${e.message ?: e::class.simpleName}). Retrying…"
                }
                finally { socket = null; connected.value = false }
                if(idleSuspended) break
                delay(backoff); backoff = (backoff * 2).coerceAtMost(15000)
            }
        }
    }
    private suspend fun send(type: String, payload: Map<String,JsonElement>) {
        val ws = socket ?: error("Connect is offline")
        val text = JsonObject(mapOf("v" to 1.j(), "type" to type.j()) + payload).toString()
        require(text.encodeToByteArray().size <= 2 * 1024 * 1024) { "Queue exceeds the relay message limit" }
        ws.send(Frame.Text(text))
    }
    private suspend fun hello() = send("hello", mapOf("device" to ondoJson.encodeToJsonElement(ConnectDevice(api.settings.deviceId, api.settings.deviceName, CONNECT_PROTOCOL))))
    private suspend fun publish() {
        val state = player.localUiState.value
        var snapshot = ondoJson.encodeToJsonElement(state.snapshot(playingSince = localPlayingSince))
        // Cover-art ids are optional for other devices; drop them if the message is still huge.
        if(snapshot.toString().length > CONNECT_MAX_MESSAGE_BYTES) snapshot = ondoJson.encodeToJsonElement(state.snapshot(includeCoverArt = false, playingSince = localPlayingSince))
        send("snapshot", mapOf("snapshot" to snapshot))
    }
    fun command(id: String, command: JsonObject) { scope.launch { try { send("command", mapOf("to" to id.j(), "command" to command)) } catch(e: CancellationException) { throw e } catch(e: Exception) { error.value = e.message } } }
    fun control(cmd: String) { selected.value?.let { command(it, jsonObject("cmd" to cmd.j())) } }
    fun select(id: String?) {
        selected.value = id
        ConnectRouting.remoteState.value = id?.let { snapshots.value[it]?.state() ?: PlayerUiState(isPaused = true, isLoading = true) }
        if(id != null) { applying = true; player.pause(); applying = false; command(id, jsonObject("cmd" to "status".j())) }
    }
    /** Moves playback to a device: it continues there, stops where it was, and this phone controls it. */
    fun transferTo(id: String) {
        val old = selected.value
        if(old == id) return
        val snapshot = (ConnectRouting.remoteState.value ?: player.localUiState.value).snapshot()
        if(snapshot.currentIndex != null) command(id, jsonObject("cmd" to "transfer".j(), "snapshot" to ondoJson.encodeToJsonElement(snapshot.copy(playing = true))))
        if(old != null) { markHandoff(old); command(old, jsonObject("cmd" to "pause".j())) }
        select(id)
    }
    /** The device picker: null is this phone. */
    fun playOn(id: String?) {
        if(id == selected.value) return
        if(id != null) transferTo(id) else bringHere()
    }
    fun bringHere() {
        val id = selected.value ?: return
        val snapshot = snapshots.value[id]?.at(now(), snapshotTimes[id]) ?: return
        if (Clock.System.now().toEpochMilliseconds() - (snapshotTimes[id] ?: 0L) > 120_000L) {
            error.value = "Device status is stale. Refresh it before transferring playback."
            command(id, jsonObject("cmd" to "status".j()))
            return
        }
        markHandoff(id)
        select(null)
        scope.launch {
            if(snapshot.currentIndex != null) applySnapshot(snapshot.copy(playing = true))
            command(id, jsonObject("cmd" to "pause".j()))
        }
    }
    private suspend fun applySnapshot(snapshot: ConnectSnapshot) {
        require(snapshot.queue.size <= 10000) { "Queue is too large" }
        localClaimAt = now()
        applying = true
        try {
            // One atomic replacement of the queue, position, repeat and shuffle (no clear-then-load race).
            player.replaceQueue(snapshot.state())
        } finally { applying = false }
    }
    private suspend fun receive(raw: String) {
        val message = runCatching { ondoJson.parseToJsonElement(raw).obj() }.getOrNull() ?: return
        if(message.num("v") != 1.0) return
        when(message.str("type")) {
            "presence" -> {
                val others = runCatching { ondoJson.decodeFromJsonElement<List<ConnectDevice>>(message["devices"] ?: JsonArray(emptyList())) }.getOrDefault(emptyList()).distinctBy { it.id }.filter { it.id != api.settings.deviceId }
                val ids = others.map { it.id }.toSet()
                snapshots.value = snapshots.value.filterKeys { it in ids }
                snapshotTimes.keys.retainAll(ids)
                if (selected.value != null && selected.value !in ids) select(null)
                rawDevices.value = others
                devices.value = disambiguateDeviceNames(others)
            }
            "snapshot" -> {
                val snapshot = runCatching { ondoJson.decodeFromJsonElement<ConnectSnapshot>(message["snapshot"] ?: JsonNull) }.getOrNull() ?: return
                val from = message.str("from")
                snapshotTimes[from] = Clock.System.now().toEpochMilliseconds()
                snapshots.value = snapshots.value + (from to snapshot)
                if(selected.value == from) ConnectRouting.remoteState.value = snapshot.state()
                // Spotify-style: follow whichever device starts playing. This only ever pauses local audio.
                val local = player.localUiState.value
                if(shouldFollow(snapshot, from, selected.value, api.settings.deviceId, !local.isPaused && local.currentSong != null, localPlayingSince, localClaimAt, handoffUntil[from] ?: 0L, now())) select(from)
            }
            "error" -> error.value = message.str("message")
            "command" -> {
                if(message.str("to") != api.settings.deviceId) return
                val cmd = message["command"]?.obj() ?: return
                applying = true
                try {
                    when(cmd.str("cmd")) {
                        "status" -> publish()
                        "play" -> player.resume()
                        "pause" -> player.pause()
                        "toggle" -> if(player.localUiState.value.isPaused) player.resume() else player.pause()
                        "skipNext" -> player.next()
                        "skipPrevious" -> player.previous()
                        "seek" -> {
                            val duration = player.localUiState.value.currentSong?.duration?.inWholeMilliseconds ?: 0
                            if(duration > 0) player.seek((cmd.num("seconds") * 1000 / duration).toFloat().coerceIn(0f,1f))
                            // The position isn't part of the change-triggered publish; report the new
                            // one now so the controlling device doesn't show the old time for 10 s.
                            scope.launch { delay(500); runCatching { publish() } }
                        }
                        "shuffle" -> player.setShuffle(cmd["enabled"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false)
                        "repeat" -> player.setRepeatMode(cmd.num("mode").toInt().coerceIn(0, 2))
                        "playAt" -> player.playAt(cmd.num("index").toInt())
                        "remove" -> player.removeFromQueue(cmd.num("index").toInt())
                        "move" -> player.moveQueueItem(cmd.num("from").toInt(), cmd.num("to").toInt())
                        "transfer" -> { selected.value?.let(::markHandoff); select(null); applySnapshot(ondoJson.decodeFromJsonElement(cmd["snapshot"] ?: JsonNull)) }
                        "enqueue" -> {
                            val tracks = ondoJson.decodeFromJsonElement<List<ConnectTrack>>(cmd["tracks"] ?: JsonArray(emptyList())).map { it.song() }
                            require(tracks.size <= 10000)
                            if(cmd.str("position") == "next") player.playNext(tracks) else player.addToQueue(tracks, false)
                        }
                    }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { error.value = "Could not apply remote command" }
                finally { applying = false }
            }
        }
    }
    private fun peerProtocol(id: String) = rawDevices.value.find { it.id == id }?.protocol ?: 1
    private fun route(action: String, songs: List<DomainSong>?, index: Int, value: Float): Boolean {
        val target = selected.value ?: return false
        if(applying) return false
        val state = ConnectRouting.remoteState.value ?: PlayerUiState(isPaused = true)
        // A peer that speaks protocol 2 gets the exact edit, so it doesn't reload (and re-buffer) its whole queue.
        if(peerProtocol(target) >= 2) when(action) {
            "shuffle" -> { command(target, jsonObject("cmd" to "shuffle".j(), "enabled" to (!state.isShuffleEnabled).j())); return true }
            "repeat" -> { command(target, jsonObject("cmd" to "repeat".j(), "mode" to nextRepeatMode(state.repeatMode).j())); return true }
            "playAt" -> { command(target, jsonObject("cmd" to "playAt".j(), "index" to index.j())); return true }
            "remove" -> { command(target, jsonObject("cmd" to "remove".j(), "index" to index.j())); return true }
            "move" -> { command(target, jsonObject("cmd" to "move".j(), "from" to index.j(), "to" to value.toInt().j())); return true }
        }
        when(action) {
            "play", "pause", "skipNext", "skipPrevious" -> control(action)
            "seek" -> command(target, jsonObject("cmd" to "seek".j(), "seconds" to (value * (state.currentSong?.duration?.inWholeMilliseconds ?: 0) / 1000.0).j()))
            "enqueue", "next" -> command(target, jsonObject("cmd" to "enqueue".j(), "position" to (if(action == "next") "next" else "end").j(), "tracks" to ondoJson.encodeToJsonElement(songs.orEmpty().map { it.connectTrack() })))
            else -> {
                val updated = when(action) {
                    "playNow" -> state.copy(queue = songs.orEmpty(), queueEntryIds = songs.orEmpty().map { kotlin.uuid.Uuid.random().toString() }, currentIndex = index, currentSong = songs?.getOrNull(index), progress = 0f, isPaused = false)
                    "playAt" -> state.copy(currentIndex = index, currentSong = state.queue.getOrNull(index), progress = 0f, isPaused = false)
                    "clear" -> PlayerUiState(isPaused = true)
                    "shuffle" -> state.copy(isShuffleEnabled = !state.isShuffleEnabled)
                    "repeat" -> state.copy(repeatMode = nextRepeatMode(state.repeatMode))
                    "remove" -> state.withoutQueueItem(index)
                    "move" -> {
                        val queue = state.queue.toMutableList(); val ids = state.entryIds().toMutableList(); val to = value.toInt()
                        val playingId = ids.getOrNull(state.currentIndex)
                        if(index in queue.indices && to in queue.indices) { queue.add(to, queue.removeAt(index)); ids.add(to, ids.removeAt(index)) }
                        val current = ids.indexOf(playingId)
                        state.copy(queue = queue, queueEntryIds = ids, currentIndex = current, currentSong = queue.getOrNull(current))
                    }
                    else -> { error.value = "This control is unavailable on the remote device"; return true }
                }
                command(target, jsonObject("cmd" to "transfer".j(), "snapshot" to ondoJson.encodeToJsonElement(updated.snapshot())))
            }
        }
        return true
    }
}
