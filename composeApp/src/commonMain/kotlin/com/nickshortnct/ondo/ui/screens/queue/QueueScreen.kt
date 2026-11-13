package com.nickshortnct.ondo.ui.screens.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.nickshortnct.ondo.ui.core.entryIds
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_clear_queue
import ondo.composeapp.generated.resources.count_songs
import ondo.composeapp.generated.resources.info_no_queue
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.PlaylistRemove
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.queue.components.QueueScreenItem
import com.nickshortnct.ondo.ui.screens.queue.viewmodels.QueueViewModel
import com.nickshortnct.ondo.di.LocalSheetState
import com.nickshortnct.ondo.ui.util.draggableItemsIndexed
import com.nickshortnct.ondo.ui.util.rememberDraggableListState
import kotlin.time.DurationUnit

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen() {
	val viewModel = koinViewModel<QueueViewModel>()
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
	val queue = playerState.queue
	val entryIds = remember(queue, playerState.queueEntryIds) { playerState.entryIds() }

	val haptic = LocalHapticFeedback.current
	val draggableState = rememberDraggableListState(viewModel.listState) { from, to ->
		player.moveQueueItem(from, to)
		haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
	}

	var previousPlayingIndex by remember { mutableIntStateOf(-1) }
	LaunchedEffect(playerState.currentIndex) {
		val list = draggableState.listState
		val previouslyVisible = list.layoutInfo.visibleItemsInfo.any { it.index == previousPlayingIndex }
		if (previousPlayingIndex >= 0 && previouslyVisible && !list.isScrollInProgress && playerState.currentIndex >= 0) {
			list.animateScrollToItem(playerState.currentIndex)
		}
		previousPlayingIndex = playerState.currentIndex
	}

	val totalDurationText = remember(queue) {
		val totalSeconds = queue.sumOf { it.duration.toInt(DurationUnit.SECONDS) }

		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		buildString {
			if (hours > 0) {
				append("${hours}h ")
			}

			if (minutes > 0 || hours > 0) {
				append("${minutes}m ")
			}

			append("${seconds}s")
		}
	}

	val songsText = pluralStringResource(
		Res.plurals.count_songs,
		queue.size,
		queue.size
	)

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.Queue)
			}
		}
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.clip(ContinuousRoundedRectangle(topStart = 16.dp, topEnd = 16.dp))
	) {
		if (queue.isNotEmpty()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 24.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "$songsText • $totalDurationText",
					style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				TextButton(
					onClick = {
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						player.clearQueue()
					},
					colors = ButtonDefaults.filledTonalButtonColors(),
					contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
					modifier = Modifier.height(36.dp)
				) {
					Text(stringResource(Res.string.action_clear_queue))
				}
			}
		}

		LazyColumn(
			modifier = Modifier
				.padding(horizontal = 12.dp)
				.fillMaxSize(),
			state = draggableState.listState,
			contentPadding = WindowInsets.systemBars
				.only(WindowInsetsSides.Bottom)
				.asPaddingValues(),
			verticalArrangement = if (queue.isNotEmpty())
				Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
			else Arrangement.Center
		) {
			draggableItemsIndexed(
				state = draggableState,
				items = queue,
				key = { index, _ -> entryIds[index] }
			) { index, song, isDragging ->
				QueueScreenItem(
					index = index,
					count = queue.count(),
					song = song,
					isPlaying = playerState.currentIndex == index
						&& !playerState.isPaused,
					isSelected = playerState.currentIndex == index,
					isDragging = isDragging,
					draggableState = draggableState,
					onClick = dropUnlessResumed {
						if (playerState.currentIndex != index) {
							player.playAt(index)
							animateToDismiss()
						}
					},
					onRemove = {
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						player.removeFromQueue(index)
					},
					isOffline = !isOnline,
					isDownloaded = (downloadedSongs?.containsKey(song.id) == true)
				)
			}
			if (queue.isEmpty()) {
				item {
					ContentUnavailable(
						icon = Icons.Outlined.PlaylistRemove,
						label = stringResource(Res.string.info_no_queue)
					)
				}
			}
		}
	}
}
