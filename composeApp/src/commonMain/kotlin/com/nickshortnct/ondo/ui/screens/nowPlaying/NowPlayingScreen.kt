package com.nickshortnct.ondo.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_lyrics
import ondo.composeapp.generated.resources.action_navigate_back
import ondo.composeapp.generated.resources.action_queue
import ondo.composeapp.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.NowPlayingBackgroundStyle
import com.nickshortnct.ondo.domain.models.settings.ToolbarPosition
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Devices
import com.nickshortnct.ondo.icons.outlined.KeyboardArrowDown
import com.nickshortnct.ondo.icons.outlined.List
import com.nickshortnct.ondo.icons.outlined.Lyrics
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.BlendBackground
import com.nickshortnct.ondo.ui.components.layouts.SheetScaffold
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton
import com.nickshortnct.ondo.ui.components.toolbars.SheetActionButton
import com.nickshortnct.ondo.ui.components.toolbars.SheetToolbar
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.controls.NowPlayingArtworkPager
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.InlineNowPlayingLyrics
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.ShortPortraitNowPlayingLayout
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.rows.NowPlayingControlsRow
import com.nickshortnct.ondo.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import com.nickshortnct.ondo.di.LocalSheetState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current

	val currentScreen = backStack.lastOrNull()
	val isPlayerCurrent = currentScreen is Screen.NowPlaying
		|| currentScreen is Screen.Queue
		|| currentScreen is Screen.PlaybackSpeed
		|| currentScreen is Screen.SongDetailSheet

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val song = playerState.currentSong

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()
	val songRating by viewModel.songRating.collectAsStateWithLifecycle()

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = dropUnlessResumed {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.NowPlaying)
			}
		}
	}

	var devicesOpen by remember { mutableStateOf(false) }
	val playingElsewhere = com.nickshortnct.ondo.ondo.connectPlayingElsewhere()
	if (devicesOpen) {
		com.nickshortnct.ondo.ondo.ConnectDeviceSheet(
			onDismiss = { devicesOpen = false },
			onOpenSettings = { backStack.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Connect)) }
		)
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			SheetToolbar(
				modifier = Modifier.alpha(if (isPlayerCurrent) 1f else 0f),
				windowInsets = windowInsets,
				title = {
					Text(stringResource(Res.string.title_now_playing))
				},
				navigationIcon = {
					TopBarButton(
						onClick = animateToDismiss,
						content = {
							Icon(
								imageVector = Icons.Outlined.KeyboardArrowDown,
								contentDescription = stringResource(Res.string.action_navigate_back)
							)
						}
					)
				},
				actions = {
					// Ondo Connect: green while music plays on another device.
					SheetActionButton(
						icon = Icons.Outlined.Devices,
						contentDescription = "Devices",
						onClick = { devicesOpen = true },
						isStartRounded = true,
						tint = if (playingElsewhere) com.nickshortnct.ondo.ondo.ConnectGreen else null
					)
					SheetActionButton(
						icon = Icons.Outlined.Lyrics,
						contentDescription = stringResource(Res.string.action_lyrics),
						onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) }
					)
					SheetActionButton(
						icon = Icons.Outlined.List,
						contentDescription = stringResource(Res.string.action_queue),
						onClick = dropUnlessResumed { backStack.add(Screen.Queue) },
						isEndRounded = true
					)
				}
			)
		}
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			if (preferenceManager.nowPlayingBackgroundStyle
				== NowPlayingBackgroundStyle.Dynamic
			) {
				BlendBackground(
					coverArtId = song?.coverArtId,
					isPaused = playerState.isPaused
				)
			}
			if (!isPlayerCurrent) return@Box
			BoxWithConstraints(
				modifier = Modifier
					.padding(horizontal = 8.dp)
					.fillMaxSize()
			) {
				val isLandscape = maxWidth > maxHeight
				// In landscape a phone turned sideways is too short for the lyric preview and
				// the controls together, so only taller wide screens (foldables, tablets) get it.
				val showLandscapeLyrics = maxHeight >= 480.dp
				// Wide, short portrait spaces (the Z Fold8 cover screen is 475x751dp, or a pane of
				// a three-app split) can't fit the weighted artwork/controls split; see
				// ShortPortraitNowPlayingLayout.
				val isShortPortrait = !isLandscape && maxHeight < maxWidth * 1.8f
				val toolbarPosition = preferenceManager.nowPlayingToolbarPosition
				val padding = when {
					isLandscape -> contentPadding
					toolbarPosition == ToolbarPosition.Top -> contentPadding.plus(
						PaddingValues(
							bottom = if (isShortPortrait) 16.dp else 40.dp
						)
					)

					toolbarPosition == ToolbarPosition.Bottom -> contentPadding.plus(
						PaddingValues(
							top = if (isShortPortrait) 16.dp else 40.dp
						)
					)

					else -> contentPadding
				}
				if (isLandscape) {
					Row(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically
					) {
						NowPlayingArtworkPager(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							isLandscape = true
						)
						// Wide screens (foldable inner displays, tablets) put the synced lyric
						// preview above the controls, mirroring art -> lyrics -> controls in portrait.
						Column(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							horizontalAlignment = Alignment.CenterHorizontally,
							verticalArrangement = Arrangement.Center
						) {
							if (showLandscapeLyrics) InlineNowPlayingLyrics(
								song = song,
								position = song?.duration?.times(playerState.progress.toDouble())
									?: kotlin.time.Duration.ZERO,
								modifier = Modifier.padding(bottom = 16.dp)
							)
							NowPlayingControlsRow(
								isLandscape = true,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) },
								songRating = songRating,
								onSetSongRating = { viewModel.rateSong(it) }
							)
						}
					}
				} else if (isShortPortrait) {
					ShortPortraitNowPlayingLayout(
						modifier = Modifier.fillMaxSize().padding(padding),
						links = { com.nickshortnct.ondo.ondo.OndoLinks() },
						artwork = {
							NowPlayingArtworkPager(
								modifier = Modifier.fillMaxSize(),
								isLandscape = false
							)
						},
						lyrics = {
							InlineNowPlayingLyrics(
								song = song,
								position = song?.duration?.times(playerState.progress.toDouble())
									?: kotlin.time.Duration.ZERO
							)
						},
						controls = {
							NowPlayingControlsRow(
								modifier = Modifier.padding(top = 8.dp),
								isLandscape = false,
								compact = true,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) },
								songRating = songRating,
								onSetSongRating = { viewModel.rateSong(it) }
							)
						}
					)
				} else {
					Column(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
                        com.nickshortnct.ondo.ondo.OndoLinks()
						NowPlayingArtworkPager(
							modifier = Modifier.weight(1.45f).fillMaxWidth(),
							isLandscape = false
						)
						InlineNowPlayingLyrics(
							song = song,
							position = song?.duration?.times(playerState.progress.toDouble())
								?: kotlin.time.Duration.ZERO
						)
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f),
							isLandscape = false,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) },
							songRating = songRating,
							onSetSongRating = { viewModel.rateSong(it) }
						)
					}
				}
			}
		}
	}
}
