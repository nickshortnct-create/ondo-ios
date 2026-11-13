package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.domain.models.DomainReplayGain
import com.nickshortnct.ondo.domain.models.settings.ReplayGainMode

actual class AudioGainManager {
	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {
	}

	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) {
	}

	actual fun applyGainMode(mode: ReplayGainMode) {
	}

	actual fun resetGain() {
	}

}
