package com.nickshortnct.ondo.wear.ui

import android.media.AudioManager
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import com.nickshortnct.ondo.wear.R
import com.nickshortnct.ondo.wear.data.WearContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything a player screen needs, whether it drives the watch or the phone. */
data class PlayerUi(
	val hasItem: Boolean,
	val title: String,
	val artist: String,
	val artwork: Any?,
	val playing: Boolean,
	val buffering: Boolean,
	val shuffle: Boolean,
	val repeatMode: Int,
	val starred: Boolean,
	val canFavorite: Boolean,
	val positionMs: Long,
	val durationMs: Long,
	val deviceLabel: String,
	val volumeLabel: String?,
	/** Shown instead of the time, e.g. when the watch is waiting for headphones. */
	val notice: String? = null,
)

interface PlayerActions {
	fun toggle()
	fun next()
	fun previous()
	fun shuffle()
	fun repeat()
	fun favorite()
	fun volume(up: Boolean)
}

@Composable
fun WatchPlayerScreen() {
	val app = ondoApp
	val state by app.player.state.collectAsState()
	val context = LocalContext.current
	val audio = remember { context.getSystemService(AudioManager::class.java) }
	val scope = rememberCoroutineScope()
	var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
	LaunchedEffect(Unit) { app.player.connect() }
	val song = state.song
	PlayerLayout(
		ui = PlayerUi(
			hasItem = state.hasItem,
			title = state.title,
			artist = state.artist,
			artwork = state.artworkUri,
			playing = state.playing,
			buffering = state.buffering,
			shuffle = state.shuffle,
			repeatMode = state.repeatMode,
			starred = song?.starred == true,
			canFavorite = song != null && app.accounts.account.value != null,
			positionMs = state.positionMs,
			durationMs = state.durationMs,
			deviceLabel = "On watch",
			volumeLabel = "${volume.first}/${volume.second}",
			notice = if (state.waitingForOutput) "Connect headphones" else null,
		),
		actions = object : PlayerActions {
			override fun toggle() = app.player.toggle()
			override fun next() = app.player.next()
			override fun previous() = app.player.previous()
			override fun shuffle() = app.player.toggleShuffle()
			override fun repeat() = app.player.cycleRepeat()
			override fun favorite() {
				val current = song ?: return
				val starred = !current.starred
				app.player.markStarred(current.id, starred)
				scope.launch { runCatching { app.api.setStarred(current.id, starred) }.onFailure { app.player.markStarred(current.id, !starred) } }
			}
			override fun volume(up: Boolean) {
				audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
				volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
			}
		},
		emptyText = "Nothing is playing on the watch. Pick something from the library.",
	)
}

@Composable
fun PhonePlayerScreen() {
	val app = ondoApp
	val state by app.phoneLink.nowPlaying.collectAsState()
	val art by app.phoneLink.artwork.collectAsState()
	val connected by app.phoneLink.phoneConnected.collectAsState()
	var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(Unit) {
		app.phoneLink.refresh()
		while (true) {
			delay(1_000)
			now = System.currentTimeMillis()
		}
	}
	val s = state
	val link = app.phoneLink
	PlayerLayout(
		ui = PlayerUi(
			hasItem = s?.hasItem == true,
			title = s?.title.orEmpty(),
			artist = s?.artist.orEmpty(),
			artwork = art,
			playing = s?.playing == true,
			buffering = s?.buffering == true,
			shuffle = s?.shuffle == true,
			repeatMode = s?.repeatMode ?: Player.REPEAT_MODE_OFF,
			starred = s?.starred == true,
			canFavorite = s?.canFavorite == true,
			positionMs = s?.currentPositionMs(now) ?: 0,
			durationMs = s?.durationMs ?: 0,
			deviceLabel = if (connected) "On phone" else "Phone not connected",
			volumeLabel = s?.let { "${it.volume}/${it.maxVolume}" },
		),
		actions = object : PlayerActions {
			override fun toggle() {
				link.updateLocally { it.copy(playing = !it.playing, positionMs = it.currentPositionMs(), positionAt = System.currentTimeMillis()) }
				link.sendAsync(WearContract.CMD_TOGGLE)
			}
			override fun next() = link.sendAsync(WearContract.CMD_NEXT)
			override fun previous() = link.sendAsync(WearContract.CMD_PREVIOUS)
			override fun shuffle() {
				link.updateLocally { it.copy(shuffle = !it.shuffle) }
				link.sendAsync(WearContract.CMD_SHUFFLE)
			}
			override fun repeat() = link.sendAsync(WearContract.CMD_REPEAT)
			override fun favorite() {
				link.updateLocally { it.copy(starred = !it.starred) }
				link.sendAsync(WearContract.CMD_FAVORITE)
			}
			override fun volume(up: Boolean) {
				link.updateLocally { it.copy(volume = (it.volume + if (up) 1 else -1).coerceIn(0, it.maxVolume)) }
				link.sendAsync(if (up) WearContract.CMD_VOLUME_UP else WearContract.CMD_VOLUME_DOWN)
			}
		},
		emptyText = if (connected) "Nothing is playing on the phone." else "Can't reach the phone. Check Bluetooth.",
	)
}

@Composable
private fun PlayerLayout(ui: PlayerUi, actions: PlayerActions, emptyText: String) {
	val focus = remember { FocusRequester() }
	var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
	LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

	ScreenScaffold {
		Box(
			modifier = Modifier
				.fillMaxSize()
				// The crown / bezel changes volume, like the system media controls.
				.onRotaryScrollEvent { event ->
					rotaryAccumulator += event.verticalScrollPixels
					if (kotlin.math.abs(rotaryAccumulator) > 48f) {
						actions.volume(up = rotaryAccumulator > 0)
						rotaryAccumulator = 0f
					}
					true
				}
				.focusRequester(focus)
				.focusable(),
			contentAlignment = Alignment.Center,
		) {
			when (val art = ui.artwork) {
				is android.graphics.Bitmap -> androidx.compose.foundation.Image(
					art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize().alpha(0.28f)
				)
				null -> Unit
				else -> AsyncImage(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.28f))
			}
			if (ui.durationMs > 0) {
				CircularProgressIndicator(
					progress = { (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) },
					modifier = Modifier.fillMaxSize().padding(2.dp),
					strokeWidth = 4.dp,
				)
			}
			if (!ui.hasItem) {
				Text(emptyText, textAlign = TextAlign.Center, modifier = Modifier.padding(28.dp))
				return@Box
			}
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
				modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp),
			) {
				Text(ui.deviceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
				Text(
					ui.title, maxLines = 1, style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.basicMarquee(), textAlign = TextAlign.Center
				)
				Text(
					ui.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.basicMarquee(), textAlign = TextAlign.Center
				)
				Spacer(Modifier.height(4.dp))
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					IconButton(onClick = actions::previous) { Icon(painterResource(R.drawable.ic_skip_previous), "Previous") }
					FilledIconButton(onClick = actions::toggle, modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize)) {
						Icon(
							painterResource(if (ui.playing) R.drawable.ic_pause else R.drawable.ic_play),
							if (ui.playing) "Pause" else "Play",
							modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
						)
					}
					IconButton(onClick = actions::next) { Icon(painterResource(R.drawable.ic_skip_next), "Next") }
				}
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					IconButton(onClick = actions::shuffle, modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)) {
						Icon(
							painterResource(if (ui.shuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle),
							if (ui.shuffle) "Shuffle on" else "Shuffle off",
							tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
						)
					}
					IconButton(
						onClick = actions::favorite, enabled = ui.canFavorite,
						modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)
					) {
						Icon(
							painterResource(if (ui.starred) R.drawable.ic_star_filled else R.drawable.ic_star),
							if (ui.starred) "Remove from favourites" else "Add to favourites",
							tint = if (ui.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
						)
					}
					IconButton(onClick = actions::repeat, modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)) {
						Icon(
							painterResource(
								when (ui.repeatMode) {
									Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_on
									Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
									else -> R.drawable.ic_repeat
								}
							),
							when (ui.repeatMode) {
								Player.REPEAT_MODE_ONE -> "Repeat one"
								Player.REPEAT_MODE_ALL -> "Repeat all"
								else -> "Repeat off"
							},
							tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
						)
					}
				}
				// Volume: these buttons, or turn the crown / bezel anywhere on this screen.
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					IconButton(onClick = { actions.volume(false) }, modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)) {
						Icon(painterResource(R.drawable.ic_volume_down), "Volume down", modifier = Modifier.size(IconButtonDefaults.SmallIconSize))
					}
					Text(
						ui.notice ?: if (ui.durationMs > 0) "${formatDuration(ui.positionMs)} / ${formatDuration(ui.durationMs)}" else ui.volumeLabel?.let { "vol $it" }.orEmpty(),
						style = MaterialTheme.typography.labelSmall,
						color = if (ui.notice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
						textAlign = TextAlign.Center,
						maxLines = 2,
						modifier = Modifier.widthIn(max = 96.dp),
					)
					IconButton(onClick = { actions.volume(true) }, modifier = Modifier.size(IconButtonDefaults.ExtraSmallButtonSize)) {
						Icon(painterResource(R.drawable.ic_volume_up), "Volume up", modifier = Modifier.size(IconButtonDefaults.SmallIconSize))
					}
				}
			}
		}
	}
}
