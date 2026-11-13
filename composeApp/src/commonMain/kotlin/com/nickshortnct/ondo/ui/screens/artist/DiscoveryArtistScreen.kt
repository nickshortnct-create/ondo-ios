package com.nickshortnct.ondo.ui.screens.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.ondo.DiscographyActionSheet
import com.nickshortnct.ondo.ondo.DiscographyGridItem
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.str
import com.nickshortnct.ondo.ui.components.layouts.ArtGrid
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.DiscoveryArtistViewModel
import org.koin.compose.koinInject

// Profile screen for an artist found only through a decknas downloader (Lidarr/
// SoulSync/etc.), not yet in the local library -- ArtistDetailScreen can't be
// used since there's no local artistId. Shows a MusicBrainz-sourced discography
// (badged on-deck vs. downloadable) plus any individual track hits already found
// for this artist name. Doubles as the "See all" destination for
// ArtistDetailScreen's own Albums & EPs / Singles carousels (skipLocalRedirect).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryArtistScreen(name: String, musicBrainzId: String, skipLocalRedirect: Boolean = false) {
	val backStack = LocalNavStack.current
	val downloads = koinInject<OndoDownloads>()
	val viewModel = koinViewModel<DiscoveryArtistViewModel> { parametersOf(name, musicBrainzId, skipLocalRedirect) }
	val state by viewModel.state.collectAsStateWithLifecycle()

	var sheetItem by remember { mutableStateOf<MusicResult?>(null) }

	when (val uiState = state) {
		is UiState.Loading -> Scaffold(topBar = { NestedTopBar({ Text(name) }) }) { padding ->
			Column(Modifier.padding(padding).fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
		}
		is UiState.Error -> Scaffold(topBar = { NestedTopBar({ Text(name) }) }) { padding ->
			Text(uiState.error.message ?: "Failed to load artist", modifier = Modifier.padding(padding))
		}
		is UiState.Success -> {
			val data = uiState.data
			val redirectToLocalArtistId = data.redirectToLocalArtistId
			LaunchedEffect(redirectToLocalArtistId) {
				if (redirectToLocalArtistId != null) {
					backStack.removeLastOrNull()
					backStack.add(Screen.ArtistDetail(redirectToLocalArtistId))
				}
			}
			if (redirectToLocalArtistId != null) return
			Scaffold(topBar = { NestedTopBar({ Text(name) }) }) { padding ->
				ArtGrid(contentPadding = padding) {
					if (!data.discographyChecked || !data.tracksChecked) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
					if (data.discographyError != null) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text("Couldn't load discography: ${data.discographyError}")
						}
					} else if (data.discographyChecked && data.albums.isEmpty() && data.singles.isEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text("No discography found for this artist.")
						}
					}
					if (data.albums.isNotEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text("Albums & EPs", style = MaterialTheme.typography.titleMedium)
						}
						itemsIndexed(data.albums.distinctBy { listOf(it.service, it.id, it.title) }, key = { index, row -> "album-" + row.service + "-" + row.id + "-" + row.title + if (row.id.isBlank()) "-$index" else "" }) { _, album ->
							val onDeckAlbum = data.onDeckAlbums[normalizeForMatch(album.title)]
							DiscographyGridItem(
								item = album,
								localCoverArtId = onDeckAlbum?.coverArtId,
								onDeck = onDeckAlbum != null,
								onClick = {
									if (onDeckAlbum != null) {
										backStack.add(Screen.CollectionDetail(onDeckAlbum.id, "artist"))
									} else {
										backStack.add(
											Screen.DiscographyAlbum(
												foreignAlbumId = album.raw.str("foreignAlbumId"),
												title = album.title,
												artistName = album.artist
											)
										)
									}
								},
								tab = "discography"
							)
						}
					}
					if (data.singles.isNotEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text("Singles", style = MaterialTheme.typography.titleMedium)
						}
						itemsIndexed(data.singles.distinctBy { listOf(it.service, it.id, it.title) }, key = { index, row -> "single-" + row.service + "-" + row.id + "-" + row.title + if (row.id.isBlank()) "-$index" else "" }) { _, single ->
							val onDeckAlbum = data.onDeckAlbums[normalizeForMatch(single.title)]
							DiscographyGridItem(
								item = single,
								localCoverArtId = onDeckAlbum?.coverArtId,
								onDeck = onDeckAlbum != null,
								onClick = {
									if (onDeckAlbum != null) {
										backStack.add(Screen.CollectionDetail(onDeckAlbum.id, "artist"))
									} else {
										sheetItem = single
									}
								},
								tab = "discography"
							)
						}
					}
					if (data.tracksFound.isNotEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text("Tracks found", style = MaterialTheme.typography.titleMedium)
						}
						itemsIndexed(data.tracksFound.distinctBy { listOf(it.service, it.id, it.title) }, key = { index, row -> "track-" + row.service + "-" + row.id + "-" + row.title + if (row.id.isBlank()) "-$index" else "" }) { _, track ->
							DiscographyGridItem(
								item = track,
								localCoverArtId = null,
								onDeck = false,
								onClick = { sheetItem = track },
								tab = "discography"
							)
						}
					}
				}
			}
			sheetItem?.let { item ->
				DiscographyActionSheet(item = item, downloads = downloads, onDismissRequest = { sheetItem = null })
			}
		}
	}
}
