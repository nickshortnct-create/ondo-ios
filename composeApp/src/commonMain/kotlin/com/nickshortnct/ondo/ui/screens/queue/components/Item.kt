package com.nickshortnct.ondo.ui.screens.queue.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_remove_from_queue
import ondo.composeapp.generated.resources.action_reorder
import ondo.composeapp.generated.resources.info_explicit
import ondo.composeapp.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Delete
import com.nickshortnct.ondo.icons.outlined.DragHandle
import com.nickshortnct.ondo.icons.outlined.Lock
import com.nickshortnct.ondo.icons.outlined.Offline
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.common.MarqueeText
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem
import com.nickshortnct.ondo.ui.components.common.SegmentedListItemDefaults
import com.nickshortnct.ondo.ui.components.common.Waveform
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.util.DraggableListState
import com.nickshortnct.ondo.ui.util.buildSongInfoString
import com.nickshortnct.ondo.ui.util.dragHandle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QueueScreenItem(
	index: Int,
	count: Int,
	song: DomainSong,
	isPlaying: Boolean,
	isSelected: Boolean,
	isDragging: Boolean,
	draggableState: DraggableListState,
	onClick: () -> Unit,
	onRemove: () -> Unit,
	isOffline: Boolean = false,
	isDownloaded: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = isOffline && !isDownloaded

	val elevation by animateDpAsState(
		targetValue = if (isDragging) 8.dp else 0.dp,
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
	)

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val itemShape = SegmentedListItemDefaults.segmentedShapes(
		index = index,
		count = count,
		dismissDirection = dismissState.dismissDirection
	)

	val backStack = LocalNavStack.current

	SwipeToDismissBox(
		state = dismissState,
		onDismiss = {
			onRemove()
			scope.launch {
				dismissState.reset()
			}
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(itemShape.shape)
					.background(MaterialTheme.colorScheme.errorContainer)
					.padding(horizontal = 20.dp)
			) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = stringResource(Res.string.action_remove_from_queue),
					tint = MaterialTheme.colorScheme.onErrorContainer,
					modifier = Modifier.align(
						when (dismissState.dismissDirection) {
							SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
							else -> Alignment.CenterEnd
						}
					)
				)
			}
		},
		content = {
			Surface(
				shadowElevation = elevation,
				shape = itemShape.shape
			) {
				SegmentedListItem(
					onClick = onClick,
					enabled = !isExplicit,
					selected = isSelected,
					colors = SegmentedListItemDefaults.segmentedColors(
						selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
						selectedContentColor = MaterialTheme.colorScheme.primary,
						selectedSupportingContentColor = MaterialTheme.colorScheme.primary
							.copy(alpha = .7f)
					),
					shapes = itemShape,
					verticalAlignment = Alignment.CenterVertically,
					content = { MarqueeText(song.title) },
					supportingContent = {
						MarqueeText(
							buildSongInfoString(
								song = song,
								onClickArtist = {
									backStack.remove(Screen.Queue)
									backStack.remove(Screen.NowPlaying)
									backStack.add(Screen.ArtistDetail(it))
								}
							)
						)
					},
					leadingContent = {
						CoverArt(
							modifier = Modifier.size(48.dp),
							coverArtId = song.coverArtId,
							shape = ContinuousRoundedRectangle(10.dp)
						)
					},
					trailingContent = {
						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							if (isExplicit) {
								Icon(
									Icons.Outlined.Lock,
									stringResource(Res.string.info_explicit),
									modifier = Modifier.size(20.dp)
								)
							}
							if (maybeUnavailable) {
								Icon(
									Icons.Outlined.Offline,
									stringResource(Res.string.info_not_available_offline),
									modifier = Modifier.size(20.dp)
								)
							}
							if (isSelected) {
								Waveform(isPlaying = isPlaying)
							}
							IconButton(
								modifier = Modifier.dragHandle(
									state = draggableState,
									index = index
								),
								onClick = {}
							) {
								Icon(
									Icons.Outlined.DragHandle,
									contentDescription = stringResource(Res.string.action_reorder)
								)
							}
						}
					},
					contentPadding = PaddingValues(10.dp)
				)
			}
		}
	)
}
