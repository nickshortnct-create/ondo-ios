package com.nickshortnct.ondo.ui.screens.nowPlaying.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.domain.manager.AudioOutputManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.screens.lyrics.components.LyricsScreenKaraokeText
import com.nickshortnct.ondo.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import com.nickshortnct.ondo.util.calculateWordProgress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A compact, synchronized lyric preview shown immediately below the cover art. */
@Composable
fun InlineNowPlayingLyrics(
	song: DomainSong?,
	position: Duration,
	modifier: Modifier = Modifier
) {
	val songToDisplay = song ?: return
	val preferenceManager = koinInject<PreferenceManager>()
	val audioOutputManager = koinInject<AudioOutputManager>()
	val bluetoothOutput by audioOutputManager.bluetoothOutputName.collectAsStateWithLifecycle()
	// Hidden on Bluetooth devices the user picked in settings (their delay puts synced lyrics out of time).
	if (bluetoothOutput?.let { it in preferenceManager.lyricsHiddenOutputNames() } == true) return
	val viewModel = koinViewModel<LyricsScreenViewModel>(
		key = "inline-lyrics",
		parameters = { parametersOf(songToDisplay) }
	)
	DisposableEffect(viewModel) { onDispose { viewModel.setSong(null) } }

	LaunchedEffect(songToDisplay?.id) { viewModel.setSong(songToDisplay) }
	val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
	val data = (lyricsState as? UiState.Success)?.data
	val lines = data?.lines?.filter { it.text.isNotBlank() } ?: emptyList()

	// The cover art above this shares a weighted column with this box, so a plain
	// appear/disappear here already resizes it - animating that in/out with a bouncy
	// spring is what makes the cover art bounce as it shrinks to make room for lyrics
	// instead of snapping to size instantly.
	val bounceSpec = spring<androidx.compose.ui.unit.IntSize>(
		dampingRatio = Spring.DampingRatioMediumBouncy,
		stiffness = Spring.StiffnessMediumLow
	)
	AnimatedVisibility(
		visible = lines.isNotEmpty(),
		modifier = modifier,
		enter = expandVertically(animationSpec = bounceSpec) + fadeIn(),
		exit = shrinkVertically(animationSpec = bounceSpec) + fadeOut()
	) {
		if (lines.isEmpty()) return@AnimatedVisibility
		// A positive offset advances the displayed lyric, correcting late lyric data.
		val adjustedPosition = position + preferenceManager.lyricsTimingOffsetMs.milliseconds
		val isSynced = data?.isSynced == true
		val rawActiveIndex = if (isSynced) {
			lines.indexOfLast { line -> line.time != null && adjustedPosition >= line.time }
		} else {
			0
		}
		val activeIndex = rawActiveIndex.coerceAtLeast(0)
		val active = lines.getOrNull(activeIndex) ?: return@AnimatedVisibility
		val nextTime = lines.getOrNull(activeIndex + 1)?.time ?: songToDisplay.duration
		val activeStart = active.time ?: Duration.ZERO
		val fallbackProgress = ((adjustedPosition - activeStart) /
			(nextTime - activeStart).coerceAtLeast(1.milliseconds))
			.toFloat()
			.coerceIn(0f..1f)
		val activeProgress = active.words
			?.takeIf { it.isNotEmpty() }
			?.calculateWordProgress(active.text, adjustedPosition)
			?: fallbackProgress

		BoxWithConstraints(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 2.dp)
		) {
			val textMeasurer = rememberTextMeasurer()
			val activeStyle = androidx.compose.material3.MaterialTheme.typography.headlineLargeEmphasized.copy(
				fontSize = 20.sp,
				lineHeight = 24.sp,
				fontWeight = FontWeight.Bold
			)
			val activeLineCount = textMeasurer.measure(
				text = AnnotatedString(active.text),
				style = activeStyle,
				constraints = Constraints(maxWidth = constraints.maxWidth)
			).lineCount
			val activeWraps = activeLineCount > 1

			// Fixed height (not a max) so 1-vs-2-line active lyrics and the presence/absence
			// of the upcoming-line preview never resize this box and reflow the screen above
			// mid-song; the AnimatedVisibility above handles the (animated) resize that
			// happens when lyrics appear/disappear entirely between songs.
			Column(Modifier.height(64.dp)) {
				LyricsScreenKaraokeText(
					text = active.text,
					progress = activeProgress,
					isActive = true,
					isSynced = isSynced,
					onClick = {},
					fontSize = 20.sp,
					lineHeight = 24.sp,
					maxLines = if (activeWraps) 2 else 1
				)
				if (!activeWraps) lines.getOrNull(activeIndex + 1)?.let { line ->
					LyricsScreenKaraokeText(
						text = line.text,
						progress = 0f,
						isActive = false,
						isSynced = isSynced,
						onClick = {},
						fontSize = 14.sp,
						lineHeight = 18.sp,
						maxLines = 1
					)
				}
			}
		}
	}
}
