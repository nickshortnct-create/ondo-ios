package com.nickshortnct.ondo.androidApp

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nickshortnct.ondo.App
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.shared.DownloadService
import com.nickshortnct.ondo.ui.navigation.AppRoutes
import org.koin.android.ext.android.inject
import com.nickshortnct.ondo.shared.PlaybackService
import com.nickshortnct.ondo.util.Logger

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
	private var searchBrowser: MediaBrowser? = null
	private var searchHandled = false
	private val downloadManager: DownloadManager by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent { App() }
		searchHandled = savedInstanceState?.getBoolean("ondo.searchHandled") ?: false
		if (!searchHandled) handleSearchIntent(intent)
		handleOpenPageIntent(intent)
	}

	override fun onStart() {
		super.onStart()
		// Lifts a foreground-service background-limit suspension and resumes leftover downloads.
		downloadManager.onAppForegrounded()
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		searchHandled = false
		handleSearchIntent(intent)
		handleOpenPageIntent(intent)
	}

	private fun handleOpenPageIntent(intent: Intent?) {
		intent?.getStringExtra(DownloadService.EXTRA_OPEN_ONDO_PAGE)?.let {
			AppRoutes.pendingOndoPage.value = it
			intent.removeExtra(DownloadService.EXTRA_OPEN_ONDO_PAGE)
		}
	}

	private fun handleSearchIntent(intent: Intent?) {
		if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
		val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
		val extras = intent.extras?.let(::Bundle)
		searchBrowser?.disconnect()
		lateinit var browser: MediaBrowser
		browser = MediaBrowser(
			this,
			ComponentName(this, PlaybackService::class.java),
			object : MediaBrowser.ConnectionCallback() {
				override fun onConnected() {
					if (isDestroyed || searchBrowser !== browser) return
					// The session prepares and plays only after this search resolves successfully.
					// Separate setMediaItem/prepare/play commands would resume the old queue on failure.
					MediaController(this@MainActivity, browser.sessionToken)
						.transportControls.playFromSearch(query, extras)
					searchHandled = true
				}

				override fun onConnectionFailed() {
					Logger.e("MainActivity", "Could not connect the voice search request to playback")
				}
			},
			null
		)
		searchBrowser = browser
		browser.connect()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putBoolean("ondo.searchHandled", searchHandled)
		super.onSaveInstanceState(outState)
	}

	override fun onDestroy() {
		searchBrowser?.disconnect()
		searchBrowser = null
		super.onDestroy()
	}
}
