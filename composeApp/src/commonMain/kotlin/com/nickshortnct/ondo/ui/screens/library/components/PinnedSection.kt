package com.nickshortnct.ondo.ui.screens.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_unpin
import ondo.composeapp.generated.resources.info_pinned_album
import ondo.composeapp.generated.resources.info_pinned_artist
import ondo.composeapp.generated.resources.info_pinned_playlist
import ondo.composeapp.generated.resources.info_pinned_song
import ondo.composeapp.generated.resources.title_pinned
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.PushPinOff
import com.nickshortnct.ondo.ondo.PinKind
import com.nickshortnct.ondo.ondo.PinnedItem
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.layouts.header
import com.nickshortnct.ondo.ui.navigation.Screen

/** The row of pinned albums, playlists, artists and songs at the top of Library. */
fun LazyGridScope.pinnedSection(
	pins: List<PinnedItem>,
	playlistsById: Map<String, DomainPlaylist>,
	onPlaySong: (DomainSong) -> Unit,
	onUnpin: (PinnedItem) -> Unit
) {
	if (pins.isEmpty()) return
	header(Res.string.title_pinned, destination = Screen.Library(), active = false)
	item(span = { GridItemSpan(maxLineSpan) }) {
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(horizontal = 16.dp),
			modifier = Modifier.padding(bottom = 4.dp)
		) {
			items(pins, key = { "${it.kind}:${it.id}" }) { pin ->
				PinnedCard(
					pin = pin,
					playlist = playlistsById[pin.id].takeIf { pin.kind == PinKind.Playlist },
					onPlaySong = onPlaySong,
					onUnpin = { onUnpin(pin) },
					modifier = Modifier.animateItem().width(124.dp)
				)
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedCard(
	pin: PinnedItem,
	playlist: DomainPlaylist?,
	onPlaySong: (DomainSong) -> Unit,
	onUnpin: () -> Unit,
	modifier: Modifier = Modifier
) {
	val backStack = LocalNavStack.current
	var menuShown by remember { mutableStateOf(false) }
	Box(modifier) {
		Column(
			Modifier.combinedClickable(
				onClick = {
					when (pin.kind) {
						PinKind.Album, PinKind.Playlist -> backStack.add(Screen.CollectionDetail(pin.id, "library"))
						PinKind.Artist -> backStack.add(Screen.ArtistDetail(pin.id))
						PinKind.Song -> pin.song?.let(onPlaySong)
					}
				},
				onLongClick = { menuShown = true }
			)
		) {
			CoverArt(
				coverArtId = playlist?.coverArtId ?: pin.coverArtId,
				localImageUri = playlist?.customCoverPath,
				shape = if (pin.kind == PinKind.Artist) CircleShape else null,
				modifier = Modifier.fillMaxWidth()
			)
			Text(
				playlist?.name ?: pin.title,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(top = 6.dp)
			)
			Text(
				when (pin.kind) {
					PinKind.Song -> stringResource(Res.string.info_pinned_song, pin.subtitle)
					PinKind.Album -> stringResource(Res.string.info_pinned_album, pin.subtitle)
					PinKind.Playlist -> stringResource(Res.string.info_pinned_playlist)
					PinKind.Artist -> stringResource(Res.string.info_pinned_artist)
				},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		DropdownMenu(expanded = menuShown, onDismissRequest = { menuShown = false }) {
			DropdownMenuItem(
				text = { Text(stringResource(Res.string.action_unpin)) },
				leadingIcon = { Icon(Icons.Outlined.PushPinOff, null) },
				onClick = {
					menuShown = false
					onUnpin()
				}
			)
		}
	}
}
