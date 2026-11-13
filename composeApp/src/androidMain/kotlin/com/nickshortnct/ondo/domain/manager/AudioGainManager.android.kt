package com.nickshortnct.ondo.domain.manager

import androidx.media3.common.util.UnstableApi
import com.nickshortnct.ondo.domain.models.DomainReplayGain
import com.nickshortnct.ondo.domain.models.settings.ReplayGainMode
import com.nickshortnct.ondo.exoplayer.AudioGainProcessor

/**
 * Settings-screen entry point to the gain processor. The playback service is the source of truth
 * (it watches the preferences and each track's metadata); these calls just apply a change
 * immediately, thread-safely.
 */
@UnstableApi
actual class AudioGainManager(
	private val audioGainProcessor: AudioGainProcessor
) {
	actual fun applyGainMode(mode: ReplayGainMode) {
		audioGainProcessor.update { it.copy(mode = mode) }
	}

	actual fun resetGain() {
		audioGainProcessor.update { it.copy(mode = ReplayGainMode.Off, replayGain = null) }
	}

	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {
		audioGainProcessor.update { it.copy(rgAmpDb = withReplayGain, ampDb = withoutReplayGain) }
	}

	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) {
		audioGainProcessor.update { it.copy(replayGain = metadata) }
	}
}
