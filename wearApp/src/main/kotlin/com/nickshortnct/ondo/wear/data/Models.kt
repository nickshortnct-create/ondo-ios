package com.nickshortnct.ondo.wear.data

import org.json.JSONObject

data class Song(
	val id: String,
	val title: String,
	val artist: String,
	val album: String,
	val albumId: String?,
	val coverArt: String?,
	val durationSec: Int,
	val starred: Boolean,
) {
	fun toJson(): JSONObject = JSONObject()
		.put("id", id).put("title", title).put("artist", artist).put("album", album)
		.put("albumId", albumId).put("coverArt", coverArt).put("duration", durationSec).put("starred", starred)

	companion object {
		fun fromJson(o: JSONObject) = Song(
			id = o.getString("id"),
			title = o.optString("title"),
			artist = o.optString("artist"),
			album = o.optString("album"),
			albumId = o.optString("albumId").takeIf { it.isNotBlank() && it != "null" },
			coverArt = o.optString("coverArt").takeIf { it.isNotBlank() && it != "null" },
			durationSec = o.optInt("duration"),
			starred = o.has("starred") && o.optBoolean("starred"),
		)
	}
}

data class Album(val id: String, val name: String, val artist: String, val coverArt: String?, val songCount: Int)
data class Artist(val id: String, val name: String, val albumCount: Int, val coverArt: String?)
data class Playlist(val id: String, val name: String, val songCount: Int, val coverArt: String?)

/** A song list plus what the phone needs to play the same thing ("ondo/album/<id>" etc.). */
data class SongCollection(
	val title: String,
	val subtitle: String,
	val coverArt: String?,
	val songs: List<Song>,
	val phoneMediaId: String?,
	val downloadKey: String,
)
