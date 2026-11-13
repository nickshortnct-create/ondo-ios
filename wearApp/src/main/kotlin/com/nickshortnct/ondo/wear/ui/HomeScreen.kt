package com.nickshortnct.ondo.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.nickshortnct.ondo.wear.R
import com.nickshortnct.ondo.wear.data.PlayTarget

@Composable
fun HomeScreen(navigate: (String) -> Unit) {
	val app = ondoApp
	val account by app.accounts.account.collectAsState()
	val target by app.settings.playTarget.collectAsState()
	val watch by app.player.state.collectAsState()
	val phone by app.phoneLink.nowPlaying.collectAsState()
	val phoneConnected by app.phoneLink.phoneConnected.collectAsState()
	LaunchedEffect(Unit) {
		app.phoneLink.refresh()
		app.player.attachIfRunning()
	}

	ScreenList(title = "Ondo") {
		if (watch.hasItem) item {
			NowPlayingButton("On watch", watch.title, watch.artist, watch.playing) { navigate(Routes.PLAYER_WATCH) }
		}
		val p = phone
		if (p != null && p.hasItem) item {
			NowPlayingButton("On phone", p.title, p.artist, p.playing) { navigate(Routes.PLAYER_PHONE) }
		} else item {
			NavButton(
				label = "Phone player",
				icon = R.drawable.ic_phone,
				secondary = if (phoneConnected) "Nothing playing" else "Not connected",
			) { navigate(Routes.PLAYER_PHONE) }
		}

		if (account == null) {
			item {
				TitleCard(onClick = { app.scopeLaunch { app.phoneLink.requestAccount() } }, title = { Text("Sign in on your phone") }) {
					Text(
						"Open Ondo on your phone and log in. The watch picks up the login automatically. Tap to retry.",
						style = MaterialTheme.typography.bodySmall,
					)
				}
			}
		} else {
			item {
				SwitchButton(
					checked = target == PlayTarget.Watch,
					onCheckedChange = { app.settings.setPlayTarget(if (it) PlayTarget.Watch else PlayTarget.Phone) },
					modifier = Modifier.fillMaxWidth(),
					icon = { Icon(painterResource(if (target == PlayTarget.Watch) R.drawable.ic_watch else R.drawable.ic_phone), null) },
					label = { Text("Play on watch") },
					secondaryLabel = { Text(if (target == PlayTarget.Watch) "Watch speaker / headphones" else "Library plays on phone") },
				)
			}
			item { ListHeader { Text("Library") } }
			item { NavButton("Playlists", R.drawable.ic_playlist) { navigate(Routes.PLAYLISTS) } }
			item { NavButton("Albums", R.drawable.ic_album) { navigate(Routes.albums("alphabeticalByName", "Albums")) } }
			item { NavButton("Artists", R.drawable.ic_artist) { navigate(Routes.ARTISTS) } }
			item { NavButton("Recently added", R.drawable.ic_recent) { navigate(Routes.albums("newest", "Recently added")) } }
			item { NavButton("Recently played", R.drawable.ic_queue) { navigate(Routes.albums("recent", "Recently played")) } }
			item { NavButton("Favourite songs", R.drawable.ic_star_filled) { navigate(Routes.collection("starred", "all")) } }
			item { NavButton("Shuffle all", R.drawable.ic_shuffle_on) { navigate(Routes.collection("random", "all")) } }
			item { NavButton("Search", R.drawable.ic_search) { navigate(Routes.SEARCH) } }
		}
		item { ListHeader { Text("On this watch") } }
		item { NavButton("Downloads", R.drawable.ic_download, secondary = "Offline music") { navigate(Routes.DOWNLOADS) } }
		item { NavButton("Settings", R.drawable.ic_settings) { navigate(Routes.SETTINGS) } }
	}
}

@Composable
private fun NowPlayingButton(device: String, title: String, artist: String, playing: Boolean, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		colors = ButtonDefaults.buttonColors(),
		icon = { Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), null) },
		secondaryLabel = { Text("$device · $artist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
		label = { Text(title.ifBlank { "Now playing" }, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start) },
	)
}
