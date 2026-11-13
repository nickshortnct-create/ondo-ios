package com.nickshortnct.ondo.wear.ui

import com.nickshortnct.ondo.wear.OndoWearApp
import com.nickshortnct.ondo.wear.data.PlayTarget
import com.nickshortnct.ondo.wear.data.Song
import com.nickshortnct.ondo.wear.data.SongCollection
import com.nickshortnct.ondo.wear.data.WearContract

/**
 * Starts a selection on the chosen device. On the phone, albums and playlists
 * go through the same ids Android Auto uses, so the phone builds the full queue
 * from its own library (and its downloads) and starts at the tapped track.
 */
object Playback {
	/** @return the route of the player screen to show. */
	fun play(
		app: OndoWearApp,
		songs: List<Song>,
		startIndex: Int = 0,
		shuffle: Boolean = false,
		collection: SongCollection? = null,
		forceWatch: Boolean = false,
	): String {
		if (songs.isEmpty()) return Routes.HOME
		val target = if (forceWatch) PlayTarget.Watch else app.settings.playTarget.value
		if (target == PlayTarget.Watch) {
			app.player.play(songs, startIndex, shuffle)
			return Routes.PLAYER_WATCH
		}
		val phoneId = collection?.phoneMediaId
		when {
			shuffle -> app.phoneLink.sendAsync(WearContract.CMD_PLAY_SONGS, songs.shuffled().joinToString(",") { it.id }, "0")
			phoneId != null && startIndex == 0 -> app.phoneLink.sendAsync(WearContract.CMD_PLAY_MEDIA, phoneId)
			phoneId != null && phoneId.startsWith("ondo/album/") ->
				app.phoneLink.sendAsync(WearContract.CMD_PLAY_MEDIA, "ondo/track/album/${phoneId.removePrefix("ondo/album/")}/${songs[startIndex].id}")
			phoneId != null && phoneId.startsWith("ondo/playlist/") ->
				app.phoneLink.sendAsync(WearContract.CMD_PLAY_MEDIA, "ondo/track/playlist/${phoneId.removePrefix("ondo/playlist/")}/${songs[startIndex].id}")
			else -> app.phoneLink.sendAsync(WearContract.CMD_PLAY_SONGS, songs.take(MAX_PHONE_QUEUE).joinToString(",") { it.id }, startIndex.coerceAtMost(MAX_PHONE_QUEUE - 1).toString())
		}
		return Routes.PLAYER_PHONE
	}

	private const val MAX_PHONE_QUEUE = 500
}

object Routes {
	const val HOME = "home"
	const val PLAYER_WATCH = "player/watch"
	const val PLAYER_PHONE = "player/phone"
	const val PLAYLISTS = "playlists"
	const val ARTISTS = "artists"
	const val SEARCH = "search"
	const val DOWNLOADS = "downloads"
	const val SETTINGS = "settings"
	fun albums(type: String, title: String) = "albums/$type/${android.net.Uri.encode(title)}"
	fun artist(id: String) = "artist/${android.net.Uri.encode(id)}"
	fun collection(kind: String, id: String) = "collection/$kind/${android.net.Uri.encode(id)}"
}
