package com.nickshortnct.ondo.ui.screens.song.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_no_songs
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.data.database.entities.DownloadEntity
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Note
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.core.UiState

fun LazyListScope.songListScreenContent(
	state: UiState<ImmutableList<DomainSong>>,
	selectedSong: DomainSong?,
	selectedSongIsStarred: Boolean,
	selectedSongRating: Int,
	allDownloads: List<DownloadEntity>,
	onUpdateSelection: (DomainSong) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetStarred: (Boolean) -> Unit,
	onPlayNext: (DomainSong) -> Unit,
	onAddToQueue: (DomainSong) -> Unit,
	onPlaySong: (DomainSong) -> Unit,
	onSetRating: (Int) -> Unit,
	onDownload: (DomainSong) -> Unit,
	onCancelDownload: (DomainSong) -> Unit,
	onDeleteDownload: (DomainSong) -> Unit
) {
	val downloadsById = allDownloads.associateBy { it.songId }
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data) { song ->
			val download = downloadsById[song.id]
			SongListScreenItem(
				modifier = Modifier.animateItem(),
				song = song,
				selected = song == selectedSong,
				starred = if (song == selectedSong) selectedSongIsStarred else song.starredAt != null,
				rating = if (song == selectedSong) selectedSongRating else 0,
				onSelect = { onUpdateSelection(song) },
				onDeselect = { onClearSelection() },
				onSetStarred = { onSetStarred(it) },
				onSetShareId = onSetShareId,
				onPlayNext = { onPlayNext(song) },
				onAddToQueue = { onAddToQueue(song) },
				onClick = { onPlaySong(song) },
				onSetRating = onSetRating,
				download = download,
				onDownload = { onDownload(song) },
				onCancelDownload = { onCancelDownload(song) },
				onDeleteDownload = { onDeleteDownload(song) }
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				// TODO
			}

			else -> {
				item {
					ContentUnavailable(
						icon = Icons.Outlined.Note,
						label = stringResource(Res.string.info_no_songs)
					)
				}
			}
		}
	}
}
