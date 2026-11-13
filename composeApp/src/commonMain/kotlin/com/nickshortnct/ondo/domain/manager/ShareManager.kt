package com.nickshortnct.ondo.domain.manager

import androidx.compose.ui.graphics.ImageBitmap

expect class ShareManager {
	suspend fun shareImage(bitmap: ImageBitmap, fileName: String)
	suspend fun shareString(string: String)

	/** Shares the persisted debug log file (see Logger), if one exists yet. */
	suspend fun shareLogFile()
}
