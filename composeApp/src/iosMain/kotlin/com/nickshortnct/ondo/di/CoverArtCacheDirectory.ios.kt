package com.nickshortnct.ondo.di

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import coil3.PlatformContext as CoilPlatformContext

@OptIn(ExperimentalForeignApi::class)
internal actual fun coverArtCacheDirectory(context: CoilPlatformContext): Path {
	val supportDirectory = NSFileManager.defaultManager.URLForDirectory(
		directory = NSApplicationSupportDirectory,
		inDomain = NSUserDomainMask,
		appropriateForURL = null,
		create = true,
		error = null
	)
	return "${requireNotNull(supportDirectory?.path)}/cover_art".toPath()
}
