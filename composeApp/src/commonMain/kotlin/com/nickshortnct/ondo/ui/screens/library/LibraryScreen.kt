package com.nickshortnct.ondo.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_library
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.domain.manager.LoginManager
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ondo.CurationManager
import com.nickshortnct.ondo.ondo.mixTag
import com.nickshortnct.ondo.ondo.PinKind
import com.nickshortnct.ondo.ondo.PinManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.nickshortnct.ondo.ui.components.dialogs.DeletionDialog
import com.nickshortnct.ondo.ui.components.dialogs.DeletionEndpoint
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.RootTopBar
import com.nickshortnct.ondo.ui.components.snackbars.ErrorSnackBar
import com.nickshortnct.ondo.ui.core.LoginUiState
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.screens.album.viewmodels.AlbumListViewModel
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistListViewModel
import com.nickshortnct.ondo.ui.screens.genre.viewmodels.GenreListViewModel
import com.nickshortnct.ondo.ui.screens.library.components.LibraryScreenContent
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistCreateDialog
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistListViewModel
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "libraryAlbums",
		parameters = { parametersOf(DomainAlbumListType.Recent) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val playlistsViewModel = koinViewModel<PlaylistListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val allPlaylistsState by playlistsViewModel.playlistsState.collectAsStateWithLifecycle()
	// Weekly mixes get their own rows, so they're left out of the regular Playlists row.
	val mixes = remember(allPlaylistsState.data) {
		allPlaylistsState.data.orEmpty()
			.mapNotNull { playlist -> playlist.mixTag?.let { playlist to it } }
			.sortedBy { it.second.slot.substringAfterLast('-').toIntOrNull() ?: 0 }
	}
	val playlistsState = remember(allPlaylistsState) {
		val regular = allPlaylistsState.data?.filter { it.mixTag == null }?.toImmutableList()
		when (val state = allPlaylistsState) {
			is UiState.Loading -> UiState.Loading(regular)
			is UiState.Success -> UiState.Success(regular ?: persistentListOf())
			is UiState.Error -> UiState.Error(state.error, regular)
		}
	}
	val curationManager = koinInject<CurationManager>()
	val pinManager = koinInject<PinManager>()
	val allPins by pinManager.pins.collectAsStateWithLifecycle()
	val playlistsById = remember(allPlaylistsState.data) { allPlaylistsState.data.orEmpty().associateBy { it.id } }
	// A pinned playlist that was deleted on the server drops off once the playlist list has loaded.
	val pins = remember(allPins, allPlaylistsState) {
		if (allPlaylistsState !is UiState.Success) allPins
		else allPins.filter { it.kind != PinKind.Playlist || it.id in playlistsById }
	}
	val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "libraryArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	val genresViewModel = koinViewModel<GenreListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val genresState by genresViewModel.genresState.collectAsStateWithLifecycle()

	val loginManager = koinInject<LoginManager>()
	val loginState by loginManager.loginState.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var playlistCreateDialogShown by rememberSaveable { mutableStateOf(false) }

	val player = koinInject<MediaPlayerViewModel>()

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	LaunchedEffect(loginState is LoginUiState.Success) {
		pinManager.reload()
		albumsViewModel.refreshAlbums(false)
		playlistsViewModel.refreshPlaylists(false)
		artistsViewModel.refreshArtists(false)
		genresViewModel.refreshGenres(false)
	}

	val gridState = rememberLazyGridState()
	val rootViewModel = koinViewModel<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				gridState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(Res.string.title_library)) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = albumsState !is UiState.Loading &&
				playlistsState !is UiState.Loading &&
				artistsState !is UiState.Loading &&
				genresState !is UiState.Loading,
			onRefresh = {
				curationManager.refreshAsync(true)
				albumsViewModel.refreshAlbums(true)
				playlistsViewModel.refreshPlaylists(true)
				artistsViewModel.refreshArtists(true)
				genresViewModel.refreshGenres(true)
			},
			key = listOf(albumsState, playlistsState, artistsState, genresState)
		) {
			LibraryScreenContent(
				state = gridState,
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },

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

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = {
					if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(
						player
					)
				},
				onAddArtistToQueue = {
					if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(
						player
					)
				},

				playlistsState = playlistsState,
				selectedPlaylist = selectedPlaylist,
				onSelectPlaylist = { playlistsViewModel.selectPlaylist(it) },
				onClearPlaylistSelection = { playlistsViewModel.clearSelection() },
				onDeletePlaylist = { playlistDeletionId = it },
				onPlayPlaylistNext = {
					if (selectedPlaylist != null) player.playNext(
						selectedPlaylist as DomainSongCollection
					)
				},
				onAddPlaylistToQueue = {
					if (selectedPlaylist != null) player.addToQueue(
						selectedPlaylist as DomainSongCollection
					)
				},

				genresState = genresState,
				mixes = mixes,
				pins = pins,
				playlistsById = playlistsById,
				onPlayPinnedSong = { player.playNow(it) },
				onUnpin = { pinManager.unpin(it.kind, it.id) }
			)
		}
	}

	val flattenedErrors = listOf(
		(albumsState as? UiState.Error)?.error,
		(playlistsState as? UiState.Error)?.error,
		(artistsState as? UiState.Error)?.error,
		(genresState as? UiState.Error)?.error
	).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackBar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			albumsViewModel.clearError()
			playlistsViewModel.clearError()
			artistsViewModel.clearError()
			genresViewModel.clearError()
		}
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = playlistDeletionId,
		onIdClear = { playlistDeletionId = null },
		onRefresh = { playlistsViewModel.refreshPlaylists(false) }
	)

	if (playlistCreateDialogShown) {
		PlaylistCreateDialog(
			onDismissRequest = { playlistCreateDialogShown = false },
			onRefresh = { playlistsViewModel.refreshPlaylists(true) }
		)
	}
}
