package com.nickshortnct.ondo.domain.manager

import coil3.PlatformContext as CoilPlatformContext

actual fun startBackgroundDownloadService(context: CoilPlatformContext) {
	// not wired up on iOS yet -- would need a BGTaskScheduler/background
	// URLSession based approach instead of a foreground service
}
