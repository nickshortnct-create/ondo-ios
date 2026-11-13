package com.nickshortnct.ondo.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.nickshortnct.ondo.wear.R
import com.nickshortnct.ondo.wear.data.PlayTarget
import com.nickshortnct.ondo.wear.data.SongCollection
import com.nickshortnct.ondo.wear.data.SubsonicApi
import com.nickshortnct.ondo.wear.data.WatchSettings
import kotlinx.coroutines.launch

private const val QUERY_KEY = "query"

@Composable
fun SearchScreen(navigate: (String) -> Unit) {
	val app = ondoApp
	var query by rememberSaveable { mutableStateOf("") }
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		result.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(QUERY_KEY)?.toString() }
			?.takeIf { it.isNotBlank() }
			?.let { query = it }
	}
	fun ask() {
		val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
		RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(QUERY_KEY).setLabel("Search music").build()))
		launcher.launch(intent)
	}
	LaunchedEffect(Unit) { if (query.isBlank()) ask() }
	val loader = rememberLoad(query) { if (query.isBlank()) null else app.api.search(query) }

	ScreenList(title = if (query.isBlank()) "Search" else "“$query”") {
		item { NavButton(if (query.isBlank()) "Search" else "New search", R.drawable.ic_search) { ask() } }
		loadItems(loader) { results: SubsonicApi.SearchResults? ->
			if (results == null) return@loadItems
			if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
				item { Text("No results", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
			}
			if (results.songs.isNotEmpty()) item { ListHeader { Text("Songs") } }
			val collection = SongCollection("Search", query, null, results.songs, null, "search:$query")
			items(results.songs.size) { index ->
				val song = results.songs[index]
				MediaRow(song.title, song.artist, app.api.coverArtUri(song.coverArt, 96)) {
					navigate(Playback.play(app, results.songs, index, false, collection))
				}
			}
			if (results.albums.isNotEmpty()) item { ListHeader { Text("Albums") } }
			items(results.albums) { a ->
				MediaRow(a.name, a.artist, app.api.coverArtUri(a.coverArt, 96)) { navigate(Routes.collection("album", a.id)) }
			}
			if (results.artists.isNotEmpty()) item { ListHeader { Text("Artists") } }
			items(results.artists) { a ->
				MediaRow(a.name, "${a.albumCount} albums", app.api.coverArtUri(a.coverArt, 96), round = true) { navigate(Routes.artist(a.id)) }
			}
		}
	}
}

@Composable
fun DownloadsScreen(navigate: (String) -> Unit) {
	val app = ondoApp
	val collections by app.downloads.collections.collectAsState()
	val progress by app.downloads.progress.collectAsState()
	val pending by app.downloads.pending.collectAsState()
	val used = remember(collections) { app.downloads.usedBytes() }
	ScreenList(title = "Downloads") {
		item {
			Text(
				"%.1f MB on watch".format(used / 1_048_576.0),
				style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
			)
		}
		progress?.let { p ->
			item { NavButton(p.title, R.drawable.ic_download, secondary = "Downloading ${p.done}/${p.total}") {} }
		}
		items(pending) { c -> NavButton(c.title, R.drawable.ic_queue, secondary = "Waiting") {} }
		if (collections.isEmpty() && progress == null) item {
			Text(
				"Open an album or playlist and tap the download button to keep it on the watch for offline listening.",
				style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
			)
		}
		if (collections.isNotEmpty()) item {
			NavButton("Shuffle downloads", R.drawable.ic_shuffle_on) {
				val songs = collections.flatMap { it.songs }.distinctBy { it.id }
				navigate(Playback.play(app, songs, 0, true, forceWatch = true))
			}
		}
		items(collections) { c ->
			MediaRow(c.title, "${c.songs.size} songs · ${c.subtitle}", app.downloads.coverFile(c.coverArt)) {
				navigate(Routes.collection("downloaded", c.key))
			}
		}
	}
}

@Composable
fun SettingsScreen() {
	val app = ondoApp
	val target by app.settings.playTarget.collectAsState()
	val bitrate by app.settings.streamBitrate.collectAsState()
	val account by app.accounts.account.collectAsState()
	val connected by app.phoneLink.phoneConnected.collectAsState()
	val scope = rememberCoroutineScope()
	var syncMessage by remember { mutableStateOf<String?>(null) }
	ScreenList(title = "Settings") {
		item {
			SwitchButton(
				checked = target == PlayTarget.Watch,
				onCheckedChange = { app.settings.setPlayTarget(if (it) PlayTarget.Watch else PlayTarget.Phone) },
				modifier = Modifier.fillMaxWidth(),
				label = { Text("Play on watch") },
				secondaryLabel = { Text(if (target == PlayTarget.Watch) "Library plays here" else "Library plays on phone") },
			)
		}
		item {
			NavButton(
				"Watch quality",
				R.drawable.ic_settings,
				secondary = if (bitrate == 0) "Original" else "$bitrate kbps MP3",
			) {
				val options = WatchSettings.BITRATES
				app.settings.setStreamBitrate(options[(options.indexOf(bitrate) + 1).mod(options.size)])
			}
		}
		item { ListHeader { Text("Account") } }
		item {
			Text(
				account?.let { "${it.username}\n${it.url}" } ?: "Not signed in",
				style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
			)
		}
		item {
			NavButton("Sync login from phone", R.drawable.ic_refresh, secondary = syncMessage ?: if (connected) "Phone connected" else "Phone not connected") {
				scope.launch {
					syncMessage = if (app.phoneLink.requestAccount()) "Requested" else "Phone not reachable"
				}
			}
		}
		item {
			Text(
				"Ondo ${runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()}",
				style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
			)
		}
	}
}
