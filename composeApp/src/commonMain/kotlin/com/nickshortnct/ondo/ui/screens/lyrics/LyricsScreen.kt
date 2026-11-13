package com.nickshortnct.ondo.ui.screens.lyrics

import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.settings.ToolbarPosition
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.ErrorBox
import com.nickshortnct.ondo.ui.util.KeepScreenOn
import com.nickshortnct.ondo.ui.components.layouts.SheetScaffold
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.lyrics.components.LyricsScreenContent
import com.nickshortnct.ondo.ui.screens.lyrics.components.LyricsScreenLoadingView
import com.nickshortnct.ondo.ui.screens.lyrics.components.LyricsScreenPlaceholder
import com.nickshortnct.ondo.ui.screens.lyrics.components.LyricsScreenToolbar
import com.nickshortnct.ondo.ui.screens.lyrics.dialogs.LyricsShareSheet
import com.nickshortnct.ondo.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import com.nickshortnct.ondo.di.LocalSheetState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
	song: DomainSong?
) {
	val backStack = LocalNavStack.current

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val viewModel = koinViewModel<LyricsScreenViewModel>(
		key = "lyrics-screen",
		parameters = { parametersOf(song) }
	)
	DisposableEffect(viewModel) { onDispose { viewModel.setSong(null) } }

	LaunchedEffect(song?.id) { viewModel.setSong(song) }
	val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

	var isSelecting by rememberSaveable { mutableStateOf(false) }
	val selectedIndices = rememberSaveable { mutableStateListOf<Int>() }
	var wasPlayingBeforeSelection by rememberSaveable { mutableStateOf(false) }
	var shareSheetOpen by rememberSaveable { mutableStateOf(false) }

	val song = song ?: return LyricsScreenPlaceholder(
		onRefresh = { viewModel.refreshResults() }
	)
	val duration = song.duration
	val progressState = playerState.progress
	val preferenceManager = koinInject<PreferenceManager>()
	// Positive offset moves a lyric forward, which corrects lyric sources that run late.
	val currentDuration = duration * progressState.toDouble() +
		preferenceManager.lyricsTimingOffsetMs.milliseconds

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val toggleIsSelecting = {
		if (isSelecting) {
			isSelecting = false
			selectedIndices.clear()
			if (wasPlayingBeforeSelection) {
				player.resume()
			}
		} else {
			wasPlayingBeforeSelection = !playerState.isPaused
			player.pause()
			isSelecting = true
		}
	}

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.Lyrics)
			}
		}
	}

	if (preferenceManager.lyricsKeepAlive) {
		KeepScreenOn()
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			LyricsScreenToolbar(
				onDismissRequest = { animateToDismiss() },
				onShare = { shareSheetOpen = true },
				isSelecting = isSelecting,
				toggleIsSelecting = toggleIsSelecting,
				windowInsets = windowInsets,
				selectedIndices = selectedIndices.toImmutableList()
			)
		},
		toolbarPosition = ToolbarPosition.Top
	) { contentPadding ->
		AnimatedContent(
			targetState = lyricsState,
			modifier = Modifier.fillMaxSize(),
			transitionSpec = {
				ContentTransform(
					targetContentEnter = fadeIn(effectSpec) + scaleIn(spatialSpec, 0.8f),
					initialContentExit = fadeOut(effectSpec) + scaleOut(spatialSpec)
				)
			},
		) { lyricsState ->
			when (lyricsState) {
				is UiState.Error -> ErrorBox(
					error = lyricsState,
					modifier = Modifier.wrapContentSize(),
					onRetry = { viewModel.refreshResults() }
				)

				is UiState.Loading -> LyricsScreenLoadingView()
				is UiState.Success -> {
					LyricsScreenContent(
						data = lyricsState.data,
						onRefresh = { viewModel.refreshResults() },
						isSelecting = isSelecting,
						selectedIndices = selectedIndices.toImmutableList(),
						onAddSelectedIndex = { idx -> selectedIndices.add(idx) },
						onRemoveSelectedIndex = { idx -> selectedIndices.remove(idx) },
						onRestartAtIndex = { idx ->
							selectedIndices.clear()
							selectedIndices.add(idx)
						},
						duration = duration,
						currentDuration = currentDuration,
						contentPadding = contentPadding
					)
				}
			}
		}

		if (shareSheetOpen) {
			val lyricsList = lyricsState.data?.lines?.map { line ->
				(line.time?.inWholeMilliseconds ?: 0L) to line.text
			}

			if (lyricsList != null) {
				val sortedIndices = selectedIndices.sorted()
				val stringsToShare = sortedIndices.mapNotNull { index ->
					lyricsList.getOrNull(index)?.second
				}.toImmutableList()

				LyricsShareSheet(
					song = song,
					selectedLyrics = stringsToShare,
					onDismiss = { shareSheetOpen = false },
					onShare = {
						shareSheetOpen = false
						isSelecting = false
						selectedIndices.clear()
					}
				)
			}
		}
	}
}
