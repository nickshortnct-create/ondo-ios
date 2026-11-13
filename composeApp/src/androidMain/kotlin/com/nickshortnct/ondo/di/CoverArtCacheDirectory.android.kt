package com.nickshortnct.ondo.di

import okio.Path
import okio.Path.Companion.toOkioPath
import coil3.PlatformContext as CoilPlatformContext

internal actual fun coverArtCacheDirectory(context: CoilPlatformContext): Path =
	context.filesDir.resolve("cover_art").toOkioPath()
