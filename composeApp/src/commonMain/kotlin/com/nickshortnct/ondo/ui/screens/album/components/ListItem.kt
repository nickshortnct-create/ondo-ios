package com.nickshortnct.ondo.ui.screens.album.components

import com.nickshortnct.ondo.ui.util.rememberHydratedCollection
import com.nickshortnct.ondo.domain.models.songIds
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
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
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.common.MarqueeText
import com.nickshortnct.ondo.ui.components.sheets.CollectionSheet
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import com.nickshortnct.ondo.ui.util.appendBulletPoint

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumListScreenListItem(
	modifier: Modifier = Modifier,
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
	val preferenceManager = koinInject<PreferenceManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val fullCollection = rememberHydratedCollection(album, selected || playlistDialogShown)
	val player = koinInject<com.nickshortnct.ondo.shared.MediaPlayerViewModel>()

	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by remember(album.id, album.songIds()) { downloadManager.getCollectionDownloadStatus(album.songIds()) }
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Box(modifier) {
		ListItem(
			leadingContent = {
				CoverArt(
					coverArtId = album.coverArtId,
					modifier = Modifier.size(50.dp),
					shape = preferenceManager.coverArtShape.decreasedShape
				)
			},
			content = { MarqueeText(album.name) },
			supportingContent = {
				MarqueeText(
					buildAnnotatedString {
						append(album.artistName)
						album.year?.let {
							appendBulletPoint()
							append("$it")
						}
					}
				)
			},
			onClick = dropUnlessResumed {
				scope.launch {
					backStack.add(Screen.CollectionDetail(album.id, ""))
				}
			},
			onLongClick = onSelect
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
