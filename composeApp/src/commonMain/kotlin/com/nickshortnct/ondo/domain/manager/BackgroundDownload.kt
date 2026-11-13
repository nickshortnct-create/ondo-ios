package com.nickshortnct.ondo.domain.manager

import coil3.PlatformContext as CoilPlatformContext

// Downloads run on DownloadManager's own coroutine scope, which is tied to
// the app process -- Android is free to kill that process once there's no
// foreground component keeping it alive, which would silently abandon any
// in-flight or queued downloads. This starts a platform-specific mechanism
// to keep the process alive while downloads are active; on Android that's a
// foreground service (DownloadService). No-op on platforms without that
// concern (iOS handles background execution differently and isn't wired up
// here yet).
expect fun startBackgroundDownloadService(context: CoilPlatformContext)
