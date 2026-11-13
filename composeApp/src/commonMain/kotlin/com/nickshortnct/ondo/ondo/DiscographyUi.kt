package com.nickshortnct.ondo.ondo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Check
import com.nickshortnct.ondo.ui.components.layouts.ArtCarouselItem
import com.nickshortnct.ondo.ui.components.layouts.ArtGridItem
import com.nickshortnct.ondo.ui.components.sheets.ModalBottomSheet

// Best-effort remote artwork for a not-yet-downloaded MusicResult, since none of
// these have local cover art of their own. Lidarr/MusicBrainz album results (see
// OndoDownloads.discography()) have no image field at all, only a release-group
// id (stored as foreignAlbumId) to fetch one from the Cover Art Archive by; other
// services' track/album hits (SoulSync, Squid, KHInsider) already carry a direct
// image URL in one of a few differently-named raw fields. Not every release has
// art in the Archive; CoverArt already falls back to a placeholder icon on a
// failed load either way.
fun MusicResult.previewImageUrl(): String? = when {
	kind == "album" && service == "Lidarr" ->
		raw.str("foreignAlbumId").takeIf { it.isNotBlank() }?.let { "https://coverartarchive.org/release-group/$it/front-250" }
	// Lidarr's artist lookup (and some album entries) carry art as an
	// `images: [{coverType, url}]` array rather than a flat field -- prefer
	// a "poster" if tagged, else whatever's first, before falling back to
	// the flat-field services (SoulSync/Squid/KHInsider).
	raw.obj()["images"] is JsonArray -> raw.obj()["images"]!!.arr().let { images ->
		images.firstOrNull { it.str("coverType") == "poster" } ?: images.firstOrNull()
	}?.str("url").takeIf { !it.isNullOrBlank() }
	else -> raw.str("image_url").ifBlank { raw.str("coverUrl") }.ifBlank { raw.str("image") }.takeIf { it.isNotBlank() }
}

@Composable
fun OnDeckBadge(modifier: Modifier = Modifier) {
	Box(
		modifier
			.size(22.dp)
			.clip(CircleShape)
			.background(Color(0xFF2E7D32)),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = Icons.Outlined.Check,
			contentDescription = "In your library",
			tint = Color.White,
			modifier = Modifier.size(14.dp)
		)
	}
}

// Cover-art carousel card for a discography entry (album/EP/single/track), used
// on artist profiles -- see ArtistDetailScreen and DiscoveryArtistScreen. Local
// cover art if it's already on deck, else a best-effort Cover Art Archive image.
@Composable
fun CarouselItemScope.DiscographyCarouselItem(
	item: MusicResult,
	localCoverArtId: String?,
	onDeck: Boolean,
	onClick: () -> Unit
) {
	ArtCarouselItem(
		coverArtId = localCoverArtId,
		localImageUri = if (localCoverArtId == null) item.previewImageUrl() else null,
		title = item.title,
		contentDescription = item.title,
		badge = if (onDeck) ({ OnDeckBadge() }) else null,
		onClick = onClick
	)
}

// Grid-tile variant of the above, for the full "see all" discography list.
@Composable
fun DiscographyGridItem(
	item: MusicResult,
	localCoverArtId: String?,
	onDeck: Boolean,
	onClick: () -> Unit,
	tab: String
) {
	Box {
		ArtGridItem(
			onClick = onClick,
			coverArtId = localCoverArtId,
			localImageUri = if (localCoverArtId == null) item.previewImageUrl() else null,
			title = item.title,
			subtitle = item.artist.takeIf { it.isNotBlank() && it != item.title },
			id = item.service + item.id + item.title,
			tab = tab
		)
		if (onDeck) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { OnDeckBadge() }
	}
}

// Tapping a not-yet-downloaded discography card opens this instead of inline
// per-card buttons -- keeps the parent Albums/Singles list state untouched no
// matter what happens here (download, browse releases, errors), which is what
// the old shared DiscoveryResultCard's onResults callback got wrong: browsing
// or choosing a release for one card used to replace the *entire* section's
// list with that card's (often empty) sub-results.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiscographyActionSheet(
	item: MusicResult,
	downloads: OndoDownloads,
	onDismissRequest: () -> Unit
) {
	val scope = rememberCoroutineScope()
	val nav = LocalNavStack.current
	var localBusy by remember(item) { mutableStateOf(false) }
	val requestedItems by downloads.requestedItems.collectAsStateWithLifecycle()
	val pendingItems by downloads.pendingItems.collectAsStateWithLifecycle()
	val busy = localBusy || acquisitionKey(item) in pendingItems
	var submittedSubResult by remember(item) { mutableStateOf(false) }
	val requested = acquisitionKey(item) in requestedItems || submittedSubResult
	var failed by remember(item) { mutableStateOf(false) }
	var message by remember(item) { mutableStateOf<String?>(null) }
	// Two different sub-lists can land here: "releases" (specific Lidarr
	// editions, tapped via grab()) and "browse" (alternate title matches for
	// Lidarr, or a KHInsider album's individual tracks -- either way tapped
	// via acquire()). subMode remembers which one so a tap does the right thing.
	var subResults by remember(item) { mutableStateOf<List<MusicResult>?>(null) }
	var subMode by remember(item) { mutableStateOf("") }
	fun action(block: suspend () -> Unit) {
		if (busy) return
		localBusy = true
		failed = false
		scope.launch {
			message = null
			try { block() }
			catch (e: CancellationException) { throw e }
			catch (e: Exception) { failed = true; message = e.message ?: "Couldn't complete this request. Try again." }
			finally { localBusy = false }
		}
	}
	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text(item.title, style = MaterialTheme.typography.titleMedium)
			Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
			Text("Adds music to your shared library. Once it is ready, you can play it or save it on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
			if (requested) TextButton(onClick = { onDismissRequest(); nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads)) }) { Text("View progress in Downloads") }
			val results = subResults
			if (results == null) {
				Button(
					onClick = { action {
						message = if (item.service == "Lidarr" && item.kind == "album") downloads.acquireEverywhere(item)
						else downloads.acquire(item)
						submittedSubResult = true
					} },
					// KHInsider only exposes tracks (via Browse below), never a
					// directly-acquirable "album" -- acquire() would just throw.
					enabled = !busy && !requested && !(item.service == "Lidarr" && item.kind == "artist") && !(item.service == "KHInsider" && item.kind == "album") && !item.raw.flag("rejected"),
					modifier = Modifier.fillMaxWidth()
					) { Text(if (requested) "Added to queue" else if (busy) "Adding…" else if (item.service == "SoulSync" && item.kind == "artist") "Follow artist releases" else "Add to library") }
				// Alternate title matches (Lidarr) or a KHInsider album's tracks.
				if ((item.kind == "artist" && item.service == "Lidarr") || (item.kind == "album" && item.service == "KHInsider")) {
					OutlinedButton(
						onClick = { action { val found = downloads.browse(item); if (found.isEmpty()) message = "No albums or tracks found" else { subResults = found; subMode = "browse" } } },
						enabled = !busy,
						modifier = Modifier.fillMaxWidth()
					) { Text("Browse") }
				}
				// Pick a specific edition -- releases() is a Lidarr-only lookup.
				if (item.kind == "album" && item.service == "Lidarr") {
					OutlinedButton(
						onClick = { action { val found = downloads.releases(item); if (found.isEmpty()) message = "No releases available" else { subResults = found; subMode = "releases" } } },
						enabled = !busy,
						modifier = Modifier.fillMaxWidth()
					) { Text("Choose release") }
				}
			} else if (results.isEmpty()) {
				Text("No results")
			} else {
				TextButton(onClick = { subResults = null; subMode = ""; message = null }) { Text("Back to album") }
				results.forEach { sub ->
					TextButton(
						onClick = { action {
							if (subMode == "releases") { downloads.grab(sub); message = "Release requested" }
							else { message = downloads.acquire(sub) }
							submittedSubResult = true
						} },
						enabled = !busy && acquisitionKey(sub) !in pendingItems && acquisitionKey(sub) !in requestedItems && !sub.raw.flag("rejected"),
						modifier = Modifier.fillMaxWidth()
					) { Text(sub.title) }
				}
			}
		}
	}
}
