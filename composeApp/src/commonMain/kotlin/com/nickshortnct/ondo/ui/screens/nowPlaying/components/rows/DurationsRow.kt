package com.nickshortnct.ondo.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.util.toHoursMinutesSeconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun NowPlayingDurationsRow(center: (@Composable () -> Unit)? = null) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val duration = playerState.currentSong?.duration
	val style = MaterialTheme.typography.bodyMedium
		.copy(
			shadow = Shadow(
				color = MaterialTheme.colorScheme.inverseOnSurface,
				offset = Offset(0f, 4f),
				blurRadius = 10f
			)
		)
	val color = MaterialTheme.colorScheme.onSurfaceVariant
	val middle: @Composable RowScope.() -> Unit = {
		if (center != null) {
			Box(
				modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
				contentAlignment = Alignment.Center
			) { center() }
		} else {
			Spacer(Modifier.weight(1f))
		}
	}
	Row(
		modifier = Modifier.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		when {
			duration == kotlin.time.Duration.ZERO -> {
				Text(text = "LIVE", color = color, style = style)
				middle()
				Text(text = "∞", color = color, style = style)
			}

			duration != null -> {
				Text(
					text = ((duration.inWholeSeconds * playerState.progress).toDouble().seconds).toHoursMinutesSeconds(),
					color = color, style = style
				)
				middle()
				Text(duration.toHoursMinutesSeconds(), color = color, style = style)
			}

			else -> {
				Text("--:--", color = color, style = style)
				middle()
				Text("--:--", color = color, style = style)
			}
		}
	}
}
