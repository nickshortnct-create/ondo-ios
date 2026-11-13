package com.nickshortnct.ondo.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.count_albums
import ondo.composeapp.generated.resources.title_artists
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.layouts.ArtGridItem
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.RootTopBar
import com.nickshortnct.ondo.ui.components.sheets.ArtistSheet
import com.nickshortnct.ondo.ui.components.snackbars.ErrorSnackBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.artist.components.ArtistListScreenContent
import com.nickshortnct.ondo.ui.screens.artist.components.ArtistListScreenSortButton
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistListViewModel
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistListScreen(
	nested: Boolean = false,
	listType: DomainArtistListType
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val selectedViewMode = preferenceManager.artistListViewMode

	val viewModel = koinViewModel<ArtistListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val artistsState by viewModel.artistsState.collectAsState()
	val selectedArtist by viewModel.selectedArtist.collectAsState()
	val selectedArtistAlbums by viewModel.selectedArtistAlbums.collectAsState()
	val selectedSorting by viewModel.listType.collectAsState()
	val selectedFilters by viewModel.selectedFilters.collectAsState()
	val starred by viewModel.starred.collectAsState()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val player = koinInject<MediaPlayerViewModel>()

	val actions: @Composable RowScope.() -> Unit = {
		ArtistListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setListType(it) },
			selectedViewMode = selectedViewMode,
			onSetViewMode = { preferenceManager.artistListViewMode = it },
			selectedFilters = selectedFilters,
			onToggleFilter = { viewModel.toggleFilter(it) }
		)
	}

	val rootViewModel = koinViewModel<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				viewModel.gridState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					{ Text(stringResource(Res.string.title_artists)) },
					scrollBehavior,
					actions
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_artists)) }, actions)
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
			finished = artistsState !is UiState.Loading,
			onRefresh = { viewModel.refreshArtists(true) },
			key = artistsState
		) {
			ArtistListScreenContent(
				state = artistsState,
				starred = starred,
				selectedSorting = selectedSorting,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedViewMode = selectedViewMode,
				gridState = viewModel.gridState,
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding,
				nested = nested,
				onUpdateSelection = { viewModel.selectArtist(it) },
				onClearSelection = { viewModel.clearSelection() },
				onSetStarred = { viewModel.starArtist(it) },
				onPlayNext = { viewModel.playArtistAlbumsNext(player) },
				onAddToQueue = { viewModel.addArtistAlbumsToQueue(player) }
			)
		}
	}

	ErrorSnackBar(
		error = (artistsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}

@Composable
fun ArtistListScreenGridItem(
	modifier: Modifier = Modifier,
	tab: String,
	artist: DomainArtist,
	selected: Boolean,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	starred: Boolean,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit
) {
	val backStack = LocalNavStack.current

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				backStack.add(Screen.ArtistDetail(artist.id))
			},
			onLongClick = onSelect,
			coverArtId = artist.coverArtId,
			localImageUri = artist.artistImageUrl,
			title = artist.name,
			subtitle = pluralStringResource(
				Res.plurals.count_albums,
				artist.albumCount,
				artist.albumCount
			),
			id = artist.id,
			tab = tab
		)
		if (selected) {
			ArtistSheet(
				onDismissRequest = onDeselect,
				artist = artist,
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onAddAllToPlaylist = { playlistDialogShown = true },
				starred = starred,
				onSetStarred = { onSetStarred(!starred) }
			)
		}
		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = selectedArtistAlbums?.flatMap { it.songs }.orEmpty().toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}
