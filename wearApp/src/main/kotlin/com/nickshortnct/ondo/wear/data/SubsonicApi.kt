package com.nickshortnct.ondo.wear.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Subsonic/OpenSubsonic client for browsing, streaming and favourites on the watch. */
class SubsonicApi(private val accounts: AccountStore) {
	class NotSignedInException : IOException("Sign in to Ondo on your phone first")

	private fun account() = accounts.account.value ?: throw NotSignedInException()

	private fun Uri.Builder.auth(account: Account): Uri.Builder = this
		.appendQueryParameter("u", account.username)
		.appendQueryParameter("t", account.token)
		.appendQueryParameter("s", account.salt)
		.appendQueryParameter("v", "1.16.1")
		.appendQueryParameter("c", CLIENT)
		.appendQueryParameter("f", "json")

	fun endpoint(method: String, params: Map<String, String> = emptyMap(), account: Account = account()): Uri =
		Uri.parse("${account.baseUrl}/rest/$method").buildUpon().auth(account).apply {
			params.forEach { (k, v) -> appendQueryParameter(k, v) }
		}.build()

	/** Stream URL; [maxBitRate] 0 = original file. */
	fun streamUri(songId: String, maxBitRate: Int): Uri = endpoint(
		"stream",
		buildMap {
			put("id", songId)
			if (maxBitRate > 0) {
				put("maxBitRate", maxBitRate.toString())
				put("format", "mp3")
			}
			put("estimateContentLength", "true")
		}
	)

	fun coverArtUri(coverArt: String?, size: Int = 300): Uri? = coverArt?.takeIf { it.isNotBlank() }?.let {
		runCatching { endpoint("getCoverArt", mapOf("id" to it, "size" to size.toString())) }.getOrNull()
	}

	fun headers(): Map<String, String> = accounts.account.value?.headers.orEmpty()

	private suspend fun call(method: String, params: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
		val account = account()
		val connection = URL(endpoint(method, params, account).toString()).openConnection() as HttpURLConnection
		try {
			connection.connectTimeout = 15_000
			connection.readTimeout = 30_000
			connection.setRequestProperty("User-Agent", CLIENT)
			account.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
			val code = connection.responseCode
			if (code !in 200..299) throw IOException("Server returned HTTP $code")
			val body = connection.inputStream.bufferedReader().use { it.readText() }
			val response = JSONObject(body).getJSONObject("subsonic-response")
			if (response.optString("status") != "ok") {
				val error = response.optJSONObject("error")
				throw IOException(error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Server error")
			}
			response
		} finally {
			connection.disconnect()
		}
	}

	suspend fun ping() { call("ping") }

	suspend fun playlists(): List<Playlist> = call("getPlaylists")
		.optJSONObject("playlists")?.optArray("playlist").objects().map {
			Playlist(it.getString("id"), it.optString("name"), it.optInt("songCount"), it.optStringOrNull("coverArt"))
		}

	suspend fun playlist(id: String): SongCollection {
		val p = call("getPlaylist", mapOf("id" to id)).getJSONObject("playlist")
		return SongCollection(
			title = p.optString("name"),
			subtitle = "${p.optInt("songCount")} songs",
			coverArt = p.optStringOrNull("coverArt"),
			songs = p.optArray("entry").objects().map(::song),
			phoneMediaId = "ondo/playlist/$id",
			downloadKey = "playlist:$id",
		)
	}

	/** [type]: alphabeticalByName, newest, recent, frequent, starred, random. */
	suspend fun albums(type: String, size: Int = 200, offset: Int = 0): List<Album> = call(
		"getAlbumList2", mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString())
	).optJSONObject("albumList2")?.optArray("album").objects().map(::album)

	suspend fun album(id: String): SongCollection {
		val a = call("getAlbum", mapOf("id" to id)).getJSONObject("album")
		return SongCollection(
			title = a.optString("name"),
			subtitle = a.optString("artist"),
			coverArt = a.optStringOrNull("coverArt"),
			songs = a.optArray("song").objects().map(::song),
			phoneMediaId = "ondo/album/$id",
			downloadKey = "album:$id",
		)
	}

	suspend fun artists(): List<Artist> = call("getArtists").optJSONObject("artists")?.optArray("index").objects()
		.flatMap { it.optArray("artist").objects() }
		.map { Artist(it.getString("id"), it.optString("name"), it.optInt("albumCount"), it.optStringOrNull("coverArt")) }

	suspend fun artistAlbums(id: String): Pair<String, List<Album>> {
		val a = call("getArtist", mapOf("id" to id)).getJSONObject("artist")
		return a.optString("name") to a.optArray("album").objects().map(::album)
	}

	suspend fun starredSongs(): List<Song> = call("getStarred2").optJSONObject("starred2")?.optArray("song").objects().map(::song)

	suspend fun randomSongs(size: Int = 100): List<Song> = call("getRandomSongs", mapOf("size" to size.toString()))
		.optJSONObject("randomSongs")?.optArray("song").objects().map(::song)

	data class SearchResults(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)

	suspend fun search(query: String): SearchResults {
		val r = call("search3", mapOf("query" to query, "artistCount" to "5", "albumCount" to "10", "songCount" to "30"))
			.optJSONObject("searchResult3")
		return SearchResults(
			artists = r?.optArray("artist").objects().map { Artist(it.getString("id"), it.optString("name"), it.optInt("albumCount"), it.optStringOrNull("coverArt")) },
			albums = r?.optArray("album").objects().map(::album),
			songs = r?.optArray("song").objects().map(::song),
		)
	}

	suspend fun setStarred(songId: String, starred: Boolean) {
		call(if (starred) "star" else "unstar", mapOf("id" to songId))
	}

	suspend fun scrobble(songId: String, submission: Boolean) {
		call("scrobble", mapOf("id" to songId, "submission" to submission.toString(), "time" to System.currentTimeMillis().toString()))
	}

	private fun album(it: JSONObject) = Album(
		it.getString("id"), it.optString("name", it.optString("title")), it.optString("artist"),
		it.optStringOrNull("coverArt"), it.optInt("songCount")
	)

	private fun song(it: JSONObject) = Song(
		id = it.getString("id"),
		title = it.optString("title"),
		artist = it.optString("artist"),
		album = it.optString("album"),
		albumId = it.optStringOrNull("albumId"),
		coverArt = it.optStringOrNull("coverArt"),
		durationSec = it.optInt("duration"),
		starred = it.has("starred"),
	)

	private companion object {
		const val CLIENT = "OndoWatch"

		fun JSONObject.optArray(name: String): JSONArray? = when (val v = opt(name)) {
			is JSONArray -> v
			is JSONObject -> JSONArray().put(v)
			else -> null
		}

		fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

		fun JSONObject.optStringOrNull(name: String): String? = optString(name).takeIf { has(name) && it.isNotBlank() }
	}
}
