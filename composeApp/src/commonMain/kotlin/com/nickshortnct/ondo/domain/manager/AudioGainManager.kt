package com.nickshortnct.ondo.domain.manager

import com.nickshortnct.ondo.domain.models.DomainReplayGain
import com.nickshortnct.ondo.domain.models.settings.ReplayGainMode

expect class AudioGainManager {
	fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float)
	fun setReplayGainMetadata(metadata: DomainReplayGain?)
	fun applyGainMode(mode: ReplayGainMode)
	fun resetGain()
}
