package com.nickshortnct.ondo.ui.screens.search

import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Duration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_add_to_queue
import ondo.composeapp.generated.resources.action_remove_from_history
import ondo.composeapp.generated.resources.action_search_history
import ondo.composeapp.generated.resources.info_explicit
import ondo.composeapp.generated.resources.info_no_search_results
import ondo.composeapp.generated.resources.info_not_available_offline
import ondo.composeapp.generated.resources.title_albums
import ondo.composeapp.generated.resources.title_all
import ondo.composeapp.generated.resources.title_artists
import ondo.composeapp.generated.resources.title_songs
import ondo.composeapp.generated.resources.title_playlists
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.ResultKind
import com.nickshortnct.ondo.domain.models.UnifiedSearchResult
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import com.nickshortnct.ondo.domain.models.settings.ListViewMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Close
import com.nickshortnct.ondo.icons.outlined.MoreVert
import com.nickshortnct.ondo.icons.outlined.Check
import com.nickshortnct.ondo.icons.outlined.Download
import com.nickshortnct.ondo.icons.outlined.History
import com.nickshortnct.ondo.icons.outlined.Lock
import com.nickshortnct.ondo.icons.outlined.NoSearchResults
import com.nickshortnct.ondo.icons.outlined.Offline
import com.nickshortnct.ondo.icons.outlined.Queue
import com.nickshortnct.ondo.ondo.DiscographyActionSheet
import com.nickshortnct.ondo.ondo.DiscographyGridItem
import com.nickshortnct.ondo.ondo.ExtraSearchModes
import com.nickshortnct.ondo.ondo.YouTubeSearchTab
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.previewImageUrl
import com.nickshortnct.ondo.ondo.str
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.common.ErrorBox
import com.nickshortnct.ondo.ui.components.common.MarqueeText
import com.nickshortnct.ondo.ui.components.dialogs.QueueDuplicateDialog
import com.nickshortnct.ondo.ui.components.layouts.ArtGrid
import com.nickshortnct.ondo.ui.components.layouts.ArtGridItem
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.artGridPlaceholder
import com.nickshortnct.ondo.ui.components.layouts.horizontalSection
import com.nickshortnct.ondo.ui.components.sheets.SongSheet
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.album.components.AlbumListScreenGridItem
import com.nickshortnct.ondo.ui.screens.album.viewmodels.AlbumListViewModel
import com.nickshortnct.ondo.ui.screens.artist.ArtistListScreenGridItem
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistListViewModel
import com.nickshortnct.ondo.ui.screens.search.components.searchResultShelf
import com.nickshortnct.ondo.ui.screens.search.components.DiscoverySongRow
import com.nickshortnct.ondo.ui.screens.search.components.SearchScreenChips
import com.nickshortnct.ondo.ui.screens.search.components.SearchScreenTopBar
import com.nickshortnct.ondo.ui.screens.search.viewmodels.SearchViewModel
import com.nickshortnct.ondo.ui.util.buildSongInfoString
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel

enum class SearchCategory(val res: StringResource?) {
	ALL(Res.string.title_all),
	SONGS(Res.string.title_songs),
	ALBUMS(Res.string.title_albums),
	ARTISTS(Res.string.title_artists),
	PLAYLISTS(Res.string.title_playlists),
	YOUTUBE(null)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
	nested: Boolean,
	initialQuery: String? = null
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<SearchViewModel>(
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val artistListViewModel = koinViewModel<ArtistListViewModel> {
		parametersOf(DomainArtistListType.AlphabeticalByName)
	}
	val artistListSelection by artistListViewModel.selectedArtist.collectAsState()
	val artistListSelectionAlbums by artistListViewModel.selectedArtistAlbums.collectAsState()
	val artistListStarred by artistListViewModel.starred.collectAsState()

	val albumListViewModel = koinViewModel<AlbumListViewModel> {
		parametersOf(DomainAlbumListType.AlphabeticalByName)
	}
	val albumListSelection by albumListViewModel.selectedAlbum.collectAsState()
	val albumListStarred by albumListViewModel.starred.collectAsState()
	val selectedAlbumRating by albumListViewModel.rating.collectAsStateWithLifecycle()

	val query = viewModel.searchQuery
	val state by viewModel.searchState.collectAsStateWithLifecycle()
	val searchFailures by viewModel.searchFailures.collectAsStateWithLifecycle()
	val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
	val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()

	val player = koinInject<MediaPlayerViewModel>()
	val downloads = koinInject<OndoDownloads>()
	val phoneDownloads = koinInject<DownloadManager>()
	val scope = rememberCoroutineScope()
	val phoneDownloadStatuses by remember(phoneDownloads) { phoneDownloads.allDownloads.map { rows -> rows.associate { it.songId to it.status } } }.collectAsStateWithLifecycle(initialValue = emptyMap())
	val backStack = LocalNavStack.current

	var selectedCategory by rememberSaveable { mutableStateOf(SearchCategory.ALL) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queuePlayNext by remember { mutableStateOf(false) }
	var showExtraModes by rememberSaveable { mutableStateOf(false) }
	// Tapping any not-on-decknas result (song/album row, or an album/artist
	// carousel tile) opens the same DiscographyActionSheet already used on
	// artist profiles, instead of the old inline row of TextButtons.
	var selectedDiscoveryItem by remember { mutableStateOf<MusicResult?>(null) }

	var initialQueryApplied by rememberSaveable(initialQuery) { mutableStateOf(false) }
	LaunchedEffect(initialQuery) {
		if (!initialQueryApplied && !initialQuery.isNullOrBlank()) {
			initialQueryApplied = true
			viewModel.submitSearch(initialQuery)
		}
	}
	DisposableEffect(viewModel) { onDispose { viewModel.setSearchActive(false) } }
	LaunchedEffect(selectedCategory, showExtraModes) {
		viewModel.setSearchActive(!showExtraModes && selectedCategory != SearchCategory.YOUTUBE)
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
			Column(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surface)
					.padding(
						TopAppBarDefaults.windowInsets.asPaddingValues()
					)
			) {
				SearchScreenTopBar(
					query = query,
					nested = nested,
					onSearch = viewModel::submitSearch
				)
				if (!showExtraModes) SearchScreenChips(
					selectedCategory = selectedCategory,
					onCategorySelect = { selectedCategory = it }
				)
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
					TextButton(onClick = { showExtraModes = !showExtraModes }) {
						Text(if (showExtraModes) "Back to search" else "More search options")
					}
					Row {
						if (query.text.isNotBlank() && !showExtraModes && selectedCategory != SearchCategory.YOUTUBE) TextButton(onClick = viewModel::retrySearch, enabled = !isSearching) { Text("Refresh") }
						if (com.nickshortnct.ondo.ondo.mediaAcquisitionEnabled) TextButton(onClick = { backStack.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads)) }) { Text("Downloads") }
					}
				}
				if (isSearching && !showExtraModes && selectedCategory != SearchCategory.YOUTUBE) {
					LinearProgressIndicator(Modifier.fillMaxWidth())
				}
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		if (showExtraModes) {
			ExtraSearchModes(Modifier.fillMaxSize().padding(contentPadding))
			return@Scaffold
		}
		if (selectedCategory == SearchCategory.YOUTUBE) {
			YouTubeSearchTab(
				query = query.text.toString(),
				isOnline = isOnline,
				modifier = Modifier.fillMaxSize().padding(contentPadding)
			)
			return@Scaffold
		}
		AnimatedContent(
			if (state is UiState.Loading && state.data != null) UiState.Success(state.data!!) else state,
			contentKey = { it::class },
			modifier = Modifier.fillMaxSize()
		) { uiState ->
			when (uiState) {
				is UiState.Loading -> ArtGrid(
					contentPadding = contentPadding,
					selectedViewMode = ListViewMode.List
				) {
					artGridPlaceholder(viewMode = ListViewMode.List)
				}
				is UiState.Error -> ErrorBox(uiState, padding = contentPadding, onRetry = viewModel::retrySearch)
				is UiState.Success -> {
					val results = uiState.data
					val showAll = selectedCategory == SearchCategory.ALL
					val albumResults = if (showAll || selectedCategory == SearchCategory.ALBUMS) results.filter { it.kind == ResultKind.ALBUM } else emptyList()
					val artistResults = if (showAll || selectedCategory == SearchCategory.ARTISTS) results.filter { it.kind == ResultKind.ARTIST } else emptyList()
					val songResults = if (showAll || selectedCategory == SearchCategory.SONGS) results.filter { it.kind == ResultKind.SONG } else emptyList()
					val playlistResults = if (showAll || selectedCategory == SearchCategory.PLAYLISTS) results.filter { it.kind == ResultKind.PLAYLIST } else emptyList()
					val visibleSongResults = if (showAll) songResults.take(6) else songResults
					val normalizedQuery = query.text.toString().trim().lowercase().filter { it.isLetterOrDigit() }
					fun matchStrength(value: String): Int {
						val candidate = value.lowercase().filter { it.isLetterOrDigit() }
						return when {
							candidate == normalizedQuery -> 3
							candidate.startsWith(normalizedQuery) -> 2
							candidate.contains(normalizedQuery) -> 1
							else -> 0
						}
					}
					val bestArtistMatch = artistResults.maxOfOrNull { matchStrength(it.title) } ?: 0
					val bestAlbumMatch = albumResults.maxOfOrNull { matchStrength(it.title) } ?: 0
					val bestSongMatch = songResults.maxOfOrNull { matchStrength(it.title) } ?: 0
					// Lead with the matching collection for artist/album searches.
					// An exact song-title match still wins for song searches.
					val artistFirst = showAll && normalizedQuery.isNotEmpty() && bestArtistMatch >= 2 && bestArtistMatch > bestSongMatch && bestArtistMatch >= bestAlbumMatch
					val albumFirst = showAll && normalizedQuery.isNotEmpty() && bestAlbumMatch >= 2 && bestAlbumMatch > bestSongMatch && bestAlbumMatch > bestArtistMatch
					val artistTile: @Composable (UnifiedSearchResult, Modifier) -> Unit = { result, tileModifier ->
						if (result.onDeck) {
							val artist = result.library as DomainArtist
							ArtistListScreenGridItem(
								modifier = tileModifier,
								tab = "search",
								artist = artist,
								selected = artist == artistListSelection,
								selectedArtistAlbums = artistListSelectionAlbums,
								starred = artistListStarred,
								onSelect = { artistListViewModel.selectArtist(artist) },
								onDeselect = { artistListViewModel.clearSelection() },
								onSetStarred = { artistListViewModel.starArtist(it) },
								onPlayNext = { artistListViewModel.playArtistAlbumsNext(player) },
								onAddToQueue = { artistListViewModel.addArtistAlbumsToQueue(player) }
							)
						} else {
							val hit = result.discovery!!
							Box(tileModifier) {
								DiscographyGridItem(
									item = hit,
									localCoverArtId = null,
									onDeck = false,
									onClick = { backStack.add(Screen.DiscoveryArtist(hit.artist.ifBlank { hit.title }, hit.raw.str("foreignArtistId"))) },
									tab = "search"
								)
							}
						}
					}
					val albumTile: @Composable (UnifiedSearchResult, Modifier) -> Unit = { result, tileModifier ->
					if (result.onDeck) {
						val album = result.library as DomainAlbum
						AlbumListScreenGridItem(
							modifier = tileModifier,
							tab = "search",
							album = album,
							selected = album == albumListSelection,
							starred = albumListStarred,
							onSelect = { albumListViewModel.selectAlbum(album) },
							onDeselect = { albumListViewModel.clearSelection() },
							onSetStarred = { albumListViewModel.starAlbum(it) },
							onSetShareId = { shareId = it },
							onPlayNext = { player.playNext(album as DomainSongCollection) },
							onAddToQueue = { player.addToQueue(album as DomainSongCollection) },
							rating = selectedAlbumRating,
							onSetRating = { albumListViewModel.setRating(it) }
						)
					} else {
						val hit = result.discovery!!
						Box(tileModifier) {
							DiscographyGridItem(
								item = hit,
								localCoverArtId = null,
								onDeck = false,
								onClick = { selectedDiscoveryItem = hit },
								tab = "search"
							)
						}
					}
					}

					LazyVerticalGrid(
						modifier = Modifier.fillMaxSize(),
						columns = GridCells.Fixed(2),
						contentPadding = contentPadding,
						state = viewModel.gridState,
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						if (query.text.isNotBlank()) {
							if (!isOnline) item(span = { GridItemSpan(maxLineSpan) }) {
								Text("Offline · searching your library. Songs saved on this device can still play.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
							if (!isSearching && albumResults.isEmpty() && artistResults.isEmpty() && songResults.isEmpty() && playlistResults.isEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
										Text(if (searchFailures.isNotEmpty()) "Search couldn't finish" else "No matches found", style = MaterialTheme.typography.titleLarge)
										Text(if (searchFailures.isNotEmpty()) "Check your connection and try again." else "Try a song title, artist, or a shorter search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
										if (selectedCategory != SearchCategory.ALL) TextButton(onClick = { selectedCategory = SearchCategory.ALL }) { Text("Search all music") }
									}
								}
							}
							if (showAll && artistFirst) {
								searchResultShelf(
									title = Res.string.title_artists,
									onSeeAll = { selectedCategory = SearchCategory.ARTISTS },
									results = artistResults,
								) { result ->
									artistTile(result, Modifier.animateItem(fadeInSpec = null).width(150.dp))
								}
							}
							if (showAll && albumFirst) searchResultShelf(
								title = Res.string.title_albums,
								onSeeAll = { selectedCategory = SearchCategory.ALBUMS },
								results = albumResults,
							) { result ->
								albumTile(result, Modifier.animateItem(fadeInSpec = null).width(150.dp))
							}
							if (searchFailures.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
										Text("Some results couldn't load. You can still use these matches.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
										TextButton(onClick = viewModel::retrySearch, enabled = !isSearching) { Text("Retry") }
									}
								}
							}
							if (songResults.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Text(
										stringResource(Res.string.title_songs),
										style = MaterialTheme.typography.headlineSmall,
										modifier = Modifier.padding(
											horizontal = 16.dp,
											vertical = 8.dp
										)
									)
								}
								items(
									visibleSongResults,
									key = { it.key },
									span = { GridItemSpan(maxLineSpan) }) { result ->
									if (result.onDeck) {
									val song = result.library as DomainSong
									val isDownloaded = (downloadedSongs?.containsKey(song.id) == true)

									val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
										&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
									val maybeUnavailable = !isOnline && !isDownloaded

									val dismissState = rememberSwipeToDismissBoxState()

									LaunchedEffect(dismissState.currentValue) {
										if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
											if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
												songToQueue = song
												queuePlayNext = false
											} else {
												player.addToQueueSingle(song)
											}
											dismissState.snapTo(SwipeToDismissBoxValue.Settled)
										}
									}

									SwipeToDismissBox(
										state = dismissState,
										enableDismissFromStartToEnd = false,
										enableDismissFromEndToStart = !maybeUnavailable && !isExplicit,
										backgroundContent = {
											val backgroundColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
													else -> Color.Transparent
												}
											)
											val iconColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
													else -> MaterialTheme.colorScheme.onSurfaceVariant
												}
											)

											Box(
												modifier = Modifier
													.fillMaxSize()
													.background(color = backgroundColor)
													.padding(horizontal = 20.dp),
												contentAlignment = Alignment.CenterEnd
											) {
												Icon(
													imageVector = Icons.Outlined.Queue,
													contentDescription = stringResource(Res.string.action_add_to_queue),
													tint = iconColor
												)
											}
										}
									) {
										ListItem(
											modifier = Modifier
												.background(MaterialTheme.colorScheme.surface),
											onClick = {
												if (!maybeUnavailable && !isExplicit) { viewModel.addToSearchHistory(query.text.toString()); player.playNow(song) }
											},
											onLongClick = { viewModel.selectSong(song) },
											content = { Text(song.title) },
											supportingContent = {
												MarqueeText(
													buildSongInfoString(
														song = song,
														onClickArtist = { backStack.add(Screen.ArtistDetail(it)) }
													)
												)
											},
											leadingContent = {
												CoverArt(
													coverArtId = song.coverArtId,
													modifier = Modifier.size(50.dp),
													shape = preferenceManager.coverArtShape.decreasedShape
												)
											},
											trailingContent = {
												Row(verticalAlignment = Alignment.CenterVertically) {
												if (isDownloaded) Icon(Icons.Outlined.Check, "Saved on this device", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
												IconButton(onClick = { viewModel.selectSong(song) }) { Icon(Icons.Outlined.MoreVert, "More options for ${song.title}") }
												if (isExplicit) {
													Icon(
														Icons.Outlined.Lock,
														stringResource(Res.string.info_explicit),
														modifier = Modifier.size(20.dp)
													)
												}
												if (maybeUnavailable) {
													Icon(
														Icons.Outlined.Offline,
														stringResource(Res.string.info_not_available_offline),
														modifier = Modifier.size(20.dp)
													)
												}
												}
											}
										)
										if (selectedSong == song) {
											SongSheet(
												onDismissRequest = { viewModel.clearSelectedSong() },
												song = song,
												onPlayNext = if (maybeUnavailable || isExplicit) null else ({
													if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
														songToQueue = song
														queuePlayNext = true
													} else {
														player.playNextSingle(song)
													}
												}),
												onAddToQueue = if (maybeUnavailable || isExplicit) null else ({
													if (player.uiState.value.queue.any { it.id == song.id } && !preferenceManager.shushQueueDuplicateDialog) {
														songToQueue = song
														queuePlayNext = false
													} else {
														player.addToQueueSingle(song)
													}
												}),
												downloadStatus = phoneDownloadStatuses[song.id]?.takeIf { isOnline || it == DownloadStatus.DOWNLOADED || it == DownloadStatus.DOWNLOADING } ?: if (isOnline) DownloadStatus.NOT_DOWNLOADED else null,
												onDownload = if (isOnline) ({ phoneDownloads.downloadSong(song) }) else null,
												onCancelDownload = { scope.launch { phoneDownloads.cancelDownload(song.id) } },
												onDeleteDownload = { scope.launch { phoneDownloads.deleteDownload(song.id) } },
												onTrackInfo = dropUnlessResumed {
													backStack.add(Screen.SongDetailScreen(song.id, song.coverArtId))
												},
												onViewAlbum = song.albumId?.let { albumId ->
													dropUnlessResumed {
														backStack.add(
															Screen.CollectionDetail(
																collectionId = albumId,
																tab = "search"
															)
														)
													}
												},
												starred = selectedSongIsStarred,
												onSetStarred = { viewModel.starSelectedSong(it) },
												rating = selectedSongRating,
												onSetRating = { viewModel.rateSelectedSong(it) }
											)
										}
									}
									} else {
										val hit = result.discovery!!
										DiscoverySongRow(hit, downloads, alternativeSources = result.alternativeSources, enabled = isOnline, onOpen = { selectedDiscoveryItem = hit }, onRequested = { viewModel.addToSearchHistory(query.text.toString()) })
									}
								}
							}
							if (showAll && songResults.size > visibleSongResults.size) item(span = { GridItemSpan(maxLineSpan) }) {
								TextButton(onClick = { selectedCategory = SearchCategory.SONGS }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("See all ${songResults.size} songs") }
							}
							if (showAll && !albumFirst) searchResultShelf(
								title = Res.string.title_albums,
								onSeeAll = { selectedCategory = SearchCategory.ALBUMS },
								results = albumResults,
							) { result ->
								albumTile(result, Modifier.animateItem(fadeInSpec = null).width(150.dp))
							}

							if (showAll && !artistFirst) searchResultShelf(
								title = Res.string.title_artists,
								onSeeAll = { selectedCategory = SearchCategory.ARTISTS },
								results = artistResults,
							) { result ->
								artistTile(result, Modifier.animateItem(fadeInSpec = null).width(150.dp))
							}
							if (selectedCategory == SearchCategory.ARTISTS) {
								items(artistResults, key = { it.key }) { result ->
									artistTile(result, Modifier.animateItem(fadeInSpec = null).padding(horizontal = 12.dp, vertical = 8.dp))
								}
							}
							if (selectedCategory == SearchCategory.ALBUMS) {
								items(albumResults, key = { it.key }) { result ->
									albumTile(result, Modifier.animateItem(fadeInSpec = null).padding(horizontal = 12.dp, vertical = 8.dp))
								}
							}
							if (playlistResults.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(Res.string.title_playlists), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)) }
								items(playlistResults, key = { it.key }) { result ->
									val playlist = result.library as DomainPlaylist
									ArtGridItem(modifier = Modifier.padding(horizontal = 16.dp), onClick = { viewModel.addToSearchHistory(query.text.toString()); backStack.add(Screen.CollectionDetail(collectionId = playlist.id, tab = "search")) }, coverArtId = playlist.coverArtId, localImageUri = playlist.customCoverPath, title = playlist.name, subtitle = "${playlist.songCount} songs", id = playlist.id, tab = "search")
								}
							}
						} else {
							item(span = { GridItemSpan(maxLineSpan) }) {
								Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
									Text("Find your next favorite", style = MaterialTheme.typography.headlineSmall)
									Text("Search songs, artists, albums, and playlists. Play music in your library, or add something new.", color = MaterialTheme.colorScheme.onSurfaceVariant)
								}
							}
							if (searchHistory.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = viewModel::clearSearchHistory) { Text("Clear recent searches") } } }
								item(span = { GridItemSpan(maxLineSpan) }) {
									Text(
										text = stringResource(Res.string.action_search_history),
										style = MaterialTheme.typography.titleMedium,
										color = MaterialTheme.colorScheme.primary,
										modifier = Modifier.padding(
											horizontal = 20.dp,
											vertical = 12.dp
										)
									)
								}
								items(
									searchHistory.size,
									span = { GridItemSpan(maxLineSpan) }) { index ->
									val historyItem = searchHistory[index]
									ListItem(
										modifier = Modifier.clickable {
											query.clearText()
											query.edit { insert(0, historyItem) }
											viewModel.submitSearch(historyItem)
										},
										headlineContent = { Text(historyItem) },
										leadingContent = {
											Icon(
												imageVector = Icons.Outlined.History,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onSurfaceVariant
											)
										},
										trailingContent = {
											IconButton(onClick = {
												viewModel.removeFromSearchHistory(historyItem)
											}) {
												Icon(
													imageVector = Icons.Outlined.Close,
													contentDescription = stringResource(Res.string.action_remove_from_history),
													tint = MaterialTheme.colorScheme.onSurfaceVariant
												)
											}
										}
									)
								}
							}
						}
					}
				}
			}
		}
	}

	ShareDialog(id = shareId, onIdClear = { shareId = null }, expiry = shareExpiry, onExpiryChange = { shareExpiry = it })

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = { songToQueue = null },
			onConfirm = {
				songToQueue?.let { if (queuePlayNext) player.playNextSingle(it) else player.addToQueueSingle(it) }
				songToQueue = null
			}
		)
	}

	selectedDiscoveryItem?.let { item ->
		DiscographyActionSheet(
			item = item,
			downloads = downloads,
			onDismissRequest = { selectedDiscoveryItem = null }
		)
	}
}
