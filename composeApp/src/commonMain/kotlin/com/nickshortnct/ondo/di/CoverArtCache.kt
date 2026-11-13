package com.nickshortnct.ondo.di

import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import coil3.request.SuccessResult
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * A Navidrome cover id split into the part that names the album/song/artist
 * and the version stamp Navidrome appends (`al-<id>_<hex updatedAt>`).
 * [version] is null for ids without one: other Subsonic servers, and Navidrome's
 * `_0`, which it writes when it built the id without looking up a date (a song
 * with no art of its own gets `dc-<album>:<disc>_0` or `al-<album>_0`).
 */
internal data class CoverArtRef(val stableId: String, val version: String?)

private val versionedCoverId = Regex("^([a-z]{2}-[^_]+)_([0-9a-f]+)$")

internal fun splitCoverArtId(id: String): CoverArtRef =
	versionedCoverId.matchEntire(id)
		?.let { CoverArtRef(it.groupValues[1], it.groupValues[2].takeUnless { version -> version == "0" }) }
		?: CoverArtRef(id, null)

/** Versions are hex timestamps without leading zeros, so a longer one is a later one. */
private fun isNewerCoverVersion(version: String?, than: String?) = when {
	version == null -> false
	than == null -> true
	else -> version.length > than.length || (version.length == than.length && version > than)
}

/**
 * The id to fetch for each cover in [coverIds], by stable id. One cover can arrive
 * under several ids: `al-x_0` from a song and `al-x_6ad1d0af` from its album, or an
 * older stamp from a song row that hasn't been refreshed since. The newest id wins.
 */
fun newestCoverIds(coverIds: Iterable<String?>): Map<String, String> = buildMap {
	coverIds.forEach { id ->
		if (id.isNullOrBlank()) return@forEach
		val ref = splitCoverArtId(id)
		val current = this[ref.stableId]
		if (current == null || isNewerCoverVersion(ref.version, splitCoverArtId(current).version)) put(ref.stableId, id)
	}
}

internal fun coverArtRefFromUrl(url: String): CoverArtRef? {
	if (!url.contains("/getCoverArt")) return null
	val id = runCatching { Url(url).parameters["id"] }.getOrNull()
	return id?.takeIf { it.isNotBlank() }?.let(::splitCoverArtId)
}

internal fun coverArtCacheKey(stableId: String) = "cover:$stableId"

/**
 * Last cover version stored for each [CoverArtRef.stableId], persisted next to
 * the image cache as `id<TAB>version` lines.
 */
internal class CoverArtVersions(
	private val file: Path,
	private val scope: CoroutineScope,
	private val fileSystem: FileSystem = FileSystem.SYSTEM
) {
	private val versions = MutableStateFlow<Map<String, String>>(emptyMap())
	private val writeLock = Mutex()
	private var saveJob: Job? = null

	private val loaded = scope.async(Dispatchers.IO) {
		val fromFile = runCatching {
			if (!fileSystem.exists(file)) emptyMap()
			else fileSystem.read(file) {
				generateSequence { readUtf8Line() }
					.mapNotNull { line ->
						val tab = line.indexOf('\t')
						if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
					}
					.toMap()
			}
		}.getOrDefault(emptyMap())
		// Versions recorded before the file finished loading are newer; keep them.
		versions.update { fromFile + it }
	}

	suspend fun get(stableId: String): String? {
		if (!loaded.isCompleted) loaded.await()
		return versions.value[stableId]
	}

	fun put(stableId: String, version: String) {
		if (versions.value[stableId] == version) return
		versions.update { it + (stableId to version) }
		saveJob?.cancel()
		saveJob = scope.launch(Dispatchers.IO) {
			delay(SAVE_DELAY_MS)
			save()
		}
	}

	private suspend fun save() = writeLock.withLock {
		runCatching {
			file.parent?.let { fileSystem.createDirectories(it) }
			val tmp = file.parent!! / "${file.name}.tmp"
			fileSystem.write(tmp) {
				versions.value.forEach { (id, version) -> writeUtf8(id).writeUtf8("\t").writeUtf8(version).writeUtf8("\n") }
			}
			fileSystem.atomicMove(tmp, file)
		}
	}

	private companion object {
		const val SAVE_DELAY_MS = 1_000L
	}
}

/**
 * Permanent copies of the covers of downloaded songs, one file per stable id, so
 * a downloaded song never waits on the server for its cover. The image cache
 * can't promise that: it evicts, the user can clear it, and it refetches whenever
 * Navidrome re-stamps a cover, which a rescan does to thousands of songs.
 *
 * DownloadManager decides which covers belong here and refreshes them; this class
 * only stores them. Coil decodes by content, so files don't need a real extension.
 */
class DownloadedCoverArt internal constructor(
	private val directory: Path,
	private val imageCacheVersions: CoverArtVersions,
	scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
	private val fileSystem: FileSystem = FileSystem.SYSTEM
) {
	private val versions = CoverArtVersions(directory / "versions.tsv", scope, fileSystem)
	private val stored = MutableStateFlow<Set<String>>(emptySet())
	// Songs of one album download at the same time and share its cover.
	private val locks = List(16) { Mutex() }

	private val loaded = scope.async(Dispatchers.IO) {
		val found = runCatching {
			fileSystem.listOrNull(directory).orEmpty().mapNotNull { file ->
				when {
					file.name.endsWith(".$FILE_EXTENSION") -> file.name.removeSuffix(".$FILE_EXTENSION")
					file.name.endsWith(".$TEMP_EXTENSION") -> { fileSystem.delete(file); null }
					else -> null
				}
			}
		}.getOrDefault(emptyList())
		stored.update { it + found }
	}

	private suspend fun awaitLoaded() {
		if (!loaded.isCompleted) loaded.await()
	}

	private fun fileName(stableId: String) =
		if (safeCoverFileName.matches(stableId)) stableId
		else "x-" + stableId.encodeToByteArray().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

	private fun path(name: String) = directory / "$name.$FILE_EXTENSION"

	internal suspend fun pathOf(stableId: String): Path? {
		awaitLoaded()
		return fileName(stableId).takeIf { it in stored.value }?.let(::path)
	}

	suspend fun contains(coverId: String) = pathOf(splitCoverArtId(coverId).stableId) != null

	/** Whether the file exists and, if [coverId] carries a version, is of that version. */
	suspend fun isCurrent(coverId: String): Boolean {
		val ref = splitCoverArtId(coverId)
		if (pathOf(ref.stableId) == null) return false
		return ref.version == null || versions.get(ref.stableId) == ref.version
	}

	/**
	 * Saves [coverId] unless it's already current, taking it from [imageCache] when
	 * that holds the same version and calling [download] with a path to write to
	 * otherwise. Throws if that fails, leaving any older copy in place.
	 */
	suspend fun store(coverId: String, imageCache: DiskCache?, download: suspend (Path) -> Unit) {
		val ref = splitCoverArtId(coverId)
		locks[ref.stableId.hashCode().mod(locks.size)].withLock {
			if (isCurrent(coverId)) return
			val name = fileName(ref.stableId)
			val temp = directory / "$name.$TEMP_EXTENSION"
			try {
				fileSystem.createDirectories(directory)
				if (!copyFromImageCache(ref, imageCache, temp)) download(temp)
				check((fileSystem.metadata(temp).size ?: 0L) > 0L) { "The cover art file is empty" }
				fileSystem.atomicMove(temp, path(name))
				stored.update { it + name }
				ref.version?.let { versions.put(ref.stableId, it) }
			} finally {
				withContext(NonCancellable) { runCatching { fileSystem.delete(temp) } }
			}
		}
	}

	private suspend fun copyFromImageCache(ref: CoverArtRef, imageCache: DiskCache?, target: Path): Boolean {
		if (imageCache == null) return false
		if (ref.version != null && imageCacheVersions.get(ref.stableId) != ref.version) return false
		return runCatching {
			imageCache.openSnapshot(coverArtCacheKey(ref.stableId))?.use { snapshot ->
				fileSystem.copy(snapshot.data, target)
				true
			} ?: false
		}.getOrDefault(false)
	}

	suspend fun retainOnly(stableIds: Set<String>) {
		awaitLoaded()
		val keep = stableIds.mapTo(HashSet(), ::fileName)
		(stored.value - keep).forEach { name ->
			stored.update { it - name }
			runCatching { fileSystem.delete(path(name)) }
		}
	}

	suspend fun clear() {
		awaitLoaded()
		stored.value = emptySet()
		runCatching { fileSystem.deleteRecursively(directory) }
	}

	private companion object {
		const val FILE_EXTENSION = "img"
		const val TEMP_EXTENSION = "tmp"
		val safeCoverFileName = Regex("[A-Za-z0-9._-]+")
	}
}

/**
 * Keeps cover art cached per album/song rather than per Navidrome version.
 *
 * Navidrome bakes the album's last-update time into every cover id, so any
 * tag edit or rescan used to orphan the cached image and leave a blank cover
 * until the phone could reach the server again. Covers are now cached under
 * the stable id. When the version changes the cover is fetched again (so new
 * artwork still shows up), and if that fails the cached cover is used.
 *
 * Covers of downloaded songs come straight from [DownloadedCoverArt] instead.
 */
internal class StableCoverArtInterceptor(
	private val versions: CoverArtVersions,
	private val downloaded: DownloadedCoverArt
) : Interceptor {
	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val request = chain.request
		val ref = (request.data as? String)?.let(::coverArtRefFromUrl) ?: return chain.proceed()
		val key = coverArtCacheKey(ref.stableId)
		val keyed = request.newBuilder()
			.memoryCacheKey(key)
			.diskCacheKey(key)
			.build()

		downloaded.pathOf(ref.stableId)?.let { file ->
			val local = chain.withRequest(keyed.newBuilder().data(file.toString()).build()).proceed()
			if (local is SuccessResult) return local
		}

		val version = ref.version
		if (version == null || versions.get(ref.stableId) == version) {
			return chain.withRequest(keyed).proceed()
		}

		val fresh = chain.withRequest(
			keyed.newBuilder()
				.memoryCachePolicy(request.memoryCachePolicy.writeOnly())
				.diskCachePolicy(request.diskCachePolicy.writeOnly())
				.placeholderMemoryCacheKey(key)
				.build()
		).proceed()
		if (fresh is SuccessResult) {
			versions.put(ref.stableId, version)
			return fresh
		}
		if (!request.diskCachePolicy.readEnabled) return fresh

		val cached = chain.withRequest(
			keyed.newBuilder()
				.networkCachePolicy(CachePolicy.DISABLED)
				.diskCachePolicy(CachePolicy.READ_ONLY)
				.build()
		).proceed()
		return if (cached is SuccessResult) cached else fresh
	}

	private fun CachePolicy.writeOnly() =
		if (writeEnabled) CachePolicy.WRITE_ONLY else CachePolicy.DISABLED
}
