package com.nickshortnct.ondo.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.EqualiserManager
import com.nickshortnct.ondo.domain.manager.LoginManager
import com.nickshortnct.ondo.domain.manager.PlaylistCoverManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SleepTimerManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.manager.SyncManager

val managerModule = module {
    single { com.nickshortnct.ondo.ondo.OndoSettings(get()) }
    single {
        com.nickshortnct.ondo.ondo.OndoApi(get()).apply {
            val session = get<SessionManager>()
            // Subsonic token auth (u, t, s) as the Navidrome login proof for gateway-mode services.
            navidromeAuth = {
                val params = io.ktor.http.Url(session.api.getCoverArtUrl("ondo-auth")).parameters
                listOf("u", "t", "s").associateWith { params[it].orEmpty() }
            }
        }
    }
    single { com.nickshortnct.ondo.ondo.OndoDownloads(get()) }
    single { com.nickshortnct.ondo.ondo.CurationManager(get(), get(), get(), get(), get(), get(), get()) }
    single { com.nickshortnct.ondo.ondo.PinManager(get(), get()) }
    single { com.nickshortnct.ondo.ondo.PlaylistPlayStats(get(), get()) }
    single { com.nickshortnct.ondo.ondo.OndoConnect(get(), get(), get(), get()) }
	singleOf(::SleepTimerManager)
	// Created on first use (the UI and playback service inject it); it starts itself once logged in.
	single { SyncManager(get(), get(), get(), get(), get(), get()) }
	single { CoverArtVersions(coverArtCacheDirectory(get()) / "versions.tsv", imageCacheScope) }
	single { DownloadedCoverArt(coverArtCacheDirectory(get()) / "downloaded", get()) }
	singleOf(::DownloadManager)
	singleOf(::SessionManager)
	singleOf(::PreferenceManager)
	singleOf(::SnackBarManager)
	singleOf(::LoginManager)
	singleOf(::EqualiserManager)
	singleOf(::PlaylistCoverManager)
}
