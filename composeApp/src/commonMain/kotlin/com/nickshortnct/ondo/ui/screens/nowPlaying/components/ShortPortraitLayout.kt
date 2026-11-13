package com.nickshortnct.ondo.ui.screens.nowPlaying.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Height InlineNowPlayingLyrics takes when it's showing: a fixed 64dp box plus 2dp padding above and below. */
private val InlineLyricsSlot = 68.dp

/**
 * Now Playing for short portrait spaces: the Z Fold8 cover screen, or one pane of a
 * three-app split on the inner screen. Top to bottom it stacks [links], [artwork],
 * [lyrics] and [controls]. The controls keep their natural height and the artwork takes
 * what's left, but never less than a minimum size: if the lyric preview would squeeze the
 * artwork below it, the preview is left out (it's still one tap away on the Lyrics
 * button), and if that isn't enough, the Downloads/Upgrades/Integrations links go too.
 */
@Composable
fun ShortPortraitNowPlayingLayout(
	modifier: Modifier = Modifier,
	links: @Composable () -> Unit,
	artwork: @Composable () -> Unit,
	lyrics: @Composable () -> Unit,
	controls: @Composable () -> Unit
) {
	Layout(
		contents = listOf(links, artwork, lyrics, controls),
		modifier = modifier
	) { (linkMeasurables, artMeasurables, lyricMeasurables, controlMeasurables), constraints ->
		val width = constraints.maxWidth
		val height = constraints.maxHeight
		val loose = Constraints(maxWidth = width, maxHeight = height)

		val controlPlaceables = controlMeasurables.map { it.measure(loose) }
		val controlsHeight = controlPlaceables.sumOf { it.height }
		val linkPlaceables = linkMeasurables.map { it.measure(loose) }
		val linksHeight = linkPlaceables.sumOf { it.height }
		// Decide with the lyric box's full height rather than its measured one, so the
		// expand/shrink animation between songs can't flip the decision mid-animation.
		val hasLyrics = lyricMeasurables.isNotEmpty()
		val lyricsSlot = if (hasLyrics) InlineLyricsSlot.roundToPx() else 0

		val minArtwork = min(width * 0.45f, 200.dp.toPx()).roundToInt()
		val showLyrics = hasLyrics && height - controlsHeight - linksHeight - lyricsSlot >= minArtwork
		val showLinks = height - controlsHeight - linksHeight - (if (showLyrics) lyricsSlot else 0) >= minArtwork

		val shownLinks = if (showLinks) linkPlaceables else emptyList()
		val lyricPlaceables = if (showLyrics) lyricMeasurables.map { it.measure(loose) } else emptyList()
		val artworkHeight = max(
			0,
			height - controlsHeight - shownLinks.sumOf { it.height } - lyricPlaceables.sumOf { it.height }
		)
		val artPlaceables = artMeasurables.map {
			it.measure(Constraints.fixed(width, artworkHeight))
		}

		layout(width, height) {
			var y = 0
			fun place(placeables: List<Placeable>) {
				placeables.forEach {
					it.placeRelative((width - it.width) / 2, y)
					y += it.height
				}
			}
			place(shownLinks)
			place(artPlaceables)
			place(lyricPlaceables)
			place(controlPlaceables)
		}
	}
}
