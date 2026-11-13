package com.nickshortnct.ondo.ui.screens.artist

import ondo.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import ondo.composeapp.generated.resources.notice_acquisition_queued
import ondo.composeapp.generated.resources.action_download_to_server
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickshortnct.ondo.ondo.acquisitionKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.koin.compose.koinInject
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.TrackInfo
import com.nickshortnct.ondo.ondo.j
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.util.toHoursMinutesSeconds
import kotlin.time.Duration.Companion.milliseconds

// Preview for a not-yet-downloaded album/EP from an artist's discography (see
// ArtistDetailScreen/DiscoveryArtistScreen) -- shows its real tracklist, sourced
// from MusicBrainz since Lidarr has no per-track data until an album is actually
// added to its library, plus Download/Choose-release actions. A compact bottom
// sheet has no room for a full tracklist, which is the whole point of this screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscographyAlbumScreen(foreignAlbumId: String, title: String, artistName: String) {
	val downloads = koinInject<OndoDownloads>()
	val scope = rememberCoroutineScope()

	// ensureAlbum() (called by acquire()/releases()) re-resolves the full Lidarr
	// album object by foreignAlbumId before creating anything, so this minimal
	// stand-in only needs to carry enough to identify + display the album.
	val item = remember(foreignAlbumId, title, artistName) {
		MusicResult(
			"Lidarr", "album", foreignAlbumId, title, artistName,
			JsonObject(mapOf(
				"foreignAlbumId" to foreignAlbumId.j(),
				"title" to title.j(),
				"albumType" to "Album".j(),
				"artist" to JsonObject(mapOf("artistName" to artistName.j()))
			))
		)
	}

	var tracks by remember { mutableStateOf<List<TrackInfo>?>(null) }
	var trackError by remember { mutableStateOf<String?>(null) }
	var localBusy by remember { mutableStateOf(false) }
	val pendingItems by downloads.pendingItems.collectAsStateWithLifecycle()
	val requestedItems by downloads.requestedItems.collectAsStateWithLifecycle()
	val busy = localBusy || acquisitionKey(item) in pendingItems
	val requested = acquisitionKey(item) in requestedItems
	var message by remember { mutableStateOf<String?>(null) }
	var failed by remember { mutableStateOf(false) }
	var releases by remember { mutableStateOf<List<MusicResult>?>(null) }

	fun action(block: suspend () -> Unit) {
		if (busy) return
		localBusy = true
		failed = false
		scope.launch {
			message = null
			try { block() }
			catch (e: CancellationException) { throw e }
			catch (e: Exception) { failed = true; message = e.message }
			finally { localBusy = false }
		}
	}

	LaunchedEffect(foreignAlbumId) {
		try {
			tracks = downloads.tracklist(foreignAlbumId)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			trackError = e.message
			tracks = emptyList()
		}
	}

	Scaffold(topBar = { NestedTopBar({ Text(title) }) }) { padding ->
		LazyColumn(
			modifier = Modifier.padding(padding).fillMaxWidth(),
			contentPadding = PaddingValues(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			item {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					CoverArt(
						coverArtId = null,
						localImageUri = "https://coverartarchive.org/release-group/$foreignAlbumId/front-500",
						contentDescription = title,
						modifier = Modifier.fillMaxWidth(0.6f)
					)
					Spacer(Modifier.height(12.dp))
					Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
					Text(
						artistName,
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			item {
				Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
					message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
					val chosenReleases = releases
					if (chosenReleases == null) {
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							Button(
								onClick = { action { message = downloads.acquireEverywhere(item) } },
								enabled = !busy && !requested
							) { Text(if (requested) stringResource(Res.string.notice_acquisition_queued) else stringResource(Res.string.action_download_to_server)) }
							OutlinedButton(
								onClick = { action { val found = downloads.releases(item); if (found.isEmpty()) message = "No releases available" else releases = found } },
								enabled = !busy
							) { Text("Choose release") }
						}
					} else if (chosenReleases.isEmpty()) {
						Text("No releases available")
					} else {
						chosenReleases.forEach { release ->
							TextButton(
								onClick = { action { downloads.grab(release); message = "Release requested" } },
								enabled = !busy
							) { Text(release.title) }
						}
					}
				}
			}
			item { HorizontalDivider() }
			when {
				tracks == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
				trackError != null -> item { Text("Couldn't load tracklist: $trackError") }
				tracks!!.isEmpty() -> item { Text("No tracklist available for this release.") }
				else -> itemsIndexed(tracks!!, key = { index, _ -> index }) { index, track ->
					ListItem(
						leadingContent = {
							Text((index + 1).toString(), style = MaterialTheme.typography.bodyMedium)
						},
						headlineContent = { Text(track.title) },
						trailingContent = {
							track.lengthMs?.let { Text(it.milliseconds.toHoursMinutesSeconds()) }
						}
					)
				}
			}
		}
	}
}
