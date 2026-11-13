package com.nickshortnct.ondo.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.shared.MediaPlayerViewModel

@Composable
fun NowPlayingTechnicalInfoRow() {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.Center
	) {
		NowPlayingTechnicalInfoPill()
	}
}

/** Format, sample rate and bitrate in a soft pill; also used inline in the durations row. */
@Composable
fun NowPlayingTechnicalInfoPill(modifier: Modifier = Modifier) {
	val preferenceManager = koinInject<PreferenceManager>()
	val connectivityManager = koinInject<ConnectivityManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong

	val style = MaterialTheme.typography.bodySmall
	val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)

	Box(modifier = modifier, contentAlignment = Alignment.Center) {

		Box(
			modifier = Modifier
				.matchParentSize()
				.clip(CircleShape)
				.blur(8.dp)
				.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
		)

		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			val sampleRateFormatted =
				(playerState.playbackSampleRate ?: song?.sampleRate)?.let {
					if (it >= 1000) "${it / 1000.0} kHz" else "$it Hz"
				} ?: "-- kHz"

			val isCellular = connectivityManager.isCellular.value
			val requestedBitrate = if (preferenceManager.isAdvancedTranscodingActive) {
				if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
			} else {
				if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
			}

			val bitrateFormatted = playerState.playbackBitrate?.let { "${it / 1000} kbps" }
				?: if (requestedBitrate > 0) {
					"$requestedBitrate kbps"
				} else {
					song?.bitRate?.let { "$it kbps" }
				} ?: "-- kbps"

			val format =
				playerState.playbackMimeType?.split("/")?.lastOrNull()?.replace("mpeg", "mp3")
					?.uppercase()
					?: song?.fileExtension?.uppercase()
					?: "--"

			// While another Ondo Connect device plays, show what that device streams
			// (its snapshot fills the same fields) and which device it is.
			val playingOn = com.nickshortnct.ondo.ondo.connectPlayingOnName()
			val remoteQuality = listOfNotNull(
				playerState.playbackMimeType?.substringAfter('/')?.uppercase(),
				playerState.playbackSampleRate?.let { if (it >= 1000) "${it / 1000.0} kHz" else "$it Hz" },
				playerState.playbackBitrate?.let { "${it / 1000} kbps" }
			).joinToString(" • ")
			Text(
				text = when {
					playingOn == null -> "$format • $sampleRateFormatted • $bitrateFormatted"
					remoteQuality.isNotEmpty() -> "$remoteQuality • $playingOn"
					else -> "Playing on $playingOn"
				},
				style = style,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = if (playingOn != null) com.nickshortnct.ondo.ondo.ConnectGreen else color
			)
		}
	}
}
