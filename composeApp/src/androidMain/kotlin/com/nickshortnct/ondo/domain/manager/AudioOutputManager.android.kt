package com.nickshortnct.ondo.domain.manager

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

private val BluetoothOutputTypes = setOf(
	AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
	26, // TYPE_BLE_HEADSET (API 31)
	27 // TYPE_BLE_SPEAKER (API 31)
)

actual class AudioOutputManager(context: Context) {
	private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	// productName is the paired device's name (e.g. the car's head unit) and, unlike the
	// address, needs no Bluetooth permission.
	private fun currentBluetoothName(): String? = audioManager
		.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
		.firstOrNull { it.type in BluetoothOutputTypes }
		?.productName?.toString()?.trim()
		?.takeIf { it.isNotEmpty() }

	actual val bluetoothOutputName: StateFlow<String?> = callbackFlow {
		trySend(currentBluetoothName())
		val callback = object : AudioDeviceCallback() {
			override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
				trySend(currentBluetoothName())
			}

			override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
				trySend(currentBluetoothName())
			}
		}
		audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
		awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
	}
		.distinctUntilChanged()
		.stateIn(CoroutineScope(Dispatchers.Main + SupervisorJob()), SharingStarted.Eagerly, currentBluetoothName())
}
