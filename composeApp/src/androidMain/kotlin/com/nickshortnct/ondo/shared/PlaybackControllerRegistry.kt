package com.nickshortnct.ondo.shared

object PlaybackControllerRegistry {
	var releaseController: (() -> Unit)? = null
}
