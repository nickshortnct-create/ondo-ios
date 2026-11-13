package com.nickshortnct.ondo.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_configure_lyric_providers
import ondo.composeapp.generated.resources.action_lyrics
import ondo.composeapp.generated.resources.option_cover_art_action
import ondo.composeapp.generated.resources.option_lyrics_autoscroll
import ondo.composeapp.generated.resources.option_lyrics_beat_by_beat
import ondo.composeapp.generated.resources.option_lyrics_blur
import ondo.composeapp.generated.resources.option_lyrics_bright_inactive
import ondo.composeapp.generated.resources.option_lyrics_keep_alive
import ondo.composeapp.generated.resources.option_now_playing_background_style
import ondo.composeapp.generated.resources.option_now_playing_slider_style
import ondo.composeapp.generated.resources.option_now_playing_song_info
import ondo.composeapp.generated.resources.option_now_playing_toolbar_position
import ondo.composeapp.generated.resources.option_swipe_to_skip
import ondo.composeapp.generated.resources.subtitle_configure_lyric_providers
import ondo.composeapp.generated.resources.subtitle_now_playing_background_style
import ondo.composeapp.generated.resources.title_layout
import ondo.composeapp.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalPlatformContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickshortnct.ondo.domain.manager.AudioOutputManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.CoverArtTapAction
import com.nickshortnct.ondo.domain.models.settings.NowPlayingBackgroundStyle
import com.nickshortnct.ondo.domain.models.settings.ToolbarPosition
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem
import com.nickshortnct.ondo.ui.components.common.SegmentedListItemDefaults
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsChoiceItem
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroup
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroupDefaults
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsNavItem

import com.nickshortnct.ondo.ui.screens.settings.components.SettingsSliderItem
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsToggleItem
import com.nickshortnct.ondo.ui.screens.settings.dialogs.LyricsPrioritySheet
import com.nickshortnct.ondo.ui.screens.settings.dialogs.NowPlayingSliderStyleDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsNowPlayingScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val audioOutputManager = koinInject<AudioOutputManager>()
	var lyricProvidersSheetOpen by rememberSaveable { mutableStateOf(false) }
	var sliderStyleDialogOpen by rememberSaveable { mutableStateOf(false) }

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_now_playing)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup {
					SettingsToggleItem(
						checked = preferenceManager.swipeToSkip,
						onCheckedChange = { preferenceManager.swipeToSkip = it },
						content = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_cover_art_action)) },
						choices = CoverArtTapAction.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingCoverArtAction,
						onChoiceSelected = { preferenceManager.nowPlayingCoverArtAction = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_now_playing_background_style)) },
						description = stringResource(Res.string.subtitle_now_playing_background_style),
						choices = NowPlayingBackgroundStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingBackgroundStyle,
						onChoiceSelected = { preferenceManager.nowPlayingBackgroundStyle = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4)
					)
					SegmentedListItem(
						onClick = { sliderStyleDialogOpen = true },
						content = { Text(stringResource(Res.string.option_now_playing_slider_style)) },
						supportingContent = { Text(stringResource(preferenceManager.nowPlayingSliderStyle.displayName)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.action_lyrics)) }) {
					SettingsNavItem(
						onClick = { lyricProvidersSheetOpen = true },
						content = { Text(stringResource(Res.string.action_configure_lyric_providers)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_configure_lyric_providers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 7)
					)
					SettingsSliderItem(
						value = preferenceManager.lyricsTimingOffsetMs.toFloat(),
						valueRange = -5000f..5000f,
						steps = 99,
						onValueChange = {
							preferenceManager.lyricsTimingOffsetMs =
								(it / 100f).roundToInt() * 100
						},
						trailingContent = {
							val offset = preferenceManager.lyricsTimingOffsetMs
							Text("${if (offset > 0) "+" else ""}${offset / 1000f}s")
						},
						content = { Text("Lyrics timing offset") },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 7)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsAutoscroll,
						onCheckedChange = { preferenceManager.lyricsAutoscroll = it },
						content = { Text(stringResource(Res.string.option_lyrics_autoscroll)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 7)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBeatByBeat,
						onCheckedChange = { preferenceManager.lyricsBeatByBeat = it },
						content = { Text(stringResource(Res.string.option_lyrics_beat_by_beat)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 7)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsKeepAlive,
						onCheckedChange = { preferenceManager.lyricsKeepAlive = it },
						content = { Text(stringResource(Res.string.option_lyrics_keep_alive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 7)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBlur,
						onCheckedChange = { preferenceManager.lyricsBlur = it },
						content = { Text(stringResource(Res.string.option_lyrics_blur)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 7)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBrightInactive,
						onCheckedChange = { preferenceManager.lyricsBrightInactive = it },
						content = { Text(stringResource(Res.string.option_lyrics_bright_inactive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 6, count = 7)
					)
				}

				SettingsGroup(title = { Text("Hide lyrics on Bluetooth") }) {
					// Bluetooth delay (e.g. a car stereo) can put synced lyrics out of time, so the
					// lyric preview on Now Playing can be hidden per device. The Lyrics screen still works.
					val bluetoothOutput by audioOutputManager.bluetoothOutputName.collectAsStateWithLifecycle()
					val hiddenOn = preferenceManager.lyricsHiddenOutputNames()
					val devices = (hiddenOn + listOfNotNull(bluetoothOutput)).sorted()
					if (devices.isEmpty()) {
						SegmentedListItem(
							onClick = {},
							enabled = false,
							content = { Text("No Bluetooth device connected") },
							supportingContent = {
								Text("Connect to your car's Bluetooth, then come back here to hide the lyric preview while you're connected to it.")
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
						)
					}
					devices.forEachIndexed { index, device ->
						SettingsToggleItem(
							checked = device in hiddenOn,
							onCheckedChange = { preferenceManager.setLyricsHiddenOn(device, it) },
							content = { Text(device) },
							supportingContent = {
								Text(
									if (device == bluetoothOutput) "Connected now. Hides the lyrics under the cover art while connected."
									else "Hides the lyrics under the cover art while connected."
								)
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = devices.size)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_layout)) }) {
					SettingsToggleItem(
						checked = preferenceManager.nowPlayingSongInfo,
						onCheckedChange = { preferenceManager.nowPlayingSongInfo = it },
						content = { Text(stringResource(Res.string.option_now_playing_song_info)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_now_playing_toolbar_position)) },
						choices = ToolbarPosition.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingToolbarPosition,
						onChoiceSelected = { preferenceManager.nowPlayingToolbarPosition = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}

	LyricsPrioritySheet(
		presented = lyricProvidersSheetOpen,
		onDismissRequest = { lyricProvidersSheetOpen = false }
	)
	NowPlayingSliderStyleDialog(
		presented = sliderStyleDialogOpen,
		onDismissRequest = { sliderStyleDialogOpen = false }
	)
}
