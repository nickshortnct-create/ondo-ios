package com.nickshortnct.ondo.domain.manager

import android.content.Intent
import androidx.core.content.ContextCompat
import com.nickshortnct.ondo.shared.DownloadService
import coil3.PlatformContext as CoilPlatformContext

actual fun startBackgroundDownloadService(context: CoilPlatformContext) {
	ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
}
