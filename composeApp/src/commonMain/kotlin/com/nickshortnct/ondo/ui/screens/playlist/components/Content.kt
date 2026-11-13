package com.nickshortnct.ondo.ui.screens.playlist.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_no_playlists_short
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.settings.ListViewMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.PlaylistRemove
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.components.layouts.artGridPlaceholder
import com.nickshortnct.ondo.ui.core.UiState

fun LazyGridScope.playlistListScreenContent(
	state: UiState<List<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	selectedViewMode: ListViewMode,
	onUpdateSelection: (DomainPlaylist) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data, { it.id }) { playlist ->
			if (selectedViewMode == ListViewMode.Grid) {
				PlaylistListScreenGridItem(
					modifier = Modifier.animateItem(),
					tab = "playlists",
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			} else {
				PlaylistListScreenListItem(
					modifier = Modifier.animateItem(),
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			}
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				artGridPlaceholder(viewMode = selectedViewMode)
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.PlaylistRemove,
						label = stringResource(Res.string.info_no_playlists_short)
					)
				}
			}
		}
	}
}
