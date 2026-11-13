package com.nickshortnct.ondo.shared

import android.content.Context
import androidx.media3.session.MediaLibraryService.MediaLibrarySession

/**
 * Something that lives exactly as long as [PlaybackService] and observes its
 * session -- e.g. the watch bridge in the app module, which must not pull its
 * Play Services dependency into this shared module.
 */
fun interface PlaybackServiceExtension {
	fun release()
}

object PlaybackServiceExtensions {
	/** Registered once from `Application.onCreate`; run on the main thread in `PlaybackService.onCreate`. */
	val factories = mutableListOf<(Context, MediaLibrarySession) -> PlaybackServiceExtension>()

	/** Set by [PlaybackService] while it runs, so remotes (the watch) can favourite the current track. */
	@Volatile
	var toggleFavorite: (() -> Unit)? = null

	/** Whether the current track is favourited, as last known by the service. */
	@Volatile
	var isCurrentStarred: Boolean = false
}
