package com.nickshortnct.ondo.ui.screens.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_sort_frequent
import ondo.composeapp.generated.resources.option_sort_newest
import ondo.composeapp.generated.resources.option_sort_random
import ondo.composeapp.generated.resources.option_sort_recent
import ondo.composeapp.generated.resources.option_sort_starred
import ondo.composeapp.generated.resources.title_artists
import ondo.composeapp.generated.resources.title_genre_mixes
import ondo.composeapp.generated.resources.title_genres
import ondo.composeapp.generated.resources.title_mood_mixes
import ondo.composeapp.generated.resources.title_playlists
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainGenre
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.ondo.MixTag
import com.nickshortnct.ondo.ondo.PinnedItem
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.icons.outlined.History
import com.nickshortnct.ondo.icons.outlined.LibraryAdd
import com.nickshortnct.ondo.icons.outlined.Shuffle
import com.nickshortnct.ondo.icons.outlined.Star
import com.nickshortnct.ondo.ui.components.layouts.horizontalSection
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.album.components.AlbumListScreenGridItem
import com.nickshortnct.ondo.ui.screens.artist.ArtistListScreenGridItem
import com.nickshortnct.ondo.ui.screens.genre.components.GenreListScreenCard
import com.nickshortnct.ondo.ui.screens.playlist.components.PlaylistListScreenGridItem
import com.nickshortnct.ondo.ui.util.withoutTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
	state: LazyGridState,
	scrollBehavior: TopAppBarScrollBehavior,
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,

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

	// artists
	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,

	// playlists
	playlistsState: UiState<ImmutableList<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	onSelectPlaylist: (DomainPlaylist) -> Unit,
	onClearPlaylistSelection: () -> Unit,
	onDeletePlaylist: (String) -> Unit,
	onPlayPlaylistNext: () -> Unit,
	onAddPlaylistToQueue: () -> Unit,

	// genres
	genresState: UiState<ImmutableList<DomainGenre>>,

	// weekly "Made for you" mixes (playlists the curator keeps up to date)
	mixes: List<Pair<DomainPlaylist, MixTag>> = emptyList(),

	// pinned to the top of Library
	pins: List<PinnedItem> = emptyList(),
	playlistsById: Map<String, DomainPlaylist> = emptyMap(),
	onPlayPinnedSong: (DomainSong) -> Unit = {},
	onUnpin: (PinnedItem) -> Unit = {}
) {
	LazyVerticalGrid(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		columns = GridCells.Fixed(2),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
		horizontalArrangement = Arrangement.spacedBy(5.dp),
		state = state
	) {
		pinnedSection(pins, playlistsById, onPlayPinnedSong, onUnpin)

		libraryScreenOverviewButton(
			icon = Icons.Outlined.LibraryAdd,
			label = Res.string.option_sort_newest,
			destination = Screen.AlbumList(true, DomainAlbumListType.Newest),
			start = true
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Shuffle,
			label = Res.string.option_sort_random,
			destination = Screen.AlbumList(true, DomainAlbumListType.Random),
			start = false
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Star,
			label = Res.string.option_sort_starred,
			destination = Screen.Starred(),
			start = true
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.History,
			label = Res.string.option_sort_frequent,
			destination = Screen.AlbumList(true, DomainAlbumListType.Frequent),
			start = false
		)

		val moodMixes = mixes.filter { it.second.isMood }
		val genreMixes = mixes.filterNot { it.second.isMood }
		mixSection(Res.string.title_mood_mixes, moodMixes, showSubtitle = true)
		mixSection(Res.string.title_genre_mixes, genreMixes, showSubtitle = moodMixes.isEmpty())

		horizontalSection(
			title = Res.string.option_sort_recent,
			destination = Screen.AlbumList(true, DomainAlbumListType.Recent),
			state = albumsState,
			key = { it.id },
			seeAll = true
		) { album ->
			AlbumListScreenGridItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				album = album,
				selected = album == selectedAlbum,
				starred = selectedAlbumIsStarred,
				onSelect = { onSelectAlbum(album) },
				onDeselect = { onClearAlbumSelection() },
				onSetStarred = { onStarSelectedAlbum(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayAlbumNext,
				onAddToQueue = onAddAlbumToQueue,
				rating = selectedAlbumRating,
				onSetRating = onRateSelectedAlbum
			)
		}

		horizontalSection(
			title = Res.string.title_playlists,
			destination = Screen.PlaylistList(true),
			state = playlistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenGridItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_artists,
			destination = Screen.ArtistList(true),
			state = artistsState,
			key = { it.id },
			seeAll = true
		) { artist ->
			ArtistListScreenGridItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				artist = artist,
				selected = artist == selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				starred = selectedArtistIsStarred,
				onSelect = { onSelectArtist(artist) },
				onDeselect = { onClearArtistSelection() },
				onSetStarred = { onStarSelectedArtist(it) },
				onPlayNext = onPlayArtistNext,
				onAddToQueue = onAddArtistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_genres,
			destination = Screen.GenreList(true),
			state = genresState,
			key = { it.name },
			seeAll = true
		) { genreWithAlbums ->
			GenreListScreenCard(genre = genreWithAlbums)
		}
	}
}
