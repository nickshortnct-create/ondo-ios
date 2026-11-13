package com.nickshortnct.ondo.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.dynamiccolor.ColorSpec
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_no_matching_songs
import ondo.composeapp.generated.resources.info_no_songs
import ondo.composeapp.generated.resources.title_disc_number
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Album
import com.nickshortnct.ondo.icons.outlined.Note
import com.nickshortnct.ondo.icons.outlined.Search
import com.nickshortnct.ondo.ondo.CurationManager
import com.nickshortnct.ondo.ondo.SongCategoryIndex
import com.nickshortnct.ondo.ondo.buildFilterChips
import com.nickshortnct.ondo.ondo.filterPlaylistSongs
import com.nickshortnct.ondo.ondo.mixTag
import com.nickshortnct.ondo.ondo.mixWeekLabel
import com.nickshortnct.ondo.ui.screens.collection.components.PlaylistFilterBar
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.snackbars.ErrorSnackBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenFooterRow
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenHeadingRow
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenHeadingRowButtons
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenSongRow
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenSongRowDropdown
import com.nickshortnct.ondo.ui.screens.collection.components.CollectionDetailScreenTopBar
import com.nickshortnct.ondo.ui.screens.collection.components.PlaylistTrackSort
import com.nickshortnct.ondo.ui.screens.collection.components.collectionDetailScreenMoreByArtistRow
import com.nickshortnct.ondo.ui.screens.collection.components.sortPlaylistTracks
import com.nickshortnct.ondo.ui.screens.collection.viewmodels.CollectionDetailViewModel
import com.nickshortnct.ondo.ui.screens.share.dialogs.ShareDialog
import com.nickshortnct.ondo.ui.theme.NavicTheme
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.ui.util.rememberColorSchemeFromCoverArt
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.ui.util.rememberPlaylistCoverPicker
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistEditDialog
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.isEditableBy
import com.nickshortnct.ondo.ui.util.withoutTop
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailScreen(
	collectionId: String,
	tab: String
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<CollectionDetailViewModel>(
		key = collectionId,
		parameters = { parametersOf(collectionId) }
	)

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val collectionState by viewModel.collectionState.collectAsState()
	val collection = collectionState.data
	var playlistTrackSortName by rememberSaveable(collectionId) {
		mutableStateOf(preferenceManager.getPlaylistTrackSortName(collectionId))
	}
	var playlistTrackSortDescending by rememberSaveable(collectionId) {
		mutableStateOf(preferenceManager.getPlaylistTrackSortDescending(collectionId))
	}
	val playlistTrackSort = PlaylistTrackSort.entries.firstOrNull { it.name == playlistTrackSortName }
		?: PlaylistTrackSort.DateAdded
	val sortedPlaylistSongs = remember(collection, playlistTrackSort, playlistTrackSortDescending) {
		(collection as? DomainPlaylist)?.songs?.let {
			sortPlaylistTracks(it, playlistTrackSort, playlistTrackSortDescending)
		}
	}
	// Search and mood/genre chips (playlists only). Filtering keeps each song's position in the sorted
	// list so "remove from playlist" still targets the right copy of a repeated song.
	val curationManager = koinInject<CurationManager>()
	val curation by curationManager.curation.collectAsStateWithLifecycle()
	val categoryIndex = remember(curation) { SongCategoryIndex(curation) }
	var searchActive by rememberSaveable(collectionId) { mutableStateOf(false) }
	var searchQuery by rememberSaveable(collectionId) { mutableStateOf("") }
	var selectedChip by rememberSaveable(collectionId) { mutableStateOf<String?>(null) }
	val filterChips = remember(sortedPlaylistSongs, categoryIndex) {
		sortedPlaylistSongs?.let { buildFilterChips(it, categoryIndex) }.orEmpty()
	}
	val activeChip = selectedChip?.takeIf { key -> filterChips.any { it.key == key } }
	val filteredPlaylistEntries = remember(sortedPlaylistSongs, searchQuery, activeChip, categoryIndex) {
		sortedPlaylistSongs?.let { filterPlaylistSongs(it, searchQuery, activeChip, categoryIndex) }
	}
	val collectionInDisplayedOrder = remember(collection, filteredPlaylistEntries) {
		if (collection is DomainPlaylist && filteredPlaylistEntries != null) {
			collection.copy(songs = filteredPlaylistEntries.map { it.value })
		} else collection
	}
	val mixTag = (collection as? DomainPlaylist)?.mixTag
	val selection by viewModel.selectedSong.collectAsState()
	val selectedAlbum by viewModel.selectedAlbum.collectAsState()
	val isOnline by viewModel.isOnline.collectAsState()
	val starred by viewModel.starred.collectAsState()

	var shareId by remember { mutableStateOf<String?>(null) }
	var editDialogShown by rememberSaveable { mutableStateOf(false) }
	val sessionManager = koinInject<SessionManager>()
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	val albumInfoState by viewModel.albumInfoState.collectAsState()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()
	val otherAlbums by viewModel.otherAlbums.collectAsState()
	val downloadsById by viewModel.downloadsById.collectAsState()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)

	val rating by viewModel.rating.collectAsStateWithLifecycle()

	val titleAlpha by remember {
		derivedStateOf {
			if (viewModel.listState.firstVisibleItemIndex >= 1) return@derivedStateOf 1f
			val height =
				viewModel.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat()
					?: 0f
			if (height > 0f) {
				val threshold = height * 0.4f
				((viewModel.listState.firstVisibleItemScrollOffset.toFloat() - threshold) / (height - threshold)).coerceIn(
					0f,
					1f
				)
			} else {
				0f
			}
		}
	}
	val colorScheme = if (preferenceManager.dynamicTheming) {
		rememberColorSchemeFromCoverArt(
			coverArtId = collection?.coverArtId,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}

	val pickPlaylistCover = rememberPlaylistCoverPicker { imageBytes ->
		imageBytes?.let { viewModel.setPlaylistCover(it) }
	}

	NavicTheme(colorScheme) {
		Scaffold(
			topBar = {
				CollectionDetailScreenTopBar(
					albumInfoState = albumInfoState,
					collection = collection,
					titleAlpha = titleAlpha,
					onSetShareId = { shareId = it },
					onDownloadAll = { viewModel.downloadAll() },
					onCancelDownloadAll = { viewModel.cancelDownloadAll() },
					onPlayNext = { if (collection != null) player.playNext(collection) },
					onAddToQueue = { if (collection != null) player.addToQueue(collection) },
					downloadStatus = downloadStatus,
					rating = if (collection !is DomainPlaylist) rating else null,
					onSetRating = if (collection !is DomainPlaylist) {
						{ viewModel.rateAlbum(it) }
					} else null,
					starred = if (collection !is DomainPlaylist) starred else null,
					onSetStarred = if (collection !is DomainPlaylist) {
						{ viewModel.starAlbum(it) }
					} else null,
					refreshCollection = { viewModel.refreshCollection(true) },
					selectedPlaylistTrackSort = playlistTrackSort.takeIf { collection is DomainPlaylist },
					playlistTrackSortDescending = playlistTrackSortDescending,
					onSetPlaylistTrackSort = {
						playlistTrackSortName = it.name
						preferenceManager.setPlaylistTrackSortName(collectionId, it.name)
					},
					onSetPlaylistTrackSortDescending = {
						playlistTrackSortDescending = it
						preferenceManager.setPlaylistTrackSortDescending(collectionId, it)
					},
					onChangePlaylistCover = if (collection is DomainPlaylist) pickPlaylistCover else null,
					onRemovePlaylistCover = if (collection is DomainPlaylist && !collection.customCoverPath.isNullOrBlank()) {
						{ viewModel.clearPlaylistCover() }
					} else null,
					onEditPlaylist = if (collection is DomainPlaylist && collection.isEditableBy(sessionManager.username)) {
						{ editDialogShown = true }
					} else null,
					onToggleSearch = if (collection is DomainPlaylist) {
						{
							if (searchActive) searchQuery = ""
							searchActive = !searchActive
						}
					} else null,
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
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = contentPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = collectionState !is UiState.Loading,
				onRefresh = { viewModel.refreshCollection(true) },
				key = collectionState
			) {
				LazyColumn(
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surface)
						.fillMaxSize(),
					horizontalAlignment = Alignment.CenterHorizontally,
					contentPadding = contentPadding.withoutTop(),
					state = viewModel.listState
				) {
					if (collection == null) return@LazyColumn

					item {
						CollectionDetailScreenHeadingRow(
							collection = collection,
							tab = tab,
							titleAlpha = 1f - titleAlpha
						)
					}

					item {
						CollectionDetailScreenHeadingRowButtons(
							collection = collectionInDisplayedOrder ?: collection
						)
					}

					if (collection is DomainPlaylist) {
						item(key = "playlist-filter-bar") {
							PlaylistFilterBar(
								searchActive = searchActive,
								query = searchQuery,
								onQueryChange = { searchQuery = it },
								onCloseSearch = {
									searchQuery = ""
									searchActive = false
								},
								chips = filterChips,
								selectedChip = activeChip,
								onSelectChip = { selectedChip = it },
								shownCount = filteredPlaylistEntries?.size ?: collection.songs.size,
								totalCount = collection.songs.size,
								mixTag = mixTag,
								onSaveMixCopy = if (mixTag != null) {
									{ viewModel.saveMixCopy(mixWeekLabel(mixTag.week)) }
								} else null
							)
						}
					}

					if (collection is DomainAlbum) {
						collection.copy(
							songs = collection.songs.sortedWith(
								compareBy(
									{ it.discNumber },
									{ it.trackNumber }
								))
						).let { album ->
							album.songs.groupBy { it.discNumber }.forEach { group ->
								val multipleDiscs = album.songs.groupBy { it.discNumber }.size > 1
								if (group.key != null && multipleDiscs) {
									item {
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.padding(horizontal = 16.dp)
												.padding(
													top = if (group.key == 1) 0.dp else 12.dp,
													bottom = 4.dp
												)
												.heightIn(min = 32.dp),
											verticalAlignment = Alignment.CenterVertically
										) {
											Icon(
												imageVector = Icons.Outlined.Album,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onSurfaceVariant,
												modifier = Modifier.size(20.dp)
											)

											Spacer(modifier = Modifier.width(8.dp))

											Text(
												text = stringResource(
													Res.string.title_disc_number,
													group.key as Int
												),
												style = MaterialTheme.typography.titleMediumEmphasized,
												fontWeight = FontWeight(600),
												color = MaterialTheme.colorScheme.onSurfaceVariant
											)
										}
									}
								}
								itemsIndexed(group.value) { index, song ->
									val download = downloadsById[song.id]
									Box {
										CollectionDetailScreenSongRow(
											song = song,
											index = index,
											count = group.value.count(),
											isPlaylist = false,
											onClick = {
												if (playerState.currentSong?.id != song.id) {
													player.playNow(
														album,
														album.songs.indexOfFirst { it.id == song.id })
												} else {
													player.togglePlay()
												}
											},
											onLongClick = {
												viewModel.selectSong(song)
											},
											onPlayNext = {
												player.playNextSingle(song)
											},
											onAddToQueue = {
												player.addToQueueSingle(song)
											},
											isStarred = if (selection == song) selectedSongIsStarred else song.starredAt != null,
											download = download,
											isOffline = !isOnline
										)
										CollectionDetailScreenSongRowDropdown(
											expanded = selection == song,
											onDismissRequest = { viewModel.clearSelection() },
											onRemoveStar = { viewModel.unstarSelectedSong() },
											onAddStar = { viewModel.starSelectedSong() },
											onShare = { shareId = song.id },
											collection = collection,
											song = song,
											onRemoveFromPlaylist = { viewModel.removeFromPlaylist() },
											starred = selectedSongIsStarred,
											downloadStatus = download?.status,
											onDownload = { viewModel.downloadSong(song) },
											onCancelDownload = { viewModel.cancelDownload(song.id) },
											onDeleteDownload = { viewModel.deleteDownload(song.id) },
											onPlayNext = { player.playNextSingle(song) },
											onAddToQueue = { player.addToQueueSingle(song) },
											rating = selectedSongRating,
											onSetRating = { viewModel.rateSelectedSong(it) }
										)
									}
								}
							}
						}
					} else {
						val playlistSongs = sortedPlaylistSongs ?: collection.songs
						val shownEntries = filteredPlaylistEntries ?: playlistSongs.withIndex().toList()
						itemsIndexed(shownEntries) { index, (positionInPlaylist, song) ->
							val download = downloadsById[song.id]
							Box {
								CollectionDetailScreenSongRow(
									song = song,
									index = index,
									count = shownEntries.size,
									isPlaylist = true,
									onClick = {
										if (playerState.currentSong?.id != song.id) {
											player.playNow(collectionInDisplayedOrder ?: collection, index)
										} else {
											player.togglePlay()
										}
									},
									onLongClick = {
										viewModel.selectSong(song)
									},
									onPlayNext = {
										player.playNextSingle(song)
									},
									onAddToQueue = {
										player.addToQueueSingle(song)
									},
									isStarred = if (selection == song) selectedSongIsStarred else song.starredAt != null,
									download = download,
									isOffline = !isOnline
								)
								CollectionDetailScreenSongRowDropdown(
									expanded = selection == song,
									onDismissRequest = { viewModel.clearSelection() },
									onRemoveStar = { viewModel.unstarSelectedSong() },
									onAddStar = { viewModel.starSelectedSong() },
									onShare = { shareId = song.id },
									collection = collection,
									song = song,
									onRemoveFromPlaylist = {
										// Which copy of a repeated song this row is, in the order shown.
										viewModel.removeFromPlaylist(playlistSongs.take(positionInPlaylist).count { it.id == song.id })
									},
									starred = selectedSongIsStarred,
									downloadStatus = download?.status,
									onDownload = { viewModel.downloadSong(song) },
									onCancelDownload = { viewModel.cancelDownload(song.id) },
									onDeleteDownload = { viewModel.deleteDownload(song.id) },
									onPlayNext = { player.playNextSingle(song) },
									onAddToQueue = { player.addToQueueSingle(song) },
									rating = selectedSongRating,
									onSetRating = { viewModel.rateSelectedSong(it) }
								)
							}
						}
					}

					if (collection.songs.isEmpty()) {
						item {
							ContentUnavailable(
								icon = Icons.Outlined.Note,
								label = stringResource(Res.string.info_no_songs)
							)
						}
					} else if (filteredPlaylistEntries?.isEmpty() == true) {
						item {
							ContentUnavailable(
								icon = Icons.Outlined.Search,
								label = stringResource(Res.string.info_no_matching_songs)
							)
						}
					}

					item { CollectionDetailScreenFooterRow(collection) }

					(collection as? DomainAlbum)?.artistName?.let { artistName ->
						collectionDetailScreenMoreByArtistRow(
							artistName = artistName,
							artistAlbums = otherAlbums,
							selectedAlbum = selectedAlbum,
							onSetShareId = { shareId = it },
							onPlayNext = if (selectedAlbum != null) {
								{ player.playNext(selectedAlbum as DomainSongCollection) }
							} else null,
							onAddToQueue = if (selectedAlbum != null) {
								{ player.addToQueue(selectedAlbum as DomainSongCollection) }
							} else null,
							selectedAlbumRating = selectedAlbumRating,
							selectedAlbumStarred = selectedAlbumIsStarred,
							onSetAlbumRating = { viewModel.rateSelectedAlbum(it) },
							onSetAlbumStarred = { viewModel.starSelectedAlbum(it) },
							onSelect = { viewModel.selectAlbum(it) },
							onDeselect = { viewModel.clearSelection() },
							tab = tab
						)
					}
				}
			}
		}

		ErrorSnackBar(
			error = (collectionState as? UiState.Error)?.error,
			onClearError = { viewModel.clearError() }
		)

		if (editDialogShown && collection is DomainPlaylist) {
			PlaylistEditDialog(
				playlist = collection,
				onDismissRequest = { editDialogShown = false },
				onSaved = { viewModel.refreshCollection(false) }
			)
		}

		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null; viewModel.clearSelection() },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)
	}
}
