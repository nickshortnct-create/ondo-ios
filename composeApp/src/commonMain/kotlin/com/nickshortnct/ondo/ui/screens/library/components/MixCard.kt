package com.nickshortnct.ondo.ui.screens.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_mixes_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.ondo.MixTag
import com.nickshortnct.ondo.ondo.displayComment
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.layouts.header
import com.nickshortnct.ondo.ui.navigation.Screen

/** A row of weekly mixes on the home screen: heading, a "picked by Claude" line, then the cards. */
fun LazyGridScope.mixSection(
	title: StringResource,
	mixes: List<Pair<DomainPlaylist, MixTag>>,
	showSubtitle: Boolean
) {
	if (mixes.isEmpty()) return
	header(title, destination = Screen.PlaylistList(true), active = false)
	if (showSubtitle) {
		item(span = { GridItemSpan(maxLineSpan) }) {
			Text(
				stringResource(Res.string.info_mixes_subtitle),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
			)
		}
	}
	item(span = { GridItemSpan(maxLineSpan) }) {
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = PaddingValues(horizontal = 16.dp)
		) {
			items(mixes, key = { it.first.id }) { (playlist, tag) ->
				MixCard(playlist = playlist, tag = tag, modifier = Modifier.animateItem().width(156.dp))
			}
		}
	}
}

@Composable
fun MixCard(playlist: DomainPlaylist, tag: MixTag, modifier: Modifier = Modifier) {
	val backStack = LocalNavStack.current
	val base = parseHexColor(tag.color)
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(14.dp))
			.clickable(onClick = dropUnlessResumed { backStack.add(Screen.CollectionDetail(playlist.id, "library")) })
	) {
		if (!playlist.customCoverPath.isNullOrBlank()) {
			// The curator's cover already carries the title.
			CoverArt(
				coverArtId = null,
				localImageUri = playlist.customCoverPath,
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth()
			)
		} else {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(1f)
					.clip(RoundedCornerShape(14.dp))
					.background(
						Brush.linearGradient(listOf(base, lerpColor(base, Color.Black, 0.55f)))
					)
					.padding(12.dp)
			) {
				Text(tag.emoji.ifBlank { "🎶" }, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopStart))
				Text(
					playlist.name,
					color = Color.White,
					fontWeight = FontWeight.Bold,
					style = MaterialTheme.typography.titleMedium,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.align(Alignment.BottomStart)
				)
			}
		}
		playlist.displayComment?.let {
			Text(
				it,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
			)
		}
	}
}

internal fun parseHexColor(hex: String): Color =
	hex.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Color(0xFF7C4DFF)

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
	red = a.red + (b.red - a.red) * t,
	green = a.green + (b.green - a.green) * t,
	blue = a.blue + (b.blue - a.blue) * t,
	alpha = 1f
)
