package com.nickshortnct.ondo.ui.screens.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.dialogs.QueueDuplicateDialog
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.RootTopBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.screens.song.components.SongListScreenSortButton
import com.nickshortnct.ondo.ui.screens.song.components.songListScreenContent
import com.nickshortnct.ondo.ui.screens.song.viewmodels.SongListViewModel
import com.nickshortnct.ondo.ui.util.withoutTop
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListScreen(
	nested: Boolean,
	listType: DomainSongListType
) {
	val platformContext = LocalPlatformContext.current
	val viewModel = koinViewModel<SongListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val songsState by viewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queuePlayNext by remember { mutableStateOf(false) }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val actions: @Composable RowScope.() -> Unit = {
		SongListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) },
			selectedFilters = selectedFilters,
			onToggleFilter = { viewModel.toggleFilter(it) }
		)
	}

	val rootViewModel = koinViewModel<RootViewModel>()
	val listState = rememberLazyListState()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				listState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					actions = actions
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = songsState !is UiState.Loading,
			onRefresh = { viewModel.refreshSongs(true) },
			key = songsState
		) {
			LazyColumn(
				modifier = if (!nested)
					Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
				else Modifier.fillMaxSize(),
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if ((songsState as? UiState.Success)?.data?.isEmpty() == true)
					Arrangement.Center
				else Arrangement.spacedBy(12.dp),
				state = listState
			) {
				songListScreenContent(
					state = songsState,
					selectedSongIsStarred = starred,
					selectedSongRating = selectedSongRating,
					selectedSong = selectedSong,
					onUpdateSelection = { viewModel.selectSong(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetShareId = { newShareId ->
						shareId = newShareId
					},
					onSetStarred = { viewModel.starSong(it) },
					onPlayNext = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
							queuePlayNext = true
						} else {
							player.playNextSingle(song)
						}
					},
					onAddToQueue = { song ->
						if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
							songToQueue = song
							queuePlayNext = false
						} else {
							player.addToQueueSingle(song)
						}
					},
					onPlaySong = { song ->
						player.playNow(song)
					},
					onSetRating = { viewModel.rateSelectedSong(it) },
					onDownload = { viewModel.downloadSong(it) },
					allDownloads = allDownloads,
					onCancelDownload = { viewModel.cancelDownload(it.id) },
					onDeleteDownload = { viewModel.deleteDownload(it.id) }
				)
			}
		}
	}

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
