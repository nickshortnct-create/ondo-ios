package com.nickshortnct.ondo.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.dialogs.QueueDuplicateDialog
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.snackbars.ErrorSnackBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.screens.album.viewmodels.AlbumListViewModel
import com.nickshortnct.ondo.ui.screens.genre.components.GenreDetailScreenContent
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.screens.song.viewmodels.SongListViewModel
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalPlatformContext
import kotlin.time.Duration

@Composable
fun GenreDetailScreen(
	genreName: String
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()

	val songsViewModel = koinViewModel<SongListViewModel>(
		key = "genre_detail_songs_$genreName",
		parameters = { parametersOf(DomainSongListType.ByGenre(genreName)) }
	)
	val songsState by songsViewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by songsViewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by songsViewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by songsViewModel.selectedSongRating.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "genre_detail_albums_$genreName",
		parameters = { parametersOf(DomainAlbumListType.ByGenre(genreName)) }
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val allDownloads by songsViewModel.allDownloads.collectAsStateWithLifecycle()
	val isOnline by songsViewModel.isOnline.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queuePlayNext by remember { mutableStateOf(false) }

	Scaffold(
		topBar = { NestedTopBar({ Text(genreName) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!platformContext.isLandscape() && preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = albumsState !is UiState.Loading &&
				songsState !is UiState.Loading,
			onRefresh = {
				albumsViewModel.refreshAlbums(true)
				songsViewModel.refreshSongs(true)
			},
			key = listOf(albumsState, songsState)
		) {
			GenreDetailScreenContent(
				genreName = genreName,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },
				isOnline = isOnline,

				songsState = songsState,
				selectedSong = selectedSong,
				selectedSongIsStarred = selectedSongIsStarred,
				selectedSongRating = selectedSongRating,
				allDownloads = allDownloads,
				onSelectSong = { songsViewModel.selectSong(it) },
				onClearSongSelection = { songsViewModel.clearSelection() },
				onAddSongStar = { songsViewModel.starSong(true) },
				onRemoveSongStar = { songsViewModel.starSong(false) },
				onPlaySongNext = { song ->
					if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
						songToQueue = song
						queuePlayNext = true
					} else {
						player.playNextSingle(song)
					}
				},
				onAddSongToQueue = { song ->
					if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
						songToQueue = song
						queuePlayNext = false
					} else {
						player.addToQueueSingle(song)
					}
				},
				onPlaySong = { index ->
					player.playNow(songsState.data.orEmpty(), index)
				},
				onSetSongRating = { songsViewModel.rateSelectedSong(it) },
				onDownloadSong = { songsViewModel.downloadSong(it) },
				onCancelDownloadSong = { song ->
					songsViewModel.cancelDownload(song.id)
				},
				onDeleteDownloadSong = { song ->
					songsViewModel.deleteDownload(song.id)
				},

				albumsState = albumsState,
				selectedAlbum = selectedAlbum,
				selectedAlbumIsStarred = selectedAlbumIsStarred,
				selectedAlbumRating = selectedAlbumRating,
				onSelectAlbum = { albumsViewModel.selectAlbum(it) },
				onClearAlbumSelection = { albumsViewModel.clearSelection() },
				onStarSelectedAlbum = { albumsViewModel.starAlbum(it) },
				onPlayAlbumNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection) },
				onAddAlbumToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection) },
				onRateSelectedAlbum = { albumsViewModel.setRating(it) },
			)
		}
	}

	val flattenedErrors = listOf(
		(albumsState as? UiState.Error)?.error,
		(songsState as? UiState.Error)?.error
	).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackBar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			albumsViewModel.clearError()
			songsViewModel.clearError()
		}
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = { songToQueue = null },
			onConfirm = {
				songToQueue?.let { if (queuePlayNext) player.playNextSingle(it) else player.addToQueueSingle(it) }
				songToQueue = null
			}
		)
	}
}
