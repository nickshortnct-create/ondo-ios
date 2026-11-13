package com.nickshortnct.ondo.ui.screens.nowPlaying.components.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.CoverArtTapAction
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.nowPlaying.components.NowPlayingArtwork
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NowPlayingArtworkPager(
	modifier: Modifier = Modifier,
	isLandscape: Boolean
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()

	val pagerState = rememberPagerState(
		initialPage = playerState.currentIndex.coerceAtLeast(0),
		pageCount = { playerState.queue.size }
	)
	val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
	var userSwiped by remember { mutableStateOf(false) }

	LaunchedEffect(isDragged) {
		if (isDragged) userSwiped = true
	}

	var visible by rememberSaveable { mutableStateOf(false) }
	val scale by animateFloatAsState(if (visible) 1f else 0f)
	val offset by animateDpAsState(if (visible) 0.dp else 200.dp)

	LaunchedEffect(Unit) {
		delay(50.milliseconds)
		visible = true
	}

	LaunchedEffect(playerState.currentIndex) {
		val target = playerState.currentIndex
		if (isDragged || target !in 0 until pagerState.pageCount || target == pagerState.currentPage) {
			return@LaunchedEffect
		}
		// animateScrollToPage reads the last visible page, which throws when the pager
		// has no laid-out pages (e.g. while the queue is being replaced).
		if (pagerState.layoutInfo.visiblePagesInfo.isEmpty()) {
			pagerState.scrollToPage(target)
		} else {
			try {
				pagerState.animateScrollToPage(target)
			} catch (_: NoSuchElementException) {
				if (target in 0 until pagerState.pageCount) pagerState.scrollToPage(target)
			}
		}
	}

	LaunchedEffect(pagerState) {
		snapshotFlow { pagerState.settledPage }.collect { page ->
			if (userSwiped && page != playerState.currentIndex && page in playerState.queue.indices) {
				val wasPaused = playerState.isPaused
				player.playAt(page)
				if (wasPaused) {
					player.pause()
				}
			}
			userSwiped = false
		}
	}

	HorizontalPager(
		modifier = modifier.scale(scale).offset {
			IntOffset(x = 0, y = offset.roundToPx())
		},
		state = pagerState,
		contentPadding = PaddingValues(horizontal = if (isLandscape) 0.dp else 8.dp),
		userScrollEnabled = preferenceManager.swipeToSkip,
		overscrollEffect = null
	) { page ->
		val song = playerState.queue.getOrNull(page) ?: return@HorizontalPager
		val tapAction = preferenceManager.nowPlayingCoverArtAction
		val enabled = pagerState.settledPage == page
			&& tapAction != CoverArtTapAction.Disabled
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			NowPlayingArtwork(
				song = song,
				isLandscape = isLandscape,
				onClick = if (enabled) dropUnlessResumed {
					when (tapAction) {
						CoverArtTapAction.ShowLyrics -> backStack.add(Screen.Lyrics)
						CoverArtTapAction.Disabled -> {}
					}
				} else null
			)
		}
	}
}
