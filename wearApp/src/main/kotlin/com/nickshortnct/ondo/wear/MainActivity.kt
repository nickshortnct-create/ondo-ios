package com.nickshortnct.ondo.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nickshortnct.ondo.wear.ui.AlbumsScreen
import com.nickshortnct.ondo.wear.ui.ArtistScreen
import com.nickshortnct.ondo.wear.ui.ArtistsScreen
import com.nickshortnct.ondo.wear.ui.CollectionScreen
import com.nickshortnct.ondo.wear.ui.DownloadsScreen
import com.nickshortnct.ondo.wear.ui.HomeScreen
import com.nickshortnct.ondo.wear.ui.PhonePlayerScreen
import com.nickshortnct.ondo.wear.ui.PlaylistsScreen
import com.nickshortnct.ondo.wear.ui.Routes
import com.nickshortnct.ondo.wear.ui.SearchScreen
import com.nickshortnct.ondo.wear.ui.SettingsScreen
import com.nickshortnct.ondo.wear.ui.WatchPlayerScreen

class MainActivity : ComponentActivity() {
	private val openPlayer = mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		openPlayer.value = intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
		setContent {
			MaterialTheme {
				AppScaffold {
					val nav = rememberSwipeDismissableNavController()
					LaunchedEffect(openPlayer.value) {
						if (openPlayer.value) {
							openPlayer.value = false
							nav.navigate(Routes.PLAYER_WATCH) { launchSingleTop = true }
						}
					}
					OndoNavHost(nav)
				}
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayer.value = true
	}

	companion object {
		const val EXTRA_OPEN_PLAYER = "openPlayer"
	}
}

@androidx.compose.runtime.Composable
private fun OndoNavHost(nav: NavHostController) {
	val go: (String) -> Unit = { route ->
		if (route == Routes.PLAYER_WATCH || route == Routes.PLAYER_PHONE) {
			nav.navigate(route) {
				popUpTo(Routes.HOME)
				launchSingleTop = true
			}
		} else nav.navigate(route)
	}
	SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
		composable(Routes.HOME) { HomeScreen(go) }
		composable(Routes.PLAYER_WATCH) { WatchPlayerScreen() }
		composable(Routes.PLAYER_PHONE) { PhonePlayerScreen() }
		composable(Routes.PLAYLISTS) { PlaylistsScreen(go) }
		composable(Routes.ARTISTS) { ArtistsScreen(go) }
		composable(Routes.SEARCH) { SearchScreen(go) }
		composable(Routes.DOWNLOADS) { DownloadsScreen(go) }
		composable(Routes.SETTINGS) { SettingsScreen() }
		composable("albums/{type}/{title}") {
			AlbumsScreen(it.arguments?.getString("type").orEmpty(), it.arguments?.getString("title").orEmpty(), go)
		}
		composable("artist/{id}") { ArtistScreen(it.arguments?.getString("id").orEmpty(), go) }
		composable("collection/{kind}/{id}") {
			CollectionScreen(it.arguments?.getString("kind").orEmpty(), it.arguments?.getString("id").orEmpty(), go)
		}
	}
}
