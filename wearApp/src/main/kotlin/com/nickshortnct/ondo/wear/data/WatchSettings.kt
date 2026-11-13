package com.nickshortnct.ondo.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PlayTarget { Watch, Phone }

class WatchSettings(context: Context) {
	private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

	private val _playTarget = MutableStateFlow(
		runCatching { PlayTarget.valueOf(prefs.getString("playTarget", PlayTarget.Phone.name)!!) }.getOrDefault(PlayTarget.Phone)
	)
	/** Where library selections play: through the watch's own player, or on the phone. */
	val playTarget: StateFlow<PlayTarget> get() = _playTarget

	private val _streamBitrate = MutableStateFlow(prefs.getInt("streamBitrate", 192))
	/** kbps for watch streaming and downloads; 0 = original quality. */
	val streamBitrate: StateFlow<Int> get() = _streamBitrate

	fun setPlayTarget(target: PlayTarget) {
		prefs.edit().putString("playTarget", target.name).apply()
		_playTarget.value = target
	}

	fun setStreamBitrate(kbps: Int) {
		prefs.edit().putInt("streamBitrate", kbps).apply()
		_streamBitrate.value = kbps
	}

	companion object {
		val BITRATES = listOf(128, 192, 320, 0)
	}
}
