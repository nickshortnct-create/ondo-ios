package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class AudioOutputManager {
	// Not wired up on iOS yet: "hide lyrics on a Bluetooth device" only works on Android.
	actual val bluetoothOutputName: StateFlow<String?> = MutableStateFlow(null)
}
