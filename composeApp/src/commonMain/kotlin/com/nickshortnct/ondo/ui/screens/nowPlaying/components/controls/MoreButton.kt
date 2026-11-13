package com.nickshortnct.ondo.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.MoreHoriz
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.sheets.SongSheet
import com.nickshortnct.ondo.ui.components.sheets.SleepTimerSheet
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.theme.NavicTheme
import com.nickshortnct.ondo.ui.util.rememberColorSchemeFromCoverArt
import kotlin.time.Duration

@Composable
fun NowPlayingMoreButton(
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	var expanded by remember { mutableStateOf(false) }
	var sleepTimerSheetShown by rememberSaveable { mutableStateOf(false) }
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	val colorScheme = rememberColorSchemeFromCoverArt(song?.coverArtId)

	IconButton(
		onClick = {
			expanded = true
		},
		colors = IconButtonDefaults.filledTonalIconButtonColors(),
		modifier = Modifier.size(32.dp),
		enabled = song != null
	) {
		Icon(
			imageVector = Icons.Outlined.MoreHoriz,
			contentDescription = stringResource(Res.string.action_more)
		)
	}

	if (expanded && song != null) {
		NavicTheme(colorScheme) {
			SongSheet(
				onDismissRequest = { expanded = false },
				song = song,
				collection = playerState.currentCollection,
				onViewAlbum = dropUnlessResumed {
					playerState.currentCollection?.let { collection ->
						backStack.remove(Screen.NowPlaying)
						backStack.add(Screen.CollectionDetail(collection.id, ""))
					}
				},
				onViewArtist = dropUnlessResumed {
					backStack.remove(Screen.NowPlaying)
					backStack.add(Screen.ArtistDetail(song.artistId))
				},
				onShare = {
					shareId = song.id
				},
				onAddToPlaylist = {
					playlistDialogShown = true
				},
				onTrackInfo = dropUnlessResumed {
					expanded = false
					backStack.add(Screen.SongDetailSheet(songId = song.id, coverArtId = song.coverArtId))
				},
				rating = songRating,
				onSetRating = onSetSongRating,
				showSleepTimer = true,
				onSleepTimer = {
					expanded = false
					sleepTimerSheetShown = true
				},
				showPlaybackSpeed = true,
				onPlaybackSpeed = {
					expanded = false
					backStack.add(Screen.PlaybackSpeed)
				}
			)
		}
	}

	if (sleepTimerSheetShown) {
		NavicTheme(colorScheme) {
			SleepTimerSheet(
				onDismissRequest = { sleepTimerSheetShown = false }
			)
		}
	}

	if (playlistDialogShown && song != null) {
		NavicTheme(colorScheme) {
			PlaylistUpdateDialog(
				songs = persistentListOf(song),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}

	NavicTheme(colorScheme) {
		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)
	}
}
