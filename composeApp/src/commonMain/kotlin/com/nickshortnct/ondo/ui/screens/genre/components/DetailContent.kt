package com.nickshortnct.ondo.ui.screens.genre.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_albums
import ondo.composeapp.generated.resources.title_songs
import com.nickshortnct.ondo.data.database.entities.DownloadEntity
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.ui.components.common.SongRow
import com.nickshortnct.ondo.ui.components.layouts.header
import com.nickshortnct.ondo.ui.components.layouts.horizontalSection
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.album.components.AlbumListScreenGridItem
import com.nickshortnct.ondo.ui.util.withoutTop

@Composable
fun GenreDetailScreenContent(
	genreName: String,
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,
	isOnline: Boolean,

	songsState: UiState<ImmutableList<DomainSong>>,
	selectedSong: DomainSong?,
	selectedSongIsStarred: Boolean,
	selectedSongRating: Int,
	allDownloads: ImmutableList<DownloadEntity>,
	onSelectSong: (DomainSong) -> Unit,
	onClearSongSelection: () -> Unit,
	onAddSongStar: () -> Unit,
	onRemoveSongStar: () -> Unit,
	onPlaySongNext: (DomainSong) -> Unit,
	onAddSongToQueue: (DomainSong) -> Unit,
	onPlaySong: (Int) -> Unit,
	onSetSongRating: (Int) -> Unit,
	onDownloadSong: (DomainSong) -> Unit,
	onCancelDownloadSong: (DomainSong) -> Unit,
	onDeleteDownloadSong: (DomainSong) -> Unit,

	// albums
	albumsState: UiState<ImmutableList<DomainAlbum>>,
	selectedAlbum: DomainAlbum?,
	selectedAlbumIsStarred: Boolean,
	selectedAlbumRating: Int,
	onSelectAlbum: (DomainAlbum) -> Unit,
	onClearAlbumSelection: () -> Unit,
	onStarSelectedAlbum: (Boolean) -> Unit,
	onRateSelectedAlbum: (Int) -> Unit,
	onPlayAlbumNext: () -> Unit,
	onAddAlbumToQueue: () -> Unit,
) {
	val downloadsById = remember(allDownloads) { allDownloads.associateBy { it.songId } }

	val songs = songsState.data.orEmpty().take(12)

	LazyVerticalGrid(
		columns = GridCells.Fixed(2),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
		horizontalArrangement = Arrangement.spacedBy(5.dp),
	) {
		header(
			title = Res.string.title_songs,
			destination = Screen.SongList(true, DomainSongListType.ByGenre(genreName)),
			active = true
		)
		item(span = { GridItemSpan(maxLineSpan) }) {
			val rowCount = remember(songs.size) {
				songs.size.coerceIn(1, 3)
			}
			val gridHeight = remember(rowCount) {
				when (rowCount) {
					1 -> 82.dp
					2 -> 164.dp
					else -> 246.dp
				}
			}
			val gridState = rememberLazyGridState()
			LazyHorizontalGrid(
				rows = GridCells.Fixed(rowCount),
				state = gridState,
				flingBehavior = rememberSnapFlingBehavior(lazyGridState = gridState),
				modifier = Modifier.fillMaxWidth().height(gridHeight)
			) {
				itemsIndexed(songs) { index, song ->
					val download = downloadsById[song.id]
					SongRow(
						song = song,
						selected = selectedSong == song,
						onClick = { onPlaySong(index) },
						onLongClick = { onSelectSong(song) },
						onDismissRequest = { onClearSongSelection() },
						starredState = if (selectedSong == song) selectedSongIsStarred else song.starredAt != null,
						onAddStar = onAddSongStar,
						onRemoveStar = onRemoveSongStar,
						download = download,
						onDownload = { onDownloadSong(song) },
						onCancelDownload = { onCancelDownloadSong(song) },
						onDeleteDownload = { onDeleteDownloadSong(song) },
						onPlayNext = { onPlaySongNext(song) },
						onAddToQueue = { onAddSongToQueue(song) },
						onShare = { onSetShareId(song.id) },
						isOnline = isOnline,
						rating = selectedSongRating,
						onSetRating = { onSetSongRating(it) }
					)
				}
			}
		}

		horizontalSection(
			title = Res.string.title_albums,
			destination = Screen.AlbumList(true, DomainAlbumListType.ByGenre(genreName)),
			state = albumsState,
			key = { it.id },
			seeAll = true
		) { album ->
			AlbumListScreenGridItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "genre",
				album = album,
				selected = album == selectedAlbum,
				starred = selectedAlbumIsStarred,
				onSelect = { onSelectAlbum(album) },
				onDeselect = onClearAlbumSelection,
				onSetStarred = onStarSelectedAlbum,
				onSetShareId = onSetShareId,
				onPlayNext = onPlayAlbumNext,
				onAddToQueue = onAddAlbumToQueue,
				rating = selectedAlbumRating,
				onSetRating = onRateSelectedAlbum
			)
		}
	}
}
