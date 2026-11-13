package com.nickshortnct.ondo.domain.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nickshortnct.ondo.di.ActivityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

actual class PermissionManager(private val context: Context, private val activities: ActivityProvider) {
	private val requestMutex = Mutex()
	private val mainHandler = Handler(Looper.getMainLooper())

	actual fun openPermissionsSettings() {
		context.startActivity(Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
			.setData(Uri.fromParts("package", context.packageName, null))
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	}

	actual suspend fun requestLocalNetworkPermission(): Boolean =
		Build.VERSION.SDK_INT < 37 || request(Manifest.permission.ACCESS_LOCAL_NETWORK)

	actual suspend fun requestNotificationPermission(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || request(Manifest.permission.POST_NOTIFICATIONS)

	private suspend fun request(permission: String): Boolean = withContext(Dispatchers.Main.immediate) {
		requestMutex.withLock {
			if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return@withLock true
			val activity = activities.currentOrNull() as? ComponentActivity ?: return@withLock false
			if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) return@withLock false
			suspendCancellableCoroutine { continuation ->
				var launcher: ActivityResultLauncher<String>? = null
				lateinit var observer: LifecycleEventObserver
				fun cleanup() {
					launcher?.unregister()
					activity.lifecycle.removeObserver(observer)
				}
				fun finish(granted: Boolean) {
					cleanup()
					if (continuation.isActive) continuation.resume(granted)
				}
				observer = LifecycleEventObserver { _, event ->
					if (event == Lifecycle.Event.ON_DESTROY) finish(false)
				}
				launcher = activity.activityResultRegistry.register(
					"ondo-permission-${UUID.randomUUID()}", ActivityResultContracts.RequestPermission(), ::finish
				)
				activity.lifecycle.addObserver(observer)
				continuation.invokeOnCancellation { mainHandler.post { cleanup() } }
				try { launcher.launch(permission) }
				catch (_: RuntimeException) { finish(false) }
			}
		}
	}
}
