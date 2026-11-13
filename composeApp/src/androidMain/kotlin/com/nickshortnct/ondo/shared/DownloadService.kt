package com.nickshortnct.ondo.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Exists purely to keep the process (and DownloadManager's coroutine scope)
// alive while downloads are in flight -- Android is free to kill a
// backgrounded app process with no foreground component, which would
// silently abandon in-flight/queued playlist downloads. Started by
// DownloadManager (via startBackgroundDownloadService) whenever download
// work begins, and stops itself once activeDownloadCount drops to zero.
class DownloadService : Service(), KoinComponent {
	private val downloadManager: DownloadManager by inject()
	private val scope = MainScope()
	private var watchJob: Job? = null
	private var lastStartId = 0

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		lastStartId = startId
		if (intent?.action == ACTION_CANCEL_ALL) {
			downloadManager.cancelAllActiveDownloads()
		}
		if (!startInForeground(buildNotification(downloadManager.activeDownloadCount.value, downloadManager.libraryDownloadProgress.value))) {
			return START_NOT_STICKY
		}

		if (watchJob == null) {
			watchJob = scope.launch {
				combine(
					downloadManager.activeDownloadCount,
					downloadManager.isDownloadingLibrary,
					downloadManager.libraryDownloadProgress
				) { count, library, progress -> Triple(count, library, progress) }
					.collectLatest { (count, library, progress) ->
						if (count <= 0 && !library) {
							stopForeground(STOP_FOREGROUND_REMOVE)
							// Only stop if no newer start request arrived in the meantime.
							stopSelfResult(lastStartId)
						} else {
							val manager = getSystemService(NotificationManager::class.java)
							manager.notify(NOTIFICATION_ID, buildNotification(count, if (library) progress else null))
						}
					}
			}
		}

		return START_NOT_STICKY
	}

	// Android 14 (API 34): the data-sync quota ran out. The system gives us a few seconds to
	// stop; otherwise the app crashes with ForegroundServiceDidNotStopInTimeException.
	override fun onTimeout(startId: Int) {
		handleTimeout()
	}

	// Android 15+ (API 35): same, with the foreground service type.
	override fun onTimeout(startId: Int, fgsType: Int) {
		handleTimeout()
	}

	private fun handleTimeout() {
		Logger.w("DownloadService", "foreground service time limit reached; suspending downloads")
		downloadManager.suspendForBackgroundLimit()
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	override fun onDestroy() {
		watchJob?.cancel()
		watchJob = null
		scope.cancel()
		super.onDestroy()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	private fun startInForeground(notification: Notification): Boolean = try {
		ServiceCompat.startForeground(
			this,
			NOTIFICATION_ID,
			notification,
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
		)
		true
	} catch (e: Exception) {
		// ForegroundServiceStartNotAllowedException (API 31+) or SecurityException when the quota is used up.
		Logger.w("DownloadService", "foreground start refused; downloads continue while the app is open", e)
		stopSelf()
		false
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Downloads",
				NotificationManager.IMPORTANCE_LOW
			)
			getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
		}
	}

	private fun buildNotification(activeCount: Int, libraryProgress: Float?): Notification {
		val text = if (activeCount > 0) {
			"Downloading $activeCount song${if (activeCount == 1) "" else "s"}..."
		} else {
			"Preparing downloads…"
		}
		val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			putExtra(EXTRA_OPEN_ONDO_PAGE, "downloads")
		}
		val contentIntent = openIntent?.let {
			PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		}
		val cancelIntent = PendingIntent.getService(
			this,
			2,
			Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("Ondo")
			.setContentText(text)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setContentIntent(contentIntent)
			.addAction(0, "Cancel all", cancelIntent)
			.apply { libraryProgress?.let { setProgress(100, (it * 100).toInt().coerceIn(0, 100), false) } }
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.build()
	}

	companion object {
		private const val CHANNEL_ID = "downloads"
		private const val NOTIFICATION_ID = 4821
		private const val ACTION_CANCEL_ALL = "com.nickshortnct.ondo.action.CANCEL_ALL_DOWNLOADS"

		/** Intent extra naming an Ondo page for MainActivity to open (see AppRoutes). */
		const val EXTRA_OPEN_ONDO_PAGE = "ondo.openPage"
	}
}
