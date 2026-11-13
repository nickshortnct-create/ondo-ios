package com.nickshortnct.ondo.wear.downloads

import android.content.Context
import com.nickshortnct.ondo.wear.data.Song
import com.nickshortnct.ondo.wear.data.SongCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadedCollection(
	val key: String,
	val title: String,
	val subtitle: String,
	val coverArt: String?,
	val songs: List<Song>,
	val phoneMediaId: String?,
)

data class DownloadProgress(val key: String, val title: String, val done: Int, val total: Int, val failed: Int)

/**
 * Albums and playlists saved on the watch for offline listening. Files live in
 * app storage (`files/downloads`); the index is a small JSON file.
 */
class DownloadStore(context: Context) {
	private val root = File(context.filesDir, "downloads").apply { mkdirs() }
	private val songsDir = File(root, "songs").apply { mkdirs() }
	private val coversDir = File(root, "covers").apply { mkdirs() }
	private val indexFile = File(root, "index.json")

	private val _collections = MutableStateFlow(readIndex())
	val collections: StateFlow<List<DownloadedCollection>> get() = _collections

	private val _progress = MutableStateFlow<DownloadProgress?>(null)
	val progress: StateFlow<DownloadProgress?> get() = _progress

	private val _pending = MutableStateFlow<List<SongCollection>>(emptyList())
	val pending: StateFlow<List<SongCollection>> get() = _pending

	fun songFile(songId: String): File? = File(songsDir, safe(songId)).takeIf { it.isFile && it.length() > 0 }
	fun coverFile(coverArt: String?): File? = coverArt?.let { File(coversDir, safe(it)) }?.takeIf { it.isFile && it.length() > 0 }
	fun songTarget(songId: String) = File(songsDir, safe(songId))
	fun coverTarget(coverArt: String) = File(coversDir, safe(coverArt))

	fun isDownloaded(key: String) = _collections.value.any { it.key == key }
	fun isQueued(key: String) = _pending.value.any { it.downloadKey == key } || _progress.value?.key == key

	@Synchronized
	fun enqueue(collection: SongCollection) {
		if (isQueued(collection.downloadKey)) return
		_pending.value = _pending.value + collection
	}

	@Synchronized
	fun takeNext(): SongCollection? = _pending.value.firstOrNull()?.also { _pending.value = _pending.value.drop(1) }

	fun reportProgress(progress: DownloadProgress?) {
		_progress.value = progress
	}

	@Synchronized
	fun saveCollection(collection: SongCollection, songs: List<Song>) {
		val entry = DownloadedCollection(collection.downloadKey, collection.title, collection.subtitle, collection.coverArt, songs, collection.phoneMediaId)
		_collections.value = _collections.value.filterNot { it.key == entry.key } + entry
		writeIndex()
	}

	@Synchronized
	fun delete(key: String) {
		val removed = _collections.value.firstOrNull { it.key == key } ?: return
		_collections.value = _collections.value.filterNot { it.key == key }
		val stillUsed = _collections.value.flatMap { c -> c.songs.map { it.id } }.toSet()
		removed.songs.filter { it.id !in stillUsed }.forEach { songTarget(it.id).delete() }
		writeIndex()
	}

	fun usedBytes(): Long = songsDir.listFiles().orEmpty().sumOf { it.length() } + coversDir.listFiles().orEmpty().sumOf { it.length() }

	private fun readIndex(): List<DownloadedCollection> = runCatching {
		if (!indexFile.isFile) return emptyList()
		val array = JSONArray(indexFile.readText())
		(0 until array.length()).map { i ->
			val o = array.getJSONObject(i)
			val songs = o.getJSONArray("songs")
			DownloadedCollection(
				key = o.getString("key"),
				title = o.optString("title"),
				subtitle = o.optString("subtitle"),
				coverArt = o.optString("coverArt").takeIf { it.isNotBlank() && it != "null" },
				songs = (0 until songs.length()).map { Song.fromJson(songs.getJSONObject(it)) },
				phoneMediaId = o.optString("phoneMediaId").takeIf { it.isNotBlank() && it != "null" },
			)
		}
	}.getOrDefault(emptyList())

	private fun writeIndex() {
		val array = JSONArray()
		_collections.value.forEach { c ->
			array.put(
				JSONObject().put("key", c.key).put("title", c.title).put("subtitle", c.subtitle)
					.put("coverArt", c.coverArt).put("phoneMediaId", c.phoneMediaId)
					.put("songs", JSONArray().apply { c.songs.forEach { put(it.toJson()) } })
			)
		}
		val tmp = File(root, "index.json.tmp")
		tmp.writeText(array.toString())
		tmp.renameTo(indexFile)
	}

	private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
