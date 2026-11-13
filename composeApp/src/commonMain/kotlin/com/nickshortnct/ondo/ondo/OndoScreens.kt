package com.nickshortnct.ondo.ondo

import ondo.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import ondo.composeapp.generated.resources.title_ondo_downloads
import ondo.composeapp.generated.resources.action_ondo_upgrades
import ondo.composeapp.generated.resources.title_ondo_integrations
import ondo.composeapp.generated.resources.title_ondo_connect
import ondo.composeapp.generated.resources.title_ondo_upgrades
import androidx.compose.foundation.layout.*
import com.nickshortnct.ondo.icons.outlined.Devices
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import kotlinx.collections.immutable.persistentListOf
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.domain.manager.LinkManager
import com.nickshortnct.ondo.util.Logger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.koin.compose.koinInject

@Composable
fun OndoLinks() {
    if (!mediaAcquisitionEnabled) return
    val nav = LocalNavStack.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OndoLinkButton(stringResource(Res.string.title_ondo_downloads)) { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads)) }
        OndoLinkButton(stringResource(Res.string.action_ondo_upgrades)) { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Upgrades)) }
        OndoLinkButton(stringResource(Res.string.title_ondo_integrations)) { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Integrations)) }
    }
}

// A compact stand-in for TextButton: Material3's default min-height (40dp) and
// content padding are too generous here, leaving dead space above the cover art.
@Composable
private fun OndoLinkButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OndoScreen(page: com.nickshortnct.ondo.ui.navigation.OndoPage) {
    val title = when(page) { com.nickshortnct.ondo.ui.navigation.OndoPage.Connect -> stringResource(Res.string.title_ondo_connect); com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads -> stringResource(Res.string.title_ondo_downloads); com.nickshortnct.ondo.ui.navigation.OndoPage.Upgrades -> stringResource(Res.string.title_ondo_upgrades); else -> stringResource(Res.string.title_ondo_integrations) }
    Scaffold(topBar = { NestedTopBar({ Text(title) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OndoLinks()
            if (!mediaAcquisitionEnabled && page != com.nickshortnct.ondo.ui.navigation.OndoPage.Connect) { Text("Not available in the iPhone app.", Modifier.padding(16.dp)); return@Column }
            when(page) { com.nickshortnct.ondo.ui.navigation.OndoPage.Connect -> ConnectPanel(); com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads -> DownloadsPanel(); com.nickshortnct.ondo.ui.navigation.OndoPage.Upgrades -> UpgradesPanel(); else -> IntegrationPanel() }
        }
    }
}

@Composable
private fun ConnectPanel() {
    val connect = koinInject<OndoConnect>()
    val settings = koinInject<OndoSettings>()
    val online by connect.connected.collectAsState()
    val devices by connect.devices.collectAsState()
    val selected by connect.selected.collectAsState()
    val snapshots by connect.snapshots.collectAsState()
    val error by connect.error.collectAsState()
    val enabled by connect.enabled.collectAsState()
    val config = settings.get("Connect")
    var url by remember { mutableStateOf(config.url) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(settings.deviceName) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val signedIn = config.key.isNotBlank()
    fun act(block: suspend () -> Unit) { scope.launch {
        busy = true; message = null
        try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { message = e.message }
        finally { busy = false }
    } }
    val (statusTitle, statusColor) = when {
        !enabled -> "Off" to MaterialTheme.colorScheme.onSurfaceVariant
        !signedIn -> "Not signed in" to MaterialTheme.colorScheme.error
        online -> "Connected" to ConnectGreen
        else -> "Connecting…" to MaterialTheme.colorScheme.tertiary
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ondo Connect · $statusTitle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    !enabled -> "Play music on one device and control it from all the others, like Spotify Connect."
                                    !signedIn -> "Sign in once with your music server account."
                                    online -> "Signed in as ${config.server.ifBlank { "you" }} · ${devices.size} other device${if(devices.size == 1) "" else "s"} online"
                                    else -> error ?: "Reaching the relay on decknas…"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(enabled, { on -> if(on && !signedIn) act { connect.signInWithServerAccount() } else connect.setEnabled(on) })
                    }
                    if(enabled && !signedIn) Button(
                        onClick = { act { connect.signInWithServerAccount() } }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ConnectGreen, contentColor = androidx.compose.ui.graphics.Color.Black)
                    ) { Text(if(busy) "Signing in…" else "Sign in with my music server account", fontWeight = FontWeight.SemiBold) }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if(enabled && signedIn) {
            item { Text("Play on", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            item { ConnectDeviceRow("This phone", if(selected == null) "Playing here" else null, current = selected == null) { connect.playOn(null) } }
            items(devices, key = { it.id }) { device ->
                val snap = snapshots[device.id]
                val track = snap?.currentIndex?.let { snap.queue.getOrNull(it) }
                ConnectDeviceRow(device.name, track?.let { "${if(snap?.playing == true) "Playing" else "Paused"} · ${it.title} — ${it.artist}" }, current = selected == device.id) { connect.playOn(device.id) }
            }
            if(online && devices.isEmpty()) item { Text("Other devices show up here when Ondo is open on them with Connect on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        item {
            OutlinedTextField(name, { name = it; settings.deviceName = it }, label = { Text("This phone’s name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { advanced = !advanced }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if(advanced) "Hide" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        if(advanced) {
            item { OutlinedTextField(url, { url = it }, label = { Text("Relay address") }, supportingText = { Text("If it's unreachable, Ondo also tries the DuckDNS and Tailscale Funnel routes.") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Text("Sign in with a different Connect account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { act { connect.authenticate(url, username, password, false); password = "" } }, enabled = !busy && username.isNotBlank() && password.isNotBlank()) { Text("Sign in") }
                    TextButton(onClick = { act { connect.authenticate(url, username, password, true); password = "" } }, enabled = !busy && username.isNotBlank() && password.isNotBlank()) { Text("Register") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { connect.reconnect() }) { Text("Reconnect") }
                    if(signedIn) TextButton(onClick = { connect.logout() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun ConnectDeviceRow(name: String, detail: String?, current: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if(current) ConnectGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(if(current) ConnectGreen else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(com.nickshortnct.ondo.icons.Icons.Outlined.Devices, contentDescription = null, tint = if(current) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, color = if(current) ConnectGreen else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun IntegrationPanel() {
    val settings = koinInject<OndoSettings>()
    val api = koinInject<OndoApi>()
    val services = listOf("Lidarr", "SoulSync", "Squid", "YouTube", "AudioMuse", "ListenBrainz")
    var selected by rememberSaveable { mutableStateOf("Lidarr") }
    var cfg by remember(selected) { mutableStateOf(settings.get(selected)) }
    var message by remember(selected) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var choices by remember(selected) { mutableStateOf<Map<String,JsonArray>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState())) { services.forEach { name -> FilterChip(selected == name, { selected = name }, label = { Text(name) }) } } }
        item { Text("Settings apply to the current music-server account. New music is added to your shared library.") }
        if (selected in listOf("Lidarr", "SoulSync", "Squid", "YouTube")) {
            item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(cfg.enabled, { cfg = cfg.copy(enabled = it) })
                Text("Show this source in Downloads")
            } }
            item { Text("Connected sources remain available in search. Requests from Ondo appear in Downloads automatically.", style = MaterialTheme.typography.bodySmall) }
        }
        item { OutlinedTextField(cfg.url, { cfg = cfg.copy(url = it) }, label = { Text("Service URL") }, modifier = Modifier.fillMaxWidth()) }
        if (selected in listOf("Lidarr", "SoulSync", "Squid", "YouTube")) item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(cfg.usesNavidromeAuth, { cfg = cfg.copy(authMode = if (it) ServiceConfig.AUTH_MODE_NAVIDROME else "") })
            Text("Sign in with my music-server login (gateway)")
        } }
        if (selected in listOf("Lidarr", "SoulSync", "Squid", "YouTube")) item {
            Text("Music-server login requires a compatible gateway at the service URL. Otherwise leave it off and use this service’s API key.", style = MaterialTheme.typography.bodySmall)
        }
        if (!cfg.usesNavidromeAuth) item { OutlinedTextField(cfg.key, { cfg = cfg.copy(key = it) }, label = { Text("API key / token") }, supportingText = { Text("Stored encrypted on this device") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
        if (!cfg.usesNavidromeAuth && cfg.key.isBlank() && selected in listOf("Lidarr", "SoulSync", "Squid", "YouTube")) item {
            Text("No key needed: Ondo connects through decknas with the music server account you're signed in with (a Navidrome admin account). Only enter a key to reach this service directly at the URL above.", style = MaterialTheme.typography.bodySmall)
        }
        if(selected == "SoulSync") item { OutlinedTextField(cfg.profile, { cfg = cfg.copy(profile = it) }, label = { Text("Profile ID") }) }
        if(selected == "AudioMuse") item { OutlinedTextField(cfg.server, { cfg = cfg.copy(server = it) }, label = { Text("Media server ID") }) }
        if(selected == "Lidarr") {
            item { Button(onClick = { scope.launch {
                busy = true
                try { choices = listOf("qualityprofile", "metadataprofile", "rootfolder").associateWith { api.call("Lidarr", "/$it", config = cfg).arr() }; message = null }
                catch(e: CancellationException) { throw e } catch(e: Exception) { message = e.message } finally { busy = false }
            } }, enabled = !busy) { Text("Load Lidarr profiles and folders") } }
            choices.forEach { (kind, rows) -> item {
                Text(when(kind) { "qualityprofile" -> "Quality profile"; "metadataprofile" -> "Metadata profile"; else -> "Library folder" })
                rows.forEach { row -> val value = if(kind == "rootfolder") row.str("path") else row.str("id")
                    val checked = when(kind) { "qualityprofile" -> cfg.quality == value; "metadataprofile" -> cfg.metadata == value; else -> cfg.root == value }
                    Row { RadioButton(checked, { cfg = when(kind) { "qualityprofile" -> cfg.copy(quality = value); "metadataprofile" -> cfg.copy(metadata = value); else -> cfg.copy(root = value) } }); Text(row.str("name",row.str("path"))) }
                }
            } }
            item { Text("Folder: ${cfg.root.ifBlank { "not selected" }} · Quality: ${cfg.quality.ifBlank { "not selected" }} · Metadata: ${cfg.metadata.ifBlank { "not selected" }}") }
        }
        item { Row {
            Button(onClick = {
                message = try { settings.save(selected,cfg); "Saved" }
                catch (e: Exception) { e.message ?: "Could not save settings. Try again." }
            }) { Text("Save") }
            TextButton(onClick = { scope.launch { busy = true; try { val response = api.test(selected,cfg); if(selected == "ListenBrainz") check(response.flag("valid")) { "Token is invalid" }; message = "Connection verified" } catch(e: CancellationException) { throw e } catch(e: Exception) { message = e.message } finally { busy = false } } }, enabled = !busy) { Text("Test connection") }
        } }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { item { Text(it) } }
    }
}

// Discovery result card shared by the unified search screen (for a not-yet-on-deck
// hit) and DiscoveryArtistScreen (for a discography row) -- same buttons the old
// DiscoveryPanel had, just reusable outside that screen now.
@Composable
fun DiscoveryResultCard(item: MusicResult, downloads: OndoDownloads, player: MediaPlayerViewModel, busy: Boolean, onDeck: Boolean = false, action: (suspend () -> Unit) -> Unit, onResults: (List<MusicResult>) -> Unit, onMessage: (String?) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        Text("${item.artist} · ${item.kind} · ${item.service}")
        if(item.kind == "release") Text(item.raw.obj()["rejections"].orEmptyArray().joinToString { (it as? JsonPrimitive)?.content.orEmpty() })
        if(onDeck) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
			Text("✓ On deck", style = MaterialTheme.typography.labelMedium)
        } else Row(Modifier.horizontalScroll(rememberScrollState())) {
            if(item.kind != "library") TextButton(onClick = { action { onMessage(if(item.kind == "release") { downloads.grab(item); "Release requested" } else downloads.acquire(item)) } }, enabled = !busy && !(item.service == "Lidarr" && item.kind == "artist") && !(item.service == "KHInsider" && item.kind == "album") && !item.raw.flag("rejected")) { Text(if(item.service == "SoulSync" && item.kind == "artist") "Watch artist" else "Add to library") }
            if(item.kind in listOf("artist", "album") && item.service != "SoulSync") TextButton(onClick = { action { val results = downloads.browse(item); onResults(results); if(results.isEmpty()) onMessage("No albums or tracks found") } }, enabled = !busy) { Text("Browse") }
            if(item.service == "Lidarr" && item.kind == "album") TextButton(onClick = { action { val results = downloads.releases(item); onResults(results); if(results.isEmpty()) onMessage("No releases available") } }, enabled = !busy) { Text("Choose release") }
            if(item.kind == "library") TextButton(onClick = { action {
                player.playNow(ConnectTrack(item.id,item.title,item.artist).song())
            } }, enabled = !busy) { Text("Play") }
        }
    } }
}

// Semantic library search (AudioMuse / lyrics) has different intent from the
// library search results, so it remains a mode switch inside the search screen.
@Composable
fun ExtraSearchModes(modifier: Modifier = Modifier) {
    val downloads = koinInject<OndoDownloads>()
    val player = koinInject<MediaPlayerViewModel>()
    var mode by rememberSaveable { mutableStateOf("AudioMuse") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun action(block: suspend () -> Unit) { scope.launch { busy = true; message = null; try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { message = e.message } finally { busy = false } } }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("AudioMuse", "AudioMuse lyrics").forEach { name -> FilterChip(mode == name, { if(!busy) { mode = name; results = emptyList(); message = null } }, label = { Text(name) }) } } }
        item { OutlinedTextField(query, { query = it }, label = { Text("Sound or lyrics you're looking for") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { action { results = downloads.search(mode,query); if(results.isEmpty()) message = "No results" } }, enabled = !busy && query.isNotBlank()) { Text("Search") } }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { item { Text(it) } }
        items(results) { item -> DiscoveryResultCard(item, downloads, player, busy, action = ::action, onResults = { results = it }, onMessage = { message = it }) }
    }
}

@Composable
fun YouTubeSearchTab(query: String, modifier: Modifier = Modifier, isOnline: Boolean = true) {
    val downloads = koinInject<OndoDownloads>()
    val nav = LocalNavStack.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var selected by remember { mutableStateOf<YouTubeVideo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val requestedKeys by downloads.requestedItems.collectAsStateWithLifecycle()
    val pendingKeys by downloads.pendingItems.collectAsStateWithLifecycle()
    fun isQueued(video: YouTubeVideo): Boolean = acquisitionKey("track", video.title, video.channel) in requestedKeys
    fun isPending(video: YouTubeVideo): Boolean = video.id in pendingIds || acquisitionKey("track", video.title, video.channel) in pendingKeys
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, isOnline, refresh) {
        results = emptyList()
        selected = null
        message = null
        searchFailed = false
        messageIsError = false
        loading = false
        if (query.isBlank()) return@LaunchedEffect
        if (!isOnline) { message = "Connect to the internet to search YouTube"; return@LaunchedEffect }
        if (query.trim().length < 2) { message = "Keep typing to find music"; return@LaunchedEffect }
        loading = true
        try {
            delay(350)
            results = withTimeoutOrNull(12_000L) { downloads.searchYouTube(query.trim()) }?.distinctBy { it.id }
                ?: error("YouTube is taking too long to respond. Try again.")
            if (results.isEmpty()) { message = "No videos found. Try a song title and artist."; messageIsError = false }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "YouTube search failed"; messageIsError = true; searchFailed = true }
        finally { loading = false }
    }
    fun addVideo(video: YouTubeVideo) {
        if (isPending(video) || isQueued(video) || !isOnline) return
        pendingIds = pendingIds + video.id
        scope.launch {
            try {
                val result = downloads.downloadYouTubeVideo(video)
                val task = result.obj()["task"]
                check(task?.let { downloadQueueState(it.str("status")) != DownloadQueueState.FAILED } != false) { "This video could not be added. Try another result." }
                message = if (task?.str("status") == "done") "Already added to your library" else "Adding to your library. Follow progress in Downloads."
                messageIsError = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not add this video"; messageIsError = true }
            finally { pendingIds = pendingIds - video.id }
        }
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (query.isBlank()) item { Text("Find music on YouTube", style = MaterialTheme.typography.titleMedium); Text("Search for a song, artist, or video using the search field above.") }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { text -> item {
            Text(text, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (searchFailed && isOnline) TextButton(onClick = { refresh++ }) { Text("Retry search") }
            if (!messageIsError && requestedKeys.isNotEmpty()) TextButton(onClick = { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads)) }) { Text("View downloads") }
        } }
        selected?.let { video -> item(key = "youtube-preview") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                YouTubeWebView("https://www.youtube.com/embed/${video.id}?playsinline=1", Modifier.fillMaxWidth().height(220.dp))
                Text(video.title, style = MaterialTheme.typography.titleMedium)
                Text(video.channel, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = isOnline && !isPending(video) && !isQueued(video), onClick = { addVideo(video) }) {
                        Text(when { isPending(video) -> "Adding…"; isQueued(video) -> "Queued"; else -> "Add to library" })
                    }
                    TextButton(onClick = { selected = null }) { Text("Close preview") }
                }
            }
        } }
        items(results, key = { it.id }) { video ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { selected = video }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AsyncImage(model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(100.dp).height(64.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(listOf(video.channel, video.album).filter { it.isNotBlank() }.distinct().joinToString(" • "), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(listOfNotNull(if (video.official) "Official audio" else "YouTube video", video.durationMs?.let { formatYouTubeDuration(it) }).joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(enabled = isOnline && !isPending(video) && !isQueued(video), onClick = { addVideo(video) }) {
                        Text(when { isPending(video) -> "Adding…"; isQueued(video) -> "Queued"; else -> "Add to library" })
                    }
                    TextButton(onClick = { selected = video }) { Text("Preview") }
                }
            } }
        }
    }
}

private fun formatYouTubeDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun DownloadsPanel() {
    val downloads = koinInject<OndoDownloads>()
    val deviceManager = koinInject<DownloadManager>()
    val connectivity = koinInject<ConnectivityManager>()
    val isOnline by connectivity.isOnline.collectAsStateWithLifecycle()
    val player = koinInject<MediaPlayerViewModel>()
    val preferences = koinInject<PreferenceManager>()
    val songDao = koinInject<SongDao>()
    val nav = LocalNavStack.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceDownloads by deviceManager.allDownloads.collectAsStateWithLifecycle(initialValue = persistentListOf())
    val deviceActive by deviceManager.activeDownloadCount.collectAsStateWithLifecycle()
    val sharedPending by downloads.pendingItems.collectAsStateWithLifecycle()
    var deviceSongs by remember { mutableStateOf<Map<String, DomainSong>>(emptyMap()) }
    val songIds = deviceDownloads.map { it.songId }.distinct().sorted()
    LaunchedEffect(songIds) {
        deviceSongs = com.nickshortnct.ondo.util.chunkedIn(songIds) { songDao.getSongsByIds(it) }.associate { entity -> entity.songId to entity.toDomainModel() }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var downloadQuery by rememberSaveable { mutableStateOf("") }
    var downloadFilter by rememberSaveable { mutableStateOf("All") }
    val searchTerms = normalizeForMatch(downloadQuery).split(' ').filter { it.isNotBlank() }
    fun matchesDownload(title: String, artist: String): Boolean {
        val searchable = normalizeForMatch("$title $artist")
        return searchTerms.all(searchable::contains)
    }
    var rowsByService by remember { mutableStateOf<Map<String, List<DownloadRow>>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var pendingActions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh, selectedTab, lifecycleOwner, isOnline) {
        if (!isOnline) { busy = false; loaded = true; return@LaunchedEffect }
        if (selectedTab == 0) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                busy = true
                try {
                    val snapshot = downloads.queueAll()
                    val connected = downloads.providers.filter { downloads.api.settings.get(it).url.isNotBlank() && downloads.api.settings.shouldPollDownloads(it) }.toSet()
                    // A temporarily unavailable source keeps its last known rows.
                    rowsByService = rowsByService.filterKeys { it in connected } + snapshot.rowsByService
                    errors = snapshot.errorsByService
                    loaded = true
                } finally { busy = false }
                val active = rowsByService.values.flatten().any { it.state == DownloadQueueState.ACTIVE }
                delay(if (active) 4_000L else 20_000L)
            }
        }
    }
    fun queueAction(row: DownloadRow, retry: Boolean) {
        if (row.key in pendingActions) return
        pendingActions = pendingActions + row.key
        scope.launch {
            try {
                message = if (retry) downloads.retry(row) else { downloads.cancel(row); "Request cancelled" }
                messageIsError = false
                refresh++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not update this request"; messageIsError = true }
            finally { pendingActions = pendingActions - row.key }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Library") })
            FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("This device") })
        }
        OutlinedTextField(value = downloadQuery, onValueChange = { downloadQuery = it }, label = { Text("Find a song or artist") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "In progress", "Needs attention", "Ready").forEach { filter ->
                FilterChip(selected = downloadFilter == filter, onClick = { downloadFilter = filter }, label = { Text(filter) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedTab == 0) {
                val rows = rowsByService.values.flatten().distinctBy { it.key }.filter { matchesDownload(it.title, it.artist) }
                val active = rows.filter { it.state == DownloadQueueState.ACTIVE && downloadFilter in listOf("All", "In progress") }
                val attention = rows.filter { (it.state == DownloadQueueState.FAILED || it.state == DownloadQueueState.UNKNOWN) && downloadFilter in listOf("All", "Needs attention") }
                val completed = rows.filter { it.state == DownloadQueueState.COMPLETE && downloadFilter in listOf("All", "Ready") }.sortedByDescending { it.raw.str("completedAt").ifBlank { it.raw.str("updatedAt") }.ifBlank { it.raw.str("createdAt") } }.take(20)
                item { Text("New music is added to your shared library. Requests keep running when you close the app.", style = MaterialTheme.typography.bodyMedium) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { refresh++ }, enabled = isOnline && !busy) { Text("Refresh") }
                    TextButton(onClick = { nav.add(Screen.Library(nested = true)) }) { Text("View library") }
                } }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                message?.let { text -> item { Text(text, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }
                errors.forEach { (service, error) -> item(key = "error:$service") {
                    Text("$service couldn’t update. $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Integrations)) }) { Text("Open Integrations") }
                    if (rowsByService[service]?.isNotEmpty() == true) Text("Showing the last known progress", style = MaterialTheme.typography.bodySmall)
                } }
                if (!isOnline) item { Text("Offline. Server requests keep running; reconnect to update their progress.", style = MaterialTheme.typography.bodySmall) }
                if (loaded && active.isEmpty() && attention.isEmpty() && completed.isEmpty()) item {
                    Text(if (searchTerms.isNotEmpty() || downloadFilter != "All") "No matching downloads" else if (errors.isNotEmpty()) "Current downloads are unavailable. Refresh to try again." else "No library requests yet", style = MaterialTheme.typography.titleMedium)
                    Text(if (searchTerms.isNotEmpty() || downloadFilter != "All") "Try another title or change the status filter." else "Find a song or album in Search and choose Add to library.")
                    if (searchTerms.isNotEmpty() || downloadFilter != "All") TextButton(onClick = { downloadQuery = ""; downloadFilter = "All" }) { Text("Clear filters") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nav.add(Screen.Search(nested = true)) }) { Text("Find music") }
                        if (downloads.providers.none { downloads.api.settings.shouldPollDownloads(it) && downloads.api.settings.get(it).url.isNotBlank() }) {
                            TextButton(onClick = { nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Integrations)) }) { Text("Connect a source") }
                        }
                    }
                }
                if (active.isNotEmpty()) item(key = "active-heading") { Text("In progress · ${active.size}", style = MaterialTheme.typography.titleMedium) }
                items(active, key = { it.key }) { row -> DownloadRequestCard(row, !isOnline || row.key in pendingActions || acquisitionKey(row) in sharedPending, { queueAction(row, false) }, { queueAction(row, true) }) }
                if (attention.isNotEmpty()) item(key = "attention-heading") { Text("Needs attention · ${attention.size}", style = MaterialTheme.typography.titleMedium) }
                items(attention, key = { it.key }) { row -> DownloadRequestCard(row, !isOnline || row.key in pendingActions || acquisitionKey(row) in sharedPending, { queueAction(row, false) }, { queueAction(row, true) }) }
                if (completed.isNotEmpty()) item(key = "completed-heading") {
                    Text("Recently finished", style = MaterialTheme.typography.titleMedium)
                    Text("Your library may take a moment to show newly added music.", style = MaterialTheme.typography.bodySmall)
                }
                items(completed, key = { it.key }) { row ->
                    Column {
                        DownloadRequestCard(row, false, {}, {})
                        TextButton(onClick = { nav.add(Screen.Search(nested = true, query = row.title)) }) { Text("Find in library") }
                    }
                }
            } else {
                val matching = deviceDownloads.filter { download -> val song = deviceSongs[download.songId]; matchesDownload(song?.title ?: "Saved song", song?.artistName.orEmpty()) }
                val active = matching.filter { it.status == DownloadStatus.DOWNLOADING && downloadFilter in listOf("All", "In progress") }
                val failed = matching.filter { it.status == DownloadStatus.FAILED && downloadFilter in listOf("All", "Needs attention") }
                val saved = matching.filter { it.status == DownloadStatus.DOWNLOADED && downloadFilter in listOf("All", "Ready") }
                item { Text("Save music from your library for offline listening on this device.") }
                item { Text("${deviceDownloads.count { it.status == DownloadStatus.DOWNLOADED }} songs saved · ${deviceDownloads.count { it.status == DownloadStatus.DOWNLOADING }} in progress", style = MaterialTheme.typography.titleMedium) }
                if (deviceDownloads.isNotEmpty() && active.isEmpty() && failed.isEmpty() && saved.isEmpty()) item {
                    Text("No matching downloads")
                    TextButton(onClick = { downloadQuery = ""; downloadFilter = "All" }) { Text("Clear filters") }
                }
                if (deviceActive > 0) item { TextButton(onClick = { deviceManager.cancelAllActiveDownloads() }) { Text("Cancel active downloads") } }
                if (deviceDownloads.isEmpty()) item {
                    Text("No offline downloads yet", style = MaterialTheme.typography.titleMedium)
                    Text("Open a song, album, or playlist in your library and choose Download.")
                    TextButton(onClick = { nav.add(Screen.Library(nested = true)) }) { Text("Browse library") }
                }
                items(active + failed + saved, key = { "device:${it.songId}" }) { download ->
                    val song = deviceSongs[download.songId]
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(song?.title ?: "Saved song", style = MaterialTheme.typography.titleMedium)
                        song?.artistName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        when (download.status) {
                            DownloadStatus.DOWNLOADING -> {
                                Text(if (!isOnline) "Waiting for connection" else if (download.retryCount > 0) "Retrying · attempt ${download.retryCount + 1}" else if (download.progress > 0f) "Downloading · ${(download.progress * 100).toInt()}%" else "Queued or preparing audio", style = MaterialTheme.typography.bodySmall)
                                if (download.progress > 0f) LinearProgressIndicator(progress = { download.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = { deviceManager.cancelDownload(download.songId) }) { Text("Cancel") }
                            }
                            DownloadStatus.FAILED -> {
                                Text("Couldn’t save this song. Check your connection and try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (song != null) TextButton(enabled = isOnline, onClick = { deviceManager.downloadSong(song) }) { Text("Retry") }
                                    TextButton(onClick = { deviceManager.cancelDownload(download.songId) }) { Text("Dismiss") }
                                }
                            }
                            DownloadStatus.DOWNLOADED -> {
                                Text("Available offline", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (song != null) TextButton(
                                        enabled = song.explicitStatus != DomainExplicitStatus.Explicit || preferences.explicitContentPlayback == ExplicitContentPlayback.Allowed,
                                        onClick = { player.playNow(song) }
                                    ) { Text("Play") }
                                    TextButton(onClick = { deviceManager.deleteDownload(download.songId) }) { Text("Remove from device") }
                                }
                            }
                            DownloadStatus.NOT_DOWNLOADED -> Unit
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun DownloadRequestCard(row: DownloadRow, busy: Boolean, onCancel: () -> Unit, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(row.title, style = MaterialTheme.typography.titleMedium)
        if (row.artist.isNotBlank()) Text(row.artist, style = MaterialTheme.typography.bodySmall)
        Text("${row.statusLabel} · ${row.service}", style = MaterialTheme.typography.bodySmall, color = if (row.state == DownloadQueueState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.state == DownloadQueueState.ACTIVE) {
            row.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
        if (row.state == DownloadQueueState.FAILED && !row.canRetry && row.detail.isBlank()) Text("Try another result for this song, or check the source in Integrations.", style = MaterialTheme.typography.bodySmall)
        if (row.canCancel || row.canRetry) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.canRetry) TextButton(onClick = onRetry, enabled = !busy) { Text(if (busy) "Working…" else row.retryLabel) }
            if (row.canCancel) TextButton(onClick = onCancel, enabled = !busy) { Text(row.cancelLabel) }
        }
    } }
}
