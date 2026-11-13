package com.nickshortnct.ondo.ui.screens.collection.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_more
import ondo.composeapp.generated.resources.action_search_playlist
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumInfo
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.MoreVert
import com.nickshortnct.ondo.icons.outlined.Search
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton
import com.nickshortnct.ondo.ui.components.sheets.CollectionSheet
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun CollectionDetailScreenTopBar(
	collection: DomainSongCollection?,
	albumInfoState: UiState<DomainAlbumInfo>,
	titleAlpha: Float,
	onSetShareId: (shareId: String?) -> Unit,
	onDownloadAll: () -> Unit,
	onCancelDownloadAll: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	downloadStatus: DownloadStatus,
	rating: Int?,
	onSetRating: ((Int) -> Unit)?,
	starred: Boolean?,
	onSetStarred: ((Boolean) -> Unit)? = null,
	refreshCollection: () -> Unit,
	selectedPlaylistTrackSort: PlaylistTrackSort?,
	playlistTrackSortDescending: Boolean,
	onSetPlaylistTrackSort: (PlaylistTrackSort) -> Unit,
	onSetPlaylistTrackSortDescending: (Boolean) -> Unit,
	onChangePlaylistCover: (() -> Unit)? = null,
	onRemovePlaylistCover: (() -> Unit)? = null,
	onEditPlaylist: (() -> Unit)? = null,
	onToggleSearch: (() -> Unit)? = null,
) {
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val backStack = LocalNavStack.current

	NestedTopBar(
		title = {
			Text(
				text = collection?.name.orEmpty(),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.alpha(titleAlpha)
			)
		},
		actions = {
			if (onToggleSearch != null) {
				TopBarButton(onToggleSearch) {
					Icon(Icons.Outlined.Search, stringResource(Res.string.action_search_playlist))
				}
			}
			if (selectedPlaylistTrackSort != null) {
				PlaylistTrackSortButton(
					selectedSort = selectedPlaylistTrackSort,
					descending = playlistTrackSortDescending,
					onSelectSort = onSetPlaylistTrackSort,
					onSetDescending = onSetPlaylistTrackSortDescending
				)
			}
			Box {
				var expanded by remember { mutableStateOf(false) }
				TopBarButton({
					expanded = true
					refreshCollection()
				}) {
					Icon(
						Icons.Outlined.MoreVert,
						stringResource(Res.string.action_more)
					)
				}
				if (expanded) {
					CollectionSheet(
						onDismissRequest = { expanded = false },
						collection = collection,
						albumInfo = (albumInfoState as? UiState.Success)?.data,
						onDownloadAll = onDownloadAll,
						onCancelDownloadAll = onCancelDownloadAll,
						downloadStatus = downloadStatus,
						onShare = { onSetShareId(collection?.id) },
						onPlayNext = onPlayNext,
						onAddToQueue = onAddToQueue,
						onAddAllToPlaylist = { playlistDialogShown = true },
						onViewArtist =
							if (collection is DomainAlbum)
								dropUnlessResumed { backStack.add(Screen.ArtistDetail(collection.artistId)) }
							else null,
						rating = rating,
						onSetRating = onSetRating,
						starred = starred,
						onSetStarred = if (onSetStarred != null && starred != null) {
							{ onSetStarred(!starred) }
						} else null,
						onChangePlaylistCover = onChangePlaylistCover,
						onRemovePlaylistCover = onRemovePlaylistCover,
						onEditPlaylist = onEditPlaylist,
					)
				}
			}
		}
	)

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = collection?.songs.orEmpty().toPersistentList(),
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
