package com.nickshortnct.ondo.wear.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nickshortnct.ondo.wear.OndoWearApp
import com.nickshortnct.ondo.wear.R
import com.nickshortnct.ondo.wear.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Works through [DownloadStore]'s queue in the foreground so the watch can sleep meanwhile. */
class WatchDownloadService : Service() {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var running = false

	private val app get() = application as OndoWearApp

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val started = runCatching {
			ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Preparing downloads", 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		}.isSuccess
		if (!started) {
			stopSelf()
			return START_NOT_STICKY
		}
		if (!running) {
			running = true
			scope.launch {
				try { drain() } finally {
					running = false
					app.downloads.reportProgress(null)
					ServiceCompat.stopForeground(this@WatchDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
					stopSelf()
				}
			}
		}
		return START_NOT_STICKY
	}

	private suspend fun drain() {
		val store = app.downloads
		while (scope.isActive) {
			val collection = store.takeNext() ?: return
			val done = mutableListOf<Song>()
			var failed = 0
			collection.coverArt?.let { downloadCover(it) }
			collection.songs.forEachIndexed { index, song ->
				store.reportProgress(DownloadProgress(collection.downloadKey, collection.title, index, collection.songs.size, failed))
				notify(collection.title, index, collection.songs.size)
				val ok = store.songFile(song.id) != null || runCatching { downloadSong(song) }.isSuccess
				if (ok) {
					done += song
					song.coverArt?.takeIf { it != collection.coverArt }?.let { downloadCover(it) }
				} else failed++
			}
			if (done.isNotEmpty()) store.saveCollection(collection, done)
			store.reportProgress(DownloadProgress(collection.downloadKey, collection.title, collection.songs.size, collection.songs.size, failed))
		}
	}

	private fun downloadSong(song: Song) {
		val target = app.downloads.songTarget(song.id)
		fetch(app.api.streamUri(song.id, app.settings.streamBitrate.value).toString(), target)
	}

	private fun downloadCover(coverArt: String) {
		if (app.downloads.coverFile(coverArt) != null) return
		val uri = app.api.coverArtUri(coverArt, 300) ?: return
		runCatching { fetch(uri.toString(), app.downloads.coverTarget(coverArt)) }
	}

	private fun fetch(url: String, target: File) {
		val tmp = File(target.parentFile, target.name + ".part")
		val connection = URL(url).openConnection() as HttpURLConnection
		try {
			connection.connectTimeout = 20_000
			connection.readTimeout = 60_000
			app.api.headers().forEach { (k, v) -> connection.setRequestProperty(k, v) }
			if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
			val type = connection.contentType.orEmpty()
			// Subsonic reports errors as a JSON/XML body with HTTP 200.
			if (type.contains("json") || type.contains("xml")) throw IOException("server refused $url")
			connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
			if (!tmp.renameTo(target)) throw IOException("could not save ${target.name}")
		} finally {
			connection.disconnect()
			tmp.delete()
		}
	}

	private fun notification(title: String, done: Int, total: Int): Notification {
		val manager = getSystemService(NotificationManager::class.java)
		if (manager.getNotificationChannel(CHANNEL) == null) {
			manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW))
		}
		return NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_download)
			.setContentTitle("Downloading to watch")
			.setContentText(if (total > 0) "$title · $done/$total" else title)
			.setProgress(total, done, total == 0)
			.setOngoing(true)
			.setSilent(true)
			.build()
	}

	private fun notify(title: String, done: Int, total: Int) {
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, done, total))
	}

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}

	companion object {
		private const val CHANNEL = "downloads"
		private const val NOTIFICATION_ID = 7

		fun start(context: Context) {
			context.startForegroundService(Intent(context, WatchDownloadService::class.java))
		}
	}
}
