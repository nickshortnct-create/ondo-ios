package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.flow.StateFlow

expect class AudioOutputManager {
	/** Name of the connected Bluetooth audio device (a car, headphones), or null when there isn't one. */
	val bluetoothOutputName: StateFlow<String?>
}
