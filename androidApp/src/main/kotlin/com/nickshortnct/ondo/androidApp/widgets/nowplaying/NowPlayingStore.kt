package com.nickshortnct.ondo.androidApp.widgets.nowplaying

import android.content.Context

/**
 * The last now-playing snapshot, shared by every widget. Per-widget Glance state only exists
 * after a widget's first update, so a widget added while music is playing would otherwise show
 * "Not Playing" until the next track change.
 */
data class NowPlayingSnapshot(
	val isPlaying: Boolean,
	val title: String,
	val artist: String,
	val artUrl: String?
)

object NowPlayingStore {
	private const val FILE = "widget_now_playing"

	fun save(context: Context, snapshot: NowPlayingSnapshot) {
		context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
			.putBoolean("isPlaying", snapshot.isPlaying)
			.putString("title", snapshot.title)
			.putString("artist", snapshot.artist)
			.putString("artUrl", snapshot.artUrl)
			.apply()
	}

	fun load(context: Context): NowPlayingSnapshot? {
		val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		if (!prefs.contains("title")) return null
		return NowPlayingSnapshot(
			isPlaying = prefs.getBoolean("isPlaying", false),
			title = prefs.getString("title", "").orEmpty(),
			artist = prefs.getString("artist", "").orEmpty(),
			artUrl = prefs.getString("artUrl", null)
		)
	}
}
