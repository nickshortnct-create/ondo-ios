package com.nickshortnct.ondo.di

import org.koin.dsl.module
import com.nickshortnct.ondo.data.database.CacheDatabase
import com.nickshortnct.ondo.data.database.DownloadDatabase

val databaseModule = module {
	// CacheDatabase is initialised inside PlatformModule since we need Context on android
	// TODO: find a less shitty workaround for that^
	single { get<CacheDatabase>().albumDao() }
	single { get<CacheDatabase>().genreDao() }
	single { get<CacheDatabase>().playlistDao() }
	single { get<CacheDatabase>().songDao() }
	single { get<CacheDatabase>().artistDao() }
	single { get<CacheDatabase>().radioDao() }
	single { get<CacheDatabase>().lyricDao() }
	single { get<CacheDatabase>().syncActionDao() }
	single { get<DownloadDatabase>().downloadDao() }
}
