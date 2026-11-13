package com.nickshortnct.ondo.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import com.nickshortnct.ondo.data.subsonic.NavidromePlaylistImages
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.domain.repositories.AlbumRepository
import com.nickshortnct.ondo.domain.repositories.ArtistRepository
import com.nickshortnct.ondo.domain.repositories.CollectionRepository
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.domain.repositories.GenreRepository
import com.nickshortnct.ondo.domain.repositories.LyricsRepository
import com.nickshortnct.ondo.domain.repositories.PlayerStateRepository
import com.nickshortnct.ondo.domain.repositories.PlaylistRepository
import com.nickshortnct.ondo.domain.repositories.RadioRepository
import com.nickshortnct.ondo.domain.repositories.SearchRepository
import com.nickshortnct.ondo.domain.repositories.UnifiedSearchRepository
import com.nickshortnct.ondo.domain.repositories.ShareRepository
import com.nickshortnct.ondo.domain.repositories.SongRepository

val repositoryModule = module {
	singleOf(::PlaylistSyncFetcher)
	singleOf(::NavidromePlaylistImages)
	singleOf(::AlbumRepository)
	singleOf(::ArtistRepository)
	singleOf(::DbRepository)
	singleOf(::GenreRepository)
	singleOf(::LyricsRepository)
	singleOf(::SearchRepository)
	singleOf(::UnifiedSearchRepository)
	singleOf(::ShareRepository)
	singleOf(::CollectionRepository)
	singleOf(::PlaylistRepository)
	singleOf(::SongRepository)
	singleOf(::RadioRepository)
	singleOf(::PlayerStateRepository)
}
