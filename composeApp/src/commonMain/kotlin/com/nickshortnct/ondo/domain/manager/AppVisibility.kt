package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether the app is on screen. Playback runs for hours with the app in the background, so work that
 * only matters to someone looking (progress polling, library refreshes) slows down while it isn't.
 */
object AppVisibility {
	val visible = MutableStateFlow(false)
}
