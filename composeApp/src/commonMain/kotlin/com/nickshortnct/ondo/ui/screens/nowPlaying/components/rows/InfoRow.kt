package com.nickshortnct.ondo.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_not_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.MarqueeText
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.controls.NowPlayingMoreButton
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.controls.NowPlayingStarButton
import com.nickshortnct.ondo.ui.util.InlineExplicitIconLarge
import com.nickshortnct.ondo.ui.util.appendArtists

@Composable
fun NowPlayingInfoRow(
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	Row(
		modifier = Modifier
			.padding(horizontal = 16.dp)
			.padding(bottom = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Column(Modifier.weight(1f)) {
			song?.let { song ->
				MarqueeText(
					text = buildAnnotatedString {
						append(song.title)
						if (song.explicitStatus == DomainExplicitStatus.Explicit) {
							append(" ")
							appendInlineContent("InlineExplicitIcon")
						}
					},
					inlineContent = InlineExplicitIconLarge,
					modifier = Modifier.clickable(onClick = dropUnlessResumed {
						song.albumId?.let {
							backStack.removeLastOrNull()

							val lastScreen = backStack.lastOrNull()

							val isSameAlbum = if (lastScreen is Screen.CollectionDetail) {
								lastScreen.collectionId == song.albumId
							} else {
								false
							}

							if (!isSameAlbum)
								backStack.add(
									Screen.CollectionDetail(
										playerState.currentCollection?.id
											?: return@dropUnlessResumed,
										""
									)
								)
						}
					}),
					style = MaterialTheme.typography.bodyLarge
						.copy(
							fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1
						),
				)
			}
			val notPlayingString = stringResource(Res.string.info_not_playing)
			MarqueeText(
				modifier = Modifier.clickable(
					song != null,
					onClick = dropUnlessResumed {
						song?.artistId?.let { id ->
							backStack.remove(Screen.NowPlaying)
							backStack.add(Screen.ArtistDetail(id))
						}
					}
				),
				style = MaterialTheme.typography.bodyMedium
					.copy(
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						fontSize = MaterialTheme.typography.bodyMedium.fontSize * 1.1
					),
				text = buildAnnotatedString {
					// Tracks from another Ondo Connect device carry only an artist name.
					if (song != null && song.artists.isEmpty()) {
						append(song.artistName)
					} else if (song != null) {
						appendArtists(
							artists = song.artists,
							onClick = {
								backStack.remove(Screen.NowPlaying)
								backStack.add(Screen.ArtistDetail(it))
							}
						)
					} else {
						append(notPlayingString)
					}
				}
			)
		}
		Row(
			horizontalArrangement = Arrangement.spacedBy(10.dp)
		) {
			NowPlayingStarButton(
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred
			)
			NowPlayingMoreButton(
				songRating = songRating,
				onSetSongRating = onSetSongRating
			)
		}
	}
}
