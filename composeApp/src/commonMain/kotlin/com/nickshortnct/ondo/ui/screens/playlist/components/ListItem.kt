package com.nickshortnct.ondo.ui.screens.playlist.components

import com.nickshortnct.ondo.ondo.displayComment
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
import ondo.composeapp.generated.resources.count_songs
import ondo.composeapp.generated.resources.notice_deleted_download
import ondo.composeapp.generated.resources.notice_download_started
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PlaylistCoverManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.common.MarqueeText
import com.nickshortnct.ondo.ui.components.sheets.CollectionSheet
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistEditDialog
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.isEditableBy
import com.nickshortnct.ondo.ui.util.appendBulletPoint
import com.nickshortnct.ondo.ui.util.rememberPlaylistCoverPicker

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistListScreenListItem(
	modifier: Modifier = Modifier,
	playlist: DomainPlaylist,
	selected: Boolean,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val playlistCoverManager = koinInject<PlaylistCoverManager>()
	val scope = rememberCoroutineScope()

	val pickCover = rememberPlaylistCoverPicker { imageBytes ->
		imageBytes?.let { bytes ->
			scope.launch {
				playlistCoverManager.setCustomCover(playlist.id, bytes)
			}
		}
	}

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var editDialogShown by rememberSaveable { mutableStateOf(false) }
	val sessionManager = koinInject<SessionManager>()
	val fullCollection = rememberHydratedCollection(playlist, selected || playlistDialogShown)
	val player = koinInject<com.nickshortnct.ondo.shared.MediaPlayerViewModel>()
	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by remember(playlist.id, playlist.songIds()) { downloadManager.getCollectionDownloadStatus(playlist.songIds()) }
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Box(modifier) {
		ListItem(
			leadingContent = {
				CoverArt(
					coverArtId = playlist.coverArtId,
					localImageUri = playlist.customCoverPath,
					modifier = Modifier.size(50.dp),
					shape = preferenceManager.coverArtShape.decreasedShape
				)
			},
			content = { MarqueeText(playlist.name) },
			supportingContent = {
				MarqueeText(
					text = buildAnnotatedString {
						append(
							pluralStringResource(
								Res.plurals.count_songs,
								playlist.songCount,
								playlist.songCount
							)
						)
						playlist.displayComment?.let {
							appendBulletPoint()
							append(it)
						}
					}
				)
			},
			onClick = dropUnlessResumed {
				scope.launch {
					backStack.add(Screen.CollectionDetail(playlist.id, ""))
				}
			},
			onLongClick = onSelect
		)
		if (selected && fullCollection != null) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = fullCollection,
				onShare = { onSetShareId(playlist.id) },
				onDelete = { onSetDeletionId(playlist.id) },
				onPlayNext = { player.playNext(fullCollection) },
				onAddToQueue = { player.addToQueue(fullCollection) },
				onAddAllToPlaylist = { playlistDialogShown = true },
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
				onChangePlaylistCover = pickCover,
				onRemovePlaylistCover = if (!playlist.customCoverPath.isNullOrBlank()) {
					{
						scope.launch {
							playlistCoverManager.clearCustomCover(playlist.id)
						}
					}
				} else null,
				onEditPlaylist = if (playlist.isEditableBy(sessionManager.username)) {
					{ editDialogShown = true }
				} else null,
			)
		}

		if (editDialogShown) {
			PlaylistEditDialog(
				playlist = playlist,
				onDismissRequest = { editDialogShown = false }
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
