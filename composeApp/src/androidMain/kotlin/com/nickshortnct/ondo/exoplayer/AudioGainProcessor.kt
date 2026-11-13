package com.nickshortnct.ondo.exoplayer

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.nickshortnct.ondo.domain.models.DomainReplayGain
import com.nickshortnct.ondo.domain.models.settings.ReplayGainMode
import com.nickshortnct.ondo.util.decibelsToLinear
import com.nickshortnct.ondo.util.effectiveGain
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies ReplayGain and the user's amplifier settings on the audio thread.
 *
 * Settings and per-track metadata are written from other threads as one immutable [GainState];
 * the audio thread reads a snapshot in [queueInput] and ramps to the target over a few
 * milliseconds. Nothing here ever calls `flush()` from a foreign thread (that corrupted buffers),
 * and the target is computed where it is used, so a track change cannot play at the previous
 * track's gain.
 */
@UnstableApi
class AudioGainProcessor : BaseAudioProcessor() {
	data class GainState(
		val mode: ReplayGainMode = ReplayGainMode.Off,
		/** Extra amplification (dB) used while ReplayGain data applies. */
		val rgAmpDb: Float = 0f,
		/** Extra amplification (dB) used otherwise. */
		val ampDb: Float = 0f,
		val replayGain: DomainReplayGain? = null,
		/** True when the queue is a single album, so Dynamic mode picks album gain. */
		val albumContext: Boolean = false
	) {
		/** The processor only needs to sit in the pipeline when something can change the gain. */
		val isEnabled: Boolean get() = mode != ReplayGainMode.Off || rgAmpDb != 0f || ampDb != 0f

		fun targetLinear(): Float {
			val metadata = if (mode != ReplayGainMode.Off) replayGain else null
			val effectiveMode = when (mode) {
				ReplayGainMode.Dynamic -> if (albumContext) ReplayGainMode.Album else ReplayGainMode.Track
				else -> mode
			}
			val gainDb = metadata?.effectiveGain(effectiveMode) ?: 0f
			// Without usable ReplayGain data the plain amplifier applies; with it, the RG amplifier does.
			val db = if (metadata != null && gainDb != 0f) gainDb + rgAmpDb else ampDb
			return db.decibelsToLinear()
		}
	}

	@Volatile
	var state = GainState()
		private set

	fun update(transform: (GainState) -> GainState) {
		synchronized(this) { state = transform(state) }
	}

	// Audio-thread only below.
	private var appliedGain = 1f
	private var targetGain = 1f
	private var rampStep = 0f
	private var rampRemaining = 0

	override fun isActive(): Boolean = super.isActive() && state.isEnabled

	override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
		if (inputAudioFormat.encoding == AudioFormat.ENCODING_PCM_16BIT) {
			return inputAudioFormat
		}
		throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
	}

	override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
		// A fresh stream (seek, new item) starts at its target; no audible ramp needed.
		targetGain = state.targetLinear()
		appliedGain = targetGain
		rampRemaining = 0
	}

	override fun queueInput(inputBuffer: ByteBuffer) {
		val size = inputBuffer.remaining()
		val outputBuffer = replaceOutputBuffer(size)

		val newTarget = state.targetLinear()
		if (newTarget != targetGain) {
			targetGain = newTarget
			// ~50 ms ramp across all channels' samples to avoid a click when the gain changes.
			val rampSamples = (inputAudioFormat.sampleRate * inputAudioFormat.channelCount * RAMP_SECONDS)
				.toInt().coerceAtLeast(1)
			rampRemaining = rampSamples
			rampStep = (targetGain - appliedGain) / rampSamples
		}

		inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
		outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

		if (rampRemaining == 0 && appliedGain == 1f) {
			outputBuffer.put(inputBuffer)
		} else {
			val input = inputBuffer.asShortBuffer()
			val output = outputBuffer.asShortBuffer()
			while (input.hasRemaining()) {
				if (rampRemaining > 0) {
					appliedGain += rampStep
					if (--rampRemaining == 0) appliedGain = targetGain
				}
				val scaled = (input.get() * appliedGain)
					.toInt()
					.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
					.toShort()
				output.put(scaled)
			}
			inputBuffer.position(inputBuffer.position() + size)
			outputBuffer.position(outputBuffer.position() + size)
		}
		outputBuffer.flip()
	}

	private companion object {
		const val RAMP_SECONDS = 0.05f
	}
}
