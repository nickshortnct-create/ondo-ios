package com.nickshortnct.ondo.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.nickshortnct.ondo.domain.models.settings.OfflineMode
import android.net.ConnectivityManager as AndroidConnectivityManager

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false
) {
	companion object {
		fun fromCaps(
			caps: NetworkCapabilities
		) = NetworkStatus(
			isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
			isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				|| !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		)
	}
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
actual class ConnectivityManager(
	context: Context,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	private val started = SharingStarted.Eagerly
	private val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager
	private val initialNetworkStatus = connectivityManager
		.getNetworkCapabilities(connectivityManager.activeNetwork)
		?.let { NetworkStatus.fromCaps(it) } ?: NetworkStatus()

	private val networkStatus = callbackFlow {
		val callback = object : AndroidConnectivityManager.NetworkCallback() {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				trySend(NetworkStatus.fromCaps(caps))
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				trySend(NetworkStatus())
			}
		}

		connectivityManager.registerDefaultNetworkCallback(callback)

		trySend(
			connectivityManager
				.getNetworkCapabilities(connectivityManager.activeNetwork)
				?.let { NetworkStatus.fromCaps(it) }
				?: NetworkStatus()
		)

		awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
	}
		.flowOn(dispatcher)
		.conflate()
		.stateIn(scope, started, initialNetworkStatus)

	actual val isCellular = networkStatus
		.map { it.isCellular }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, SharingStarted.Eagerly, initialNetworkStatus.isCellular)

	actual val isOnline = networkStatus
		.combine(snapshotFlow { preferenceManager.offlineMode }) { status, mode ->
			when (mode) {
				OfflineMode.Forced -> false
				OfflineMode.NoWiFi -> status.isOnline && !status.isCellular
				else -> status.isOnline
			}
		}
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, when (preferenceManager.offlineMode) {
			OfflineMode.Forced -> false
			OfflineMode.NoWiFi -> initialNetworkStatus.isOnline && !initialNetworkStatus.isCellular
			else -> initialNetworkStatus.isOnline
		})
}
