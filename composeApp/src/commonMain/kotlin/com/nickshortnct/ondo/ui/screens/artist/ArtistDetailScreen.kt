package com.nickshortnct.ondo.ui.screens.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_see_all
import ondo.composeapp.generated.resources.count_albums
import ondo.composeapp.generated.resources.info_bulk_download_warning
import ondo.composeapp.generated.resources.notice_deleted_download
import ondo.composeapp.generated.resources.notice_download_started
import ondo.composeapp.generated.resources.option_sort_frequent
import ondo.composeapp.generated.resources.title_albums
import ondo.composeapp.generated.resources.title_bulk_download
import ondo.composeapp.generated.resources.title_similar_artists
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.ondo.DiscographyActionSheet
import com.nickshortnct.ondo.ondo.DiscographyCarouselItem
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.str
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.ErrorBox
import com.nickshortnct.ondo.ui.components.common.SongRow
import com.nickshortnct.ondo.ui.components.dialogs.BulkDownloadDialog
import com.nickshortnct.ondo.ui.components.layouts.ArtCarousel
import com.nickshortnct.ondo.ui.components.layouts.ArtCarouselItem
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.sheets.CollectionSheet
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.artist.components.ArtistActionButtons
import com.nickshortnct.ondo.ui.screens.artist.components.ArtistDetailScreenHeading
import com.nickshortnct.ondo.ui.screens.artist.components.ArtistDetailScreenTopBar
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistDetailViewModel
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.theme.NavicTheme
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.ui.util.rememberColorSchemeFromCoverArt
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
	artistId: String
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<ArtistDetailViewModel>(
		key = artistId,
		parameters = { parametersOf(artistId) }
	)
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val selection by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()

	val downloadManager = koinInject<DownloadManager>()
	val downloads = koinInject<OndoDownloads>()
	val density = LocalDensity.current
	val backStack = LocalNavStack.current
	val layoutDirection = LocalLayoutDirection.current
	val artistState by viewModel.artistState.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsState()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val downloadsById by viewModel.downloadsById.collectAsStateWithLifecycle()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)

	val snackBarManager = koinInject<SnackBarManager>()

	val scope = rememberCoroutineScope()

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val scrolled by remember {
		derivedStateOf {
			with(density) { viewModel.scrollState.value.toDp() } >= 200.dp
		}
	}

	val gridState = rememberLazyGridState()

	var showDownloadDialog by remember { mutableStateOf(false) }

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	var albumForPlaylist by remember { mutableStateOf<DomainAlbum?>(null) }

	// The item a discography card was tapped for -- opens DiscographyActionSheet.
	// Kept as a single piece of state (not per-card) since only one sheet can be
	// open at a time; the sheet owns its own busy/message/release-picking state,
	// so nothing here needs to touch the Albums & EPs / Singles lists themselves.
	var discographySheetItem by remember { mutableStateOf<MusicResult?>(null) }

	val colorScheme = if (preferenceManager.dynamicTheming) {
		rememberColorSchemeFromCoverArt(
			coverArtId = artistState.data?.artist?.coverArtId,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}

	NavicTheme(colorScheme) {
		Scaffold(
			topBar = {
				ArtistDetailScreenTopBar(
					scrolled = scrolled,
					artistState = artistState,
					starred = starred,
					onSetStarred = { viewModel.starArtist(it) },
				)
			},
			bottomBar = {
				val scrollManager = LocalBottomBarScrollManager.current
				val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
				if (!platformContext.isLandscape() && preferVisible) {
					RootBottomBar(scrolled = scrollManager.isTriggered)
				}
			}
		) { contentPadding ->
			AnimatedContent(
				targetState = artistState,
				transitionSpec = {
					(fadeIn(
						animationSpec = effectSpec
					) + scaleIn(
						initialScale = 0.8f,
						animationSpec = spatialSpec
					)) togetherWith (fadeOut(
						animationSpec = effectSpec
					) + scaleOut(
						animationSpec = spatialSpec
					))
				},
				modifier = Modifier.fillMaxSize()
			) { artistState ->
				when (artistState) {
					is UiState.Error -> Box(Modifier.fillMaxSize().padding(contentPadding)) {
						ErrorBox(artistState)
					}

					is UiState.Loading -> Box(Modifier.fillMaxSize()) {
						ContainedLoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
					}

					is UiState.Success -> {
						val state = artistState.data
						BulkDownloadDialog(
							title = stringResource(Res.string.title_bulk_download),
							message = stringResource(
								Res.string.info_bulk_download_warning,
								state.artist.name
							),
							showDialog = showDownloadDialog,
							onDismissRequest = { showDownloadDialog = false },
							onConfirm = {
								scope.launch {
									state.albums.forEach { album ->
										downloadManager.downloadCollection(album)
									}
									snackBarManager.notify(Res.string.notice_download_started)
								}
							}
						)
						Column(
							modifier = Modifier
								.fillMaxSize()
								.verticalScroll(viewModel.scrollState),
							verticalArrangement = Arrangement.spacedBy(12.dp),
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							ArtistDetailScreenHeading(
								artistName = state.artist.name,
								coverArtId = state.artist.coverArtId,
								subtitle = state.artist.biography,
								lastfm = state.artist.lastFmUrl,
								innerPadding = contentPadding,
								scrolled = scrolled
							)
							ArtistActionButtons(
								onPlay = { viewModel.playArtistAlbums(player) },
								onDownload = {
									showDownloadDialog = true
								},
								onCancelDownload = {
									state.albums.forEach { album ->
										downloadManager.cancelCollectionDownload(album)
									}
								},
								onDeleteDownload = {
									state.albums.forEach { album ->
										downloadManager.deleteDownloadedCollection(album)
									}
									snackBarManager.notify(Res.string.notice_deleted_download)
								},
								downloadStatus = downloadStatus,
								playEnabled = state.albums.isNotEmpty(),
								modifier = Modifier.padding(top = 8.dp)
							)
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(
										start = contentPadding.calculateStartPadding(
											layoutDirection
										)
									)
									.padding(
										end = contentPadding.calculateEndPadding(
											layoutDirection
										)
									),
								verticalArrangement = Arrangement.spacedBy(12.dp),
								horizontalAlignment = Alignment.CenterHorizontally
							) {
								state.topSongs.takeIf { state.topSongs.isNotEmpty() }
									?.let { songs ->
										Row(
											modifier = Modifier
												.heightIn(min = 32.dp)
												.padding(top = 8.dp)
												.padding(horizontal = 16.dp)
												.fillMaxWidth(),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.SpaceBetween
										) {
											Text(
												stringResource(Res.string.option_sort_frequent),
												style = MaterialTheme.typography.titleMediumEmphasized,
												fontWeight = FontWeight(600)
											)
											Text(
												stringResource(Res.string.action_see_all),
												style = MaterialTheme.typography.labelLarge,
												color = MaterialTheme.colorScheme.primary,
												modifier = Modifier.clickable(onClick = dropUnlessResumed {
													backStack.add(
														Screen.SongList(
															nested = true,
															listType = DomainSongListType.ByArtist(state.artist.id)
														)
													)
												})
											)
										}
										LazyHorizontalGrid(
											rows = GridCells.Fixed(3),
											state = gridState,
											flingBehavior = rememberSnapFlingBehavior(lazyGridState = gridState),
											modifier = Modifier.fillMaxWidth().height(250.dp)
										) {
											itemsIndexed(songs) { index, song ->
												val download =
													downloadsById[song.id]
												SongRow(
													modifier = Modifier.weight(1f),
													song = song,
													selected = selection == song,
													onClick = {
														if (playerState.currentSong?.id != song.id) {
															player.playNow(songs, index)
														} else {
															player.togglePlay()
														}
													},
													onLongClick = {
														viewModel.selectSong(song)
													},
													onDismissRequest = { viewModel.clearSelection() },
													starredState = if (selection == song) selectedSongIsStarred else song.starredAt != null,
													onAddStar = { viewModel.starSelectedSong() },
													onRemoveStar = { viewModel.unstarSelectedSong() },
													download = download,
													onDownload = { viewModel.downloadSong(song) },
													onCancelDownload = {
														viewModel.cancelDownload(
															song.id
														)
													},
													onDeleteDownload = {
														viewModel.deleteDownload(
															song.id
														)
													},
													onPlayNext = { player.playNextSingle(song) },
													onAddToQueue = { player.addToQueueSingle(song) },
													onShare = { shareId = song.id },
													isOnline = isOnline,
													rating = selectedSongRating,
													onSetRating = { viewModel.rateSelectedSong(it) }
												)
											}
										}
									}
								ArtCarousel(
									stringResource(Res.string.title_albums),
									state.albums.sortedByDescending { album -> album.playCount }
										.toImmutableList()
								) { album ->
									val albumDownloadStatus by remember(album.id, album.songs.map { it.id }) { downloadManager.getCollectionDownloadStatus(album.songs.map { it.id }) }
										.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
									ArtCarouselItem(
										coverArtId = album.coverArtId,
										title = album.name,
										contentDescription = null,
										onSelect = { viewModel.selectAlbum(album) },
										onClick = dropUnlessResumed {
											backStack.add(
												Screen.CollectionDetail(
													album.id,
													"artist"
												)
											)
										}
									)
									if (selectedAlbum == album) {
										CollectionSheet(
											onDismissRequest = { viewModel.clearAlbumSelection() },
											collection = album,
											starred = selectedAlbumIsStarred,
											onShare = { shareId = album.id },
											onPlayNext = { player.playNext(album) },
											onAddToQueue = { player.addToQueue(album) },
											onSetStarred = { viewModel.starAlbum(!selectedAlbumIsStarred) },
											onAddAllToPlaylist = { albumForPlaylist = album },
											downloadStatus = albumDownloadStatus,
											onDownloadAll = {
												scope.launch {
													downloadManager.downloadCollection(album)
													snackBarManager.notify(Res.string.notice_download_started)
												}
											},
											onCancelDownloadAll = {
												scope.launch {
													album.songs.forEach {
														downloadManager.cancelDownload(
															it.id
														)
													}
												}
											},
											onDeleteDownloadAll = {
												scope.launch {
													downloadManager.deleteDownloadedCollection(album)
													snackBarManager.notify(Res.string.notice_deleted_download)
												}
											},
											rating = selectedAlbumRating,
											onSetRating = { viewModel.rateSelectedAlbum(it) }
										)
									}
								}
								// Lidarr discography and requests: Android only (see mediaAcquisitionEnabled).
								if (com.nickshortnct.ondo.ondo.mediaAcquisitionEnabled) {
									val discographyDestination = Screen.DiscoveryArtist(
										name = state.artist.name,
										musicBrainzId = state.artist.musicBrainzId.orEmpty(),
										skipLocalRedirect = true
									)
									ArtCarousel(
										title = "Albums & EPs",
										items = state.discographyAlbums.toImmutableList(),
										destination = discographyDestination
									) { album ->
										val onDeckAlbum = state.onDeckAlbums[normalizeForMatch(album.title)]
										DiscographyCarouselItem(
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
											}
										)
									}
									ArtCarousel(
										title = "Singles",
										items = state.discographySingles.toImmutableList(),
										destination = discographyDestination
									) { single ->
										val onDeckAlbum = state.onDeckAlbums[normalizeForMatch(single.title)]
										DiscographyCarouselItem(
											item = single,
											localCoverArtId = onDeckAlbum?.coverArtId,
											onDeck = onDeckAlbum != null,
											onClick = {
												if (onDeckAlbum != null) {
													backStack.add(Screen.CollectionDetail(onDeckAlbum.id, "artist"))
												} else {
													discographySheetItem = single
												}
											}
										)
									}
									if (state.discographyError != null) {
										Text(
											"Couldn't load full discography: ${state.discographyError}",
											modifier = Modifier.padding(horizontal = 16.dp)
										)
									} else if (state.discographyChecked && state.discographyAlbums.isEmpty() && state.discographySingles.isEmpty()) {
										Text(
											"No additional releases found for this artist in Lidarr's metadata.",
											modifier = Modifier.padding(horizontal = 16.dp)
										)
									}
									discographySheetItem?.let { item ->
										DiscographyActionSheet(
											item = item,
											downloads = downloads,
											onDismissRequest = { discographySheetItem = null }
										)
									}
								}
								if (state.similarArtists.isEmpty()) return@Column
								ArtCarousel(
									stringResource(Res.string.title_similar_artists),
									state.similarArtists.toImmutableList()
								) { artist ->
									ArtCarouselItem(
										coverArtId = artist.coverArtId,
										title = artist.name,
										subtitle = pluralStringResource(
											Res.plurals.count_albums,
											artist.albumCount,
											artist.albumCount
										),
										contentDescription = null,
										onClick = dropUnlessResumed {
											backStack.add(Screen.ArtistDetail(artist.id))
										}
									)
								}
							}
							Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
						}
					}
				}
			}
		}

		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null; viewModel.clearSelection() },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)

		if (albumForPlaylist != null) {
			PlaylistUpdateDialog(
				songs = albumForPlaylist!!.songs.toPersistentList(),
				onDismissRequest = { albumForPlaylist = null }
			)
		}
	}
}

fun truncateText(text: String, limit: Int): String {
	return if (text.length > limit) {
		text.take(limit) + "..."
	} else {
		text
	}
}
