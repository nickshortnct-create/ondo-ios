package com.nickshortnct.ondo.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import com.nickshortnct.ondo.data.database.CacheDatabase
import com.nickshortnct.ondo.data.database.DownloadDatabase
import com.nickshortnct.ondo.domain.manager.AppIconManager
import com.nickshortnct.ondo.domain.manager.AudioOutputManager
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.LinkManager
import com.nickshortnct.ondo.domain.manager.LogManager
import com.nickshortnct.ondo.domain.manager.PermissionManager
import com.nickshortnct.ondo.domain.manager.PlaylistCoverStorage
import com.nickshortnct.ondo.domain.manager.ShareManager
import com.nickshortnct.ondo.domain.manager.StorageManager
import com.nickshortnct.ondo.shared.IOSMediaPlayerViewModel
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import coil3.PlatformContext as CoilPlatformContext

actual val platformModule = module {
	single { PlatformType.IOS }
	single<CacheDatabase> {
		val dbPath = documentDirectory() + "/cache.db"
		Room
			.databaseBuilder<CacheDatabase>(dbPath)
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<DownloadDatabase> {
		val dbPath = documentDirectory() + "/downloads.db"
		Room
			.databaseBuilder<DownloadDatabase>(dbPath)
			.setDriver(BundledSQLiteDriver())
			// No destructive fallback on upgrade: a schema change must ship a real Migration or every
			// offline-download record is lost (the audio files stay on disk). DownloadManager also
			// reconciles the folder with the table at startup.
			.fallbackToDestructiveMigrationOnDowngrade(true)
			.build()
	}

	single<MediaPlayerViewModel> {
		IOSMediaPlayerViewModel(
			stateRepository = get(),
			songRepository = get(),
			downloadManager = get(),
			connectivityManager = get(),
			syncManager = get(),
			sessionManager = get(),
			preferenceManager = get(),
			snackBarManager = get()
		)
	}

	single { PlaylistCoverStorage() }
	single { com.nickshortnct.ondo.ondo.CurationCacheStorage() }
	singleOf(::ShareManager)
	single<CoilPlatformContext> { CoilPlatformContext.INSTANCE }
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::AudioOutputManager)
	singleOf(::LogManager)
	singleOf(::AppIconManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	// The App Store updates the iPhone app; the APK updater is Android-only.
	single<AppUpdater> {
		object : AppUpdater {
			override val installedVersionCode = 0L
			override suspend fun download(url: String, expectedVersionCode: Long) = false
			override fun install(expectedVersionCode: Long) = UpdateInstallResult.Unavailable
		}
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
	val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
		directory = NSDocumentDirectory,
		inDomain = NSUserDomainMask,
		appropriateForURL = null,
		create = false,
		error = null,
	)
	return requireNotNull(documentDirectory?.path)
}
