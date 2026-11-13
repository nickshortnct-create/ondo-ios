package com.nickshortnct.ondo.ui.screens.album.components

import com.nickshortnct.ondo.ui.util.rememberHydratedCollection
import com.nickshortnct.ondo.domain.models.songIds
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.notice_deleted_download
import ondo.composeapp.generated.resources.notice_download_started
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.ui.components.layouts.ArtGridItem
import com.nickshortnct.ondo.ui.components.sheets.CollectionSheet
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun AlbumListScreenGridItem(
	modifier: Modifier = Modifier,
	tab: String,
	album: DomainAlbum,
	selected: Boolean,
	starred: Boolean,
	rating: Int,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit,
	onSetShareId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSetRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val fullCollection = rememberHydratedCollection(album, selected || playlistDialogShown)
	val player = koinInject<com.nickshortnct.ondo.shared.MediaPlayerViewModel>()

	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by remember(album.id, album.songIds()) { downloadManager.getCollectionDownloadStatus(album.songIds()) }
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				scope.launch {
					backStack.add(Screen.CollectionDetail(album.id, tab))
				}
			},
			onLongClick = onSelect,
			coverArtId = album.coverArtId,
			title = album.name,
			subtitle = album.artistName,
			id = album.id,
			tab = tab
		)
		if (selected && fullCollection != null) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = fullCollection,
				onShare = { onSetShareId(album.id) },
				onPlayNext = { player.playNext(fullCollection) },
				onAddToQueue = { player.addToQueue(fullCollection) },
				downloadStatus = downloadStatus,
				onDownloadAll = {
					scope.launch {
						downloadManager.downloadCollection(fullCollection!!)
						snackBarManager.notify(Res.string.notice_download_started)
					}
				},
				onCancelDownloadAll = {
					scope.launch {
						fullCollection!!.songs.forEach { downloadManager.cancelDownload(it.id) }
					}
				},
				onDeleteDownloadAll = {
					scope.launch {
						downloadManager.deleteDownloadedCollection(fullCollection!!)
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				},
				starred = starred,
				onSetStarred = onSetStarred,
				onAddAllToPlaylist = { playlistDialogShown = true },
				onViewArtist = dropUnlessResumed {
					backStack.add(Screen.ArtistDetail(album.artistId))
				},
				rating = rating,
				onSetRating = onSetRating
			)
		}

		if (playlistDialogShown && fullCollection != null) {
			PlaylistUpdateDialog(
				songs = fullCollection.songs.toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}
