package com.nickshortnct.ondo.di

import androidx.media3.common.util.UnstableApi
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import com.nickshortnct.ondo.data.database.CacheDatabase
import com.nickshortnct.ondo.data.database.DownloadDatabase
import com.nickshortnct.ondo.domain.manager.AppIconManager
import com.nickshortnct.ondo.domain.manager.AudioGainManager
import com.nickshortnct.ondo.domain.manager.AudioOutputManager
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.LinkManager
import com.nickshortnct.ondo.domain.manager.LogManager
import com.nickshortnct.ondo.domain.manager.PermissionManager
import com.nickshortnct.ondo.domain.manager.PlaylistCoverStorage
import com.nickshortnct.ondo.domain.manager.ShareManager
import com.nickshortnct.ondo.domain.manager.StorageManager
import com.nickshortnct.ondo.exoplayer.AudioGainProcessor
import com.nickshortnct.ondo.shared.MediaLibraryTree
import com.nickshortnct.ondo.shared.OndoMediaItemFactory
import com.nickshortnct.ondo.shared.AndroidMediaPlayerViewModel
import com.nickshortnct.ondo.shared.MediaPlayerViewModel

@UnstableApi
actual val platformModule = module {
	single { PlatformType.Android }
	single<AppUpdater> { AndroidApkUpdater(androidApplication()) }
	single<CacheDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("cache.db")
			.absolutePath
		Room
			.databaseBuilder<CacheDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.build()
	}

	single<DownloadDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("downloads.db")
			.absolutePath
		Room
			.databaseBuilder<DownloadDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			// No destructive fallback on upgrade: a schema change must ship a real Migration or every
			// offline-download record is lost (the audio files stay on disk). DownloadManager also
			// reconciles the folder with the table at startup.
			.fallbackToDestructiveMigrationOnDowngrade(true)
			.build()
	}

	single<MediaPlayerViewModel> {
		AndroidMediaPlayerViewModel(
			application = androidApplication(),
			stateRepository = get(),
			songRepository = get(),
			albumDao = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get(),
			mediaItemFactory = get(),
			preferenceManager = get(),
			snackBarManager = get()
		)
	}

	single { PlaylistCoverStorage(androidApplication()) }
	single { com.nickshortnct.ondo.ondo.CurationCacheStorage(androidApplication()) }
	singleOf(::ShareManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::AudioOutputManager)
	singleOf(::LogManager)
	singleOf(::AppIconManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::AudioGainManager)
	singleOf(::AudioGainProcessor)

	single {
		OndoMediaItemFactory(
			downloadManager = get(),
			preferenceManager = get(),
			connectivityManager = get(),
			sessionManager = get()
		)
	}

	single {
		MediaLibraryTree(
			albumDao = get(),
			artistDao = get(),
			playlistDao = get(),
			songDao = get(),
			itemFactory = get()
		)
	}
}
