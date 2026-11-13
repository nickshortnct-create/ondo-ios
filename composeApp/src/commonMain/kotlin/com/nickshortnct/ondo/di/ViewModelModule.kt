package com.nickshortnct.ondo.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.ui.components.dialogs.DeletionViewModel
import com.nickshortnct.ondo.ui.components.sheets.ChangelogViewModel
import com.nickshortnct.ondo.ui.screens.album.viewmodels.AlbumListViewModel
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistDetailViewModel
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.ArtistListViewModel
import com.nickshortnct.ondo.ui.screens.artist.viewmodels.DiscoveryArtistViewModel
import com.nickshortnct.ondo.ui.screens.collection.viewmodels.CollectionDetailViewModel
import com.nickshortnct.ondo.ui.screens.genre.viewmodels.GenreListViewModel
import com.nickshortnct.ondo.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import com.nickshortnct.ondo.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistCreateDialogViewModel
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistEditDialogViewModel
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistListViewModel
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel
import com.nickshortnct.ondo.ui.screens.queue.viewmodels.QueueViewModel
import com.nickshortnct.ondo.ui.screens.radio.viewmodels.RadioCreateDialogViewModel
import com.nickshortnct.ondo.ui.screens.radio.viewmodels.RadioListViewModel
import com.nickshortnct.ondo.ui.screens.search.viewmodels.SearchViewModel
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.LyricsPriorityViewModel
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.NavtabsViewModel
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import com.nickshortnct.ondo.ui.screens.share.viewmodels.ShareDialogViewModel
import com.nickshortnct.ondo.ui.screens.share.viewmodels.ShareListViewModel
import com.nickshortnct.ondo.ui.screens.song.viewmodels.SongDetailViewModel
import com.nickshortnct.ondo.ui.screens.song.viewmodels.SongListViewModel
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel

val viewModelModule = module {
	viewModel { (artistId: String) ->
		ArtistDetailViewModel(
			artistId = artistId,
			repository = get(),
			artistRepository = get(),
			songRepository = get(),
			albumRepository = get(),
			artistDao = get(),
			albumDao = get(),
			downloadManager = get(),
			snackBarManager = get(),
			downloads = get(),
			connectivityManager = get()
		)
	}

	viewModel { (name: String, musicBrainzId: String, skipLocalRedirect: Boolean) ->
		DiscoveryArtistViewModel(
			name = name,
			musicBrainzId = musicBrainzId,
			skipLocalRedirect = skipLocalRedirect,
			downloads = get(),
			artistDao = get(),
			albumDao = get()
		)
	}

	viewModel { (song: DomainSong?) ->
		LyricsScreenViewModel(
			song = song,
			repository = get()
		)
	}

	viewModel { (songs: List<DomainSong>, playlistToExclude: String?) ->
		PlaylistUpdateDialogViewModel(
			songs = songs,
			playlistToExclude = playlistToExclude,
			playlistSyncFetcher = get(),
			dbRepository = get(),
			sessionManager = get(),
			snackBarManager = get(),
			playlistPlayStats = get()
		)
	}

	viewModel { params ->
		AlbumListViewModel(
			initialListType = params.getOrNull<DomainAlbumListType>() ?: DomainAlbumListType.AlphabeticalByArtist,
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			sessionManager = get(),
			preferenceManager = get()
		)
	}
	viewModel { params ->
		SongListViewModel(
			initialListType = params.getOrNull<DomainSongListType>() ?: DomainSongListType.FrequentlyPlayed,
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			downloadManager = get(),
			sessionManager = get(),
			preferenceManager = get(),
			connectivityManager = get()
		)
	}
	viewModel { params ->
		ArtistListViewModel(
			initialListType = params.getOrNull<DomainArtistListType>() ?: DomainArtistListType.AlphabeticalByName,
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			albumDao = get(),
			sessionManager = get(),
			preferenceManager = get()
		)
	}
	viewModelOf(::SearchViewModel)
	viewModelOf(::GenreListViewModel)
	viewModelOf(::RadioListViewModel)
	viewModelOf(::RadioCreateDialogViewModel)
	viewModelOf(::PlaylistListViewModel)
	viewModelOf(::QueueViewModel)
	viewModelOf(::ShareListViewModel)
	viewModelOf(::DeletionViewModel)
	viewModelOf(::ShareDialogViewModel)
	viewModel { (playlist: DomainPlaylist) ->
		PlaylistEditDialogViewModel(
			playlist = playlist,
			sessionManager = get(),
			dbRepository = get(),
			playlistCoverManager = get(),
			playlistImages = get(),
			snackBarManager = get()
		)
	}
	viewModel { (songs: List<DomainSong>) ->
		PlaylistCreateDialogViewModel(
			songs = songs,
			playlistDao = get(),
			playlistSyncFetcher = get(),
			dbRepository = get(),
			sessionManager = get(),
			snackBarManager = get()
		)
	}
	viewModel { params ->
		CollectionDetailViewModel(
			collectionId = params.get(),
			repository = get(),
			songRepository = get(),
			albumRepository = get(),
			downloadManager = get(),
			sessionManager = get(),
			snackBarManager = get(),
			playlistCoverManager = get(),
			playlistSyncFetcher = get(),
			dbRepository = get(),
			connectivityManager = get()
		)
	}
	viewModelOf(::SongDetailViewModel)
	viewModelOf(::SettingsDataStorageViewModel)
	viewModelOf(::ChangelogViewModel)
	viewModel { params ->
		NowPlayingViewModel(
			player = params.get(),
			songRepository = get()
		)
	}
	viewModelOf(::NavtabsViewModel)
	viewModelOf(::LyricsPriorityViewModel)
	viewModelOf(::RootViewModel)
}
