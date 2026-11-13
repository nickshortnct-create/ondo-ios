package com.nickshortnct.ondo.domain.manager

import coil3.SingletonImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.DownloadDao
import com.nickshortnct.ondo.data.database.dao.LyricDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.entities.DownloadEntity
import com.nickshortnct.ondo.data.database.entities.DownloadStatus
import com.nickshortnct.ondo.data.database.entities.LyricEntity
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.repositories.LyricsRepository
import com.nickshortnct.ondo.util.Logger
import com.nickshortnct.ondo.di.DownloadedCoverArt
import com.nickshortnct.ondo.di.PlatformType
import com.nickshortnct.ondo.di.newestCoverIds
import coil3.PlatformContext as CoilPlatformContext
import kotlin.concurrent.Volatile

class DownloadManager(
	private val coilPlatformContext: CoilPlatformContext,
	private val downloadDao: DownloadDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val storageManager: StorageManager,
	private val lyricsRepository: LyricsRepository,
	private val lyricDao: LyricDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val connectivityManager: ConnectivityManager,
	private val platformType: PlatformType,
	private val permissionManager: PermissionManager,
	private val downloadedCovers: DownloadedCoverArt
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val client = HttpClient {
		expectSuccess = true
		install(HttpTimeout) { connectTimeoutMillis = 15_000L; socketTimeoutMillis = 60_000L }
		defaultRequest {
			preferenceManager.customHeadersMap().forEach { (key, value) -> header(key, value) }
		}
	}
	private val activeDownloadsMutex = Mutex()
	private val activeDownloads = mutableMapOf<String, Job>()
	private val downloadSemaphore =
		Semaphore(MAX_CONCURRENT_DOWNLOADS)

	private var libraryDownloadJob: Job? = null
	@Volatile private var coverSyncJob: Job? = null
	@Volatile private var coverPruneJob: Job? = null

	// Set when the OS ran out of background (foreground-service) time. Active downloads are
	// stopped but their rows stay DOWNLOADING so resumeOrphanedDownloads() picks them up
	// again when the app returns to the foreground.
	@Volatile private var backgroundLimitReached = false
	private var notificationPermissionRequested = false

	private val transferProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
	val downloadProgress = transferProgress.asStateFlow()
	val allDownloads = combine(downloadDao.getAllDownloads(), transferProgress) { downloads, progress ->
		 downloads.map { row -> progress[row.songId]?.let { row.copy(progress = it) } ?: row }.toImmutableList()
	}
	val downloadCount = downloadDao.getDownloadsCount()
	// File sizes are only re-read when the set of downloaded files changes, never on a progress tick.
	val downloadSize = allDownloads
		.map { downloads ->
			downloads
				.filter { it.status == DownloadStatus.DOWNLOADED && it.filePath != null }
				.map { it.filePath!! }
		}
		.distinctUntilChanged()
		.map { paths -> paths.sumOf { storageManager.getFileSize(it) } }
		.flowOn(Dispatchers.IO)

	/** Downloads indexed by song id, rebuilt once per emission; UI rows look themselves up here instead of scanning the list. */
	val downloadsById: StateFlow<Map<String, DownloadEntity>> = allDownloads
		.map { downloads -> downloads.associateBy { it.songId } }
		.stateIn(scope, SharingStarted.Eagerly, emptyMap())

	val downloadedSongs: StateFlow<Map<String, String>?> = allDownloads
		.map { downloads -> downloads.filter { it.status == DownloadStatus.DOWNLOADED && it.filePath != null }.associate { it.songId to it.filePath!! } }
		.stateIn(scope, SharingStarted.Eagerly, null)

	suspend fun awaitDownloadsLoaded() { downloadedSongs.first { it != null } }

	val isDownloadingLibrary: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val libraryDownloadProgress: StateFlow<Float>
		field = MutableStateFlow(0f)

	// Observed by DownloadService (Android) to know when it's safe to stop
	// the foreground service keeping the process alive for background downloads.
	val activeDownloadCount: StateFlow<Int>
		field = MutableStateFlow(0)

	init {
		scope.launch(Dispatchers.IO) {
			reconcileDownloads()
			resumeOrphanedDownloads()
			coverSyncJob = launch {
				delay(COVER_SYNC_DELAY_MS)
				syncDownloadedCovers()
			}
		}
	}

	/**
	 * Brings the downloads table and the downloads folder back in step, once per process:
	 * files with no record are adopted when their song is known (so a lost table doesn't strand
	 * gigabytes of music), stale unknown files are removed, and DOWNLOADED rows whose file has
	 * disappeared are dropped.
	 */
	private suspend fun reconcileDownloads() {
		try {
			val rows = downloadDao.getAllDownloadsList().associateBy { it.songId }
			val now = Clock.System.now().toEpochMilliseconds()
			for (file in storageManager.listDownloadedFiles()) {
				val songId = file.name.substringBeforeLast('.', missingDelimiterValue = file.name)
				val row = rows[songId]
				if (row?.status == DownloadStatus.DOWNLOADED || row?.status == DownloadStatus.DOWNLOADING) continue
				val ageMs = now - file.lastModifiedMs
				when {
					// Recent files may belong to a download that is still being written.
					ageMs < ADOPT_MIN_AGE_MS -> Unit
					songDao.getSongById(songId) != null && storageManager.getFileSize(file.path) > 0 -> {
						Logger.i("DownloadManager", "adopting download file for $songId")
						downloadDao.insertDownload(DownloadEntity(songId, DownloadStatus.DOWNLOADED, 1f, file.path))
					}
					ageMs > ORPHAN_FILE_MAX_AGE_MS -> {
						Logger.i("DownloadManager", "removing unknown download file ${file.name}")
						storageManager.deleteFile(file.path)
					}
				}
			}
			rows.values
				.filter { it.status == DownloadStatus.DOWNLOADED && (it.filePath == null || storageManager.getFileSize(it.filePath) <= 0L) }
				.forEach { downloadDao.deleteDownload(it.songId) }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("DownloadManager", "could not reconcile downloads", e)
		}
	}

	// activeDownloads only lives in memory, so it's always empty right after
	// process start. A DOWNLOADING row left behind by a killed/crashed process
	// (backgrounded mid-download, OOM, force-stop) has no Job backing it and
	// nothing ever revisits it -- it just sits there forever, and because
	// getCollectionDownloadStatus() reports DOWNLOADING if *any* song in a
	// collection is DOWNLOADING, that one orphaned row makes the whole
	// playlist look like it's perpetually downloading. Re-launch a real
	// download for each one found at startup so it either completes or goes
	// through the normal retry/FAILED path instead of staying a zombie.
	private suspend fun resumeOrphanedDownloads() {
		if (!sessionManager.isLoggedIn.value) return
		val orphaned = downloadDao.getAllDownloadsList()
			.filter { it.status == DownloadStatus.DOWNLOADING }
		if (orphaned.isEmpty()) return

		Logger.i("DownloadManager", "resuming ${orphaned.size} orphaned download(s) from a previous process")
		orphaned.forEach { entity ->
			val song = songDao.getSongById(entity.songId)?.toDomainModel()
			if (song != null) {
				downloadSong(song)
			} else {
				Logger.w("DownloadManager", "orphaned download ${entity.songId} has no matching song, dropping")
				downloadDao.deleteDownload(entity.songId)
			}
		}
	}

	// The DB can say DOWNLOADED for a file that's no longer actually on disk
	// (storage cleared, download folder wiped outside the app, etc). Playback
	// must never trust that blindly -- pointing ExoPlayer at a missing local
	// file fails outright instead of falling back to streaming, so verify the
	// file is really there before handing back a path.
	fun getDownloadedFilePath(songId: String): String? {
		val path = downloadedSongs.value?.get(songId) ?: return null
		return path.takeIf { storageManager.getFileSize(it) > 0 }
	}

	fun downloadSong(song: DomainSong): Job = scope.launch(Dispatchers.IO) {
		if (!sessionManager.isLoggedIn.value) return@launch
		val currentJob = coroutineContext[Job]!!
		// Check and register together. A second tap waits for the actual work
		// rather than starting another writer or reporting completion early.
		val existingJob = activeDownloadsMutex.withLock {
			activeDownloads[song.id]?.also { return@withLock it }
			activeDownloads[song.id] = currentJob
			activeDownloadCount.value = activeDownloads.size
			null
		}
		if (existingJob != null) {
			existingJob.join()
			return@launch
		}
		try {
			if (isDownloaded(song.id)) return@launch
			downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.DOWNLOADING))
			downloadSemaphore.withPermit { executeDownloadProcessWithRetry(song) }
		} finally {
			withContext(NonCancellable) {
				if (!currentJob.isActive && !backgroundLimitReached) {
					val download = downloadDao.getDownloadById(song.id)
					if (download?.status == DownloadStatus.DOWNLOADING) {
						downloadDao.deleteDownload(song.id)
					}
				}
				activeDownloadsMutex.withLock {
					if (activeDownloads[song.id] === currentJob) activeDownloads.remove(song.id)
					activeDownloadCount.value = activeDownloads.size
				}
			}
		}
	}

	// Keeps the download process alive while work is in flight: requests the
	// notification permission once (best-effort, so the progress notification
	// can actually show on API 33+) and (re-)starts the platform's background
	// download mechanism, which on Android is a foreground service. Safe to
	// call repeatedly -- starting an already-running service is a no-op.
	private fun ensureBackgroundExecution() {
		if (backgroundLimitReached) return
		if (!notificationPermissionRequested) {
			notificationPermissionRequested = true
			scope.launch {
				try { permissionManager.requestNotificationPermission() }
				catch (e: CancellationException) { throw e }
				catch (e: Exception) { Logger.w("DownloadManager", "Notification permission unavailable: ${e.message}") }
			}
		}
		try {
			startBackgroundDownloadService(coilPlatformContext)
		} catch (e: Exception) {
			// Android can refuse a foreground-service start during process
			// restoration. Keep the queue usable in the open app.
			Logger.w("DownloadManager", "Background download service unavailable: ${e.message}")
		}
	}

	suspend fun downloadCollection(collection: DomainSongCollection) {
		collection.songs
			.filter { !isDownloaded(it.id) }
			.forEach { downloadSong(it) }
	}

	fun downloadEntireLibrary(songs: List<DomainSong>) {
		if (isDownloadingLibrary.value) return
		isDownloadingLibrary.value = true

		libraryDownloadJob = scope.launch(Dispatchers.IO) {
			try {
				isDownloadingLibrary.value = true
				libraryDownloadProgress.value = 0f

				val songsToDownload = songs.filter { !isDownloaded(it.id) }
				val totalToDownload = songsToDownload.size

				if (totalToDownload == 0) {
					isDownloadingLibrary.value = false
					libraryDownloadProgress.value = 1f
					return@launch
				}

				val downloadQueue = Channel<DomainSong>(Channel.UNLIMITED)
				songsToDownload.forEach { downloadQueue.trySend(it) }
				downloadQueue.close()

				var processedCount = 0
				val progressMutex = Mutex()

				val workers = List(MAX_CONCURRENT_DOWNLOADS) {
					launch {
						for (song in downloadQueue) {
							downloadSong(song).join()

							progressMutex.withLock {
								processedCount++
								libraryDownloadProgress.value =
									processedCount.toFloat() / totalToDownload.toFloat()
							}
						}
					}
				}

				workers.joinAll()
				isDownloadingLibrary.value = false

			} catch (_: CancellationException) {
				libraryDownloadProgress.value = 0f
			} finally {
				isDownloadingLibrary.value = false
			}
		}
	}

	/**
	 * The platform ran out of background time (Android 15+ caps data-sync foreground services at
	 * 6 hours per 24). Stop the work without deleting rows so it can resume later.
	 */
	fun suspendForBackgroundLimit() {
		backgroundLimitReached = true
		scope.launch(Dispatchers.IO) {
			libraryDownloadJob?.cancelAndJoin()
			libraryDownloadJob = null
			isDownloadingLibrary.value = false
			libraryDownloadProgress.value = 0f
			val jobs = activeDownloadsMutex.withLock { activeDownloads.values.toList() }
			jobs.forEach { it.cancel() }
			jobs.joinAll()
		}
	}

	/** Called when an Activity starts: lifts a background-limit suspension and resumes leftover rows. */
	fun onAppForegrounded() {
		backgroundLimitReached = false
		scope.launch(Dispatchers.IO) { resumeOrphanedDownloads() }
	}

	fun cancelAllActiveDownloads() {
		scope.launch(Dispatchers.IO) { cancelAllDownloadsAndWait() }
	}

	private suspend fun cancelAllDownloadsAndWait() {
		coverSyncJob?.cancelAndJoin()
		libraryDownloadJob?.cancelAndJoin()
		libraryDownloadJob = null
		isDownloadingLibrary.value = false
		libraryDownloadProgress.value = 0f
		val jobs = activeDownloadsMutex.withLock { activeDownloads.values.toList() }
		jobs.forEach { it.cancel() }
		jobs.joinAll()
	}

	fun cancelDownload(songId: String) {
		scope.launch(Dispatchers.IO) { cancelDownloadAndWait(songId) }
	}

	private suspend fun cancelDownloadAndWait(songId: String) {
		val job = activeDownloadsMutex.withLock { activeDownloads[songId] }
		job?.cancelAndJoin()
		val existing = downloadDao.getDownloadById(songId)
		if (existing?.status == DownloadStatus.DOWNLOADING || existing?.status == DownloadStatus.FAILED) {
			downloadDao.deleteDownload(songId)
		}
	}

	fun cancelCollectionDownload(collection: DomainSongCollection) {
		collection.songs.forEach { song ->
			cancelDownload(song.id)
		}
	}

	fun deleteDownload(songId: String) {
		scope.launch {
			cancelDownloadAndWait(songId)
			val download = downloadDao.getDownloadById(songId)
			download?.filePath?.let { storageManager.deleteFile(it) }
			downloadDao.deleteDownload(songId)
			scheduleCoverPrune()
		}
	}

	fun deleteDownloadedCollection(collection: DomainSongCollection) {
		collection.songs.forEach { song ->
			deleteDownload(song.id)
		}
	}

	suspend fun isDownloaded(songId: String): Boolean {
		val download = downloadDao.getDownloadById(songId) ?: return false
		if (download.status != DownloadStatus.DOWNLOADED) return false
		val path = download.filePath ?: return false
		return storageManager.getFileSize(path) > 0
	}

	fun getCollectionDownloadStatus(songIds: List<String>): Flow<DownloadStatus> {
		val wanted = songIds.toHashSet()
		return allDownloads.map { downloads ->
			val collectionDownloads = downloads.filter { it.songId in wanted }
			when {
				collectionDownloads.isEmpty() -> DownloadStatus.NOT_DOWNLOADED
				collectionDownloads.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
				collectionDownloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
				(collectionDownloads.size == wanted.size &&
					collectionDownloads.all { it.status == DownloadStatus.DOWNLOADED })
					-> DownloadStatus.DOWNLOADED

				else -> DownloadStatus.NOT_DOWNLOADED
			}
		}.distinctUntilChanged().flowOn(Dispatchers.Default)
	}

	suspend fun clearSession(deleteOfflineFiles: Boolean) {
		cancelAllDownloadsAndWait()
		if (deleteOfflineFiles) {
			storageManager.clearDownloads()
			downloadDao.clearAllDownloads()
			downloadedCovers.clear()
		} else {
			downloadDao.getAllDownloadsList().filter { it.status != DownloadStatus.DOWNLOADED }.forEach { row ->
				row.filePath?.let(storageManager::deleteFile)
				downloadDao.deleteDownload(row.songId)
			}
		}
		transferProgress.value = emptyMap()
	}

	fun clearAllDownloads() {
		scope.launch(Dispatchers.IO) {
			cancelAllDownloadsAndWait()
			storageManager.clearDownloads()
			downloadDao.clearAllDownloads()
			downloadedCovers.clear()
			Logger.i("DownloadManager", "cleared all downloads")
		}
	}

	// Retries a failed download with exponential backoff before giving up.
	// Doesn't touch activeDownloads bookkeeping -- that's the caller's job
	// (downloadSong), since this can take multiple attempts/minutes and the
	// job needs to stay registered as active for the whole retry sequence.
	private suspend fun executeDownloadProcessWithRetry(song: DomainSong) {
		var attempt = 0
		while (true) {
			// Restored transfers and retries must honor Forced offline / Wi-Fi
			// settings too. Keep the queued row cancellable while waiting.
			connectivityManager.isOnline.first { it }
			// Work is registered before the service observes the counter.
			ensureBackgroundExecution()
			try {
				executeDownloadProcess(song, attempt)
				return
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("DownloadManager", "download attempt $attempt failed for ${song.id}", e)
				if (attempt >= MAX_DOWNLOAD_RETRIES || e is ClientRequestException || e is InvalidAudioResponseException) {
					downloadDao.insertDownload(
						DownloadEntity(song.id, DownloadStatus.FAILED, 0f, retryCount = attempt)
					)
					return
				}
				attempt++
				val backoffMs =
					(RETRY_BASE_DELAY_MS shl (attempt - 1)).coerceAtMost(RETRY_MAX_DELAY_MS)
				delay(backoffMs)
			}
		}
	}

	private suspend fun executeDownloadProcess(song: DomainSong, attempt: Int) {
		Logger.i("DownloadManager", "beginning download for ${song.id} (attempt ${attempt + 1})")
		downloadDao.insertDownload(
			DownloadEntity(song.id, DownloadStatus.DOWNLOADING, 0f, retryCount = attempt)
		)

		coroutineScope {
			// Artwork and lyrics should not delay the audio transfer or turn a
			// playable song into a failed download when a metadata service fails.
			launch { saveCovers(song) }
			launch { withTimeoutOrNull(15_000L) { cacheLyrics(song) } }
			downloadAudioFile(song)
		}
	}

	private suspend fun saveCovers(song: DomainSong) {
		val albumCover = try {
			song.albumId?.let { albumDao.getAlbumEntitiesByIds(listOf(it)).firstOrNull()?.coverArtId }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w("DownloadManager", "Album cover unavailable: ${e.message}")
			null
		}
		newestCoverIds(listOf(albumCover, song.coverArtId)).values.forEach { saveCover(it) }
	}

	/** Returns false if the cover couldn't be saved. An older copy, if any, stays in place. */
	private suspend fun saveCover(coverId: String): Boolean = try {
		withTimeoutOrNull(COVER_TIMEOUT_MS) {
			downloadedCovers.store(coverId, SingletonImageLoader.get(coilPlatformContext).diskCache) { target ->
				client.prepareRequest(sessionManager.getCoverArtUrl(coverId)) {
					method = HttpMethod.Get
				}.execute { response ->
					// Navidrome reports "Artwork not found" as JSON with a 200.
					val contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
					check(contentType.startsWith("image/")) { "Not an image: $contentType" }
					storageManager.saveFile(target.toString(), response.bodyAsChannel())
				}
			}
			true
		} ?: false
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		Logger.w("DownloadManager", "Cover $coverId unavailable: ${e.message}")
		false
	}

	private class WantedCovers(val coverIds: Map<String, String>, val isComplete: Boolean)

	/**
	 * Covers of every downloaded or downloading song and of its album. [WantedCovers.isComplete]
	 * is false when a song's cover can't be told (the cache database doesn't know it), which
	 * means the set can't be used to decide what to delete.
	 */
	private suspend fun wantedCovers(): WantedCovers {
		val songIds = downloadDao.getAllDownloadsList()
			.filter { it.status == DownloadStatus.DOWNLOADED || it.status == DownloadStatus.DOWNLOADING }
			.map { it.songId }
		val songs = songIds.chunked(QUERY_CHUNK_SIZE).flatMap { songDao.getSongsByIds(it) }
		val albumCovers = songs.mapNotNull { it.belongsToAlbumId }.distinct()
			.chunked(QUERY_CHUNK_SIZE)
			.flatMap { albumDao.getAlbumEntitiesByIds(it) }
			.map { it.coverArtId }
		return WantedCovers(
			coverIds = newestCoverIds(albumCovers + songs.map { it.coverArtId }),
			isComplete = songs.size == songIds.size && songs.none { it.coverArtId.isNullOrBlank() }
		)
	}

	/**
	 * Once per process, brings the saved covers in step with the downloads: removes ones no
	 * download needs, saves missing ones (songs downloaded before covers were kept, or whose
	 * cover failed at the time), and picks up changed ones. Runs one at a time in the
	 * background because it can be thousands of covers; whatever is left waits for next launch.
	 */
	private suspend fun syncDownloadedCovers() {
		try {
			val wanted = wantedCovers()
			if (wanted.isComplete) downloadedCovers.retainOnly(wanted.coverIds.keys)
			var failuresInARow = 0
			for (coverId in wanted.coverIds.values) {
				if (!sessionManager.isLoggedIn.value) return
				if (downloadedCovers.isCurrent(coverId)) continue
				connectivityManager.isOnline.first { it }
				// Navidrome re-stamps covers on any rescan, so a new version is nearly always the
				// same picture. Replacing one can wait for Wi-Fi; a missing one can't.
				if (connectivityManager.isCellular.value && downloadedCovers.contains(coverId)) continue
				failuresInARow = if (saveCover(coverId)) 0 else failuresInARow + 1
				if (failuresInARow >= COVER_SYNC_MAX_FAILURES) return
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("DownloadManager", "could not sync downloaded covers", e)
		}
	}

	// Removing a playlist deletes its songs one by one, so wait for the last of them.
	private fun scheduleCoverPrune() {
		coverPruneJob?.cancel()
		coverPruneJob = scope.launch {
			delay(COVER_PRUNE_DELAY_MS)
			try {
				val wanted = wantedCovers()
				if (wanted.isComplete) downloadedCovers.retainOnly(wanted.coverIds.keys)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("DownloadManager", "could not remove unused covers", e)
			}
		}
	}

	private suspend fun cacheLyrics(song: DomainSong) {
		Logger.i("DownloadManager", "caching lyrics for ${song.id}")
		try {
			val lyricsResult = lyricsRepository.fetchLyrics(song)
			if (lyricsResult != null && lyricsResult.rawContent != null) {
				lyricDao.insertLyrics(
					LyricEntity(
						songId = song.id,
						rawContent = lyricsResult.rawContent,
						providerName = lyricsResult.providerName
					)
				)
				Logger.i("DownloadManager", "cached lyrics for ${song.id}")
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("DownloadManager", "Failed to cache lyrics for ${song.id}", e)
		}
	}

	private suspend fun downloadAudioFile(song: DomainSong) {
		var lastProgress = 0f
		val persistence = DownloadProgressGate(Clock.System.now().toEpochMilliseconds())

		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedDownloadTranscodingActive) {
			if (isCellular) preferenceManager.customDownloadMaxBitrateCellular else preferenceManager.customDownloadMaxBitrateWifi
		} else {
			val quality = if (isCellular) preferenceManager.downloadQualityCellular else preferenceManager.downloadQualityWifi
			if (platformType == PlatformType.Android) quality.bitrateAndroid else quality.bitrateIos
		}
		val container = if (preferenceManager.isAdvancedDownloadTranscodingActive) {
			if (isCellular) preferenceManager.customDownloadFormatCellular else preferenceManager.customDownloadFormatWifi
		} else {
			val quality = if (isCellular) preferenceManager.downloadQualityCellular else preferenceManager.downloadQualityWifi
			if (platformType == PlatformType.Android) quality.containerAndroid else quality.containerIos
		}

		val extension = container?.takeIf { it.isNotBlank() } ?: song.fileExtension

		val request = client.prepareRequest(
			sessionManager.api.getStreamUrl(
				id = song.id,
				maxBitRate = bitrate,
				format = container?.takeIf { it.isNotBlank() },
				// if this is true u get "stream was reset: INTERNAL_ERROR" for some reason
				estimateContentLength = false
			)
		) {
			method = HttpMethod.Get
			onDownload { bytesSentTotal, contentLength ->
				if (contentLength != null && contentLength > 0L) {
					val progress = (bytesSentTotal.toDouble() / contentLength).toFloat()
					if (progress - lastProgress >= 0.01f || progress == 1f) {
						lastProgress = progress
						transferProgress.update { it + (song.id to progress.coerceIn(0f, 1f)) }

						// Write inside the transfer callback. A detached progress job
						// could otherwise overwrite the final DOWNLOADED row.
						if (persistence.shouldPersist(progress, Clock.System.now().toEpochMilliseconds())) {
							downloadDao.updateProgress(song.id, DownloadStatus.DOWNLOADING, progress.coerceIn(0f, 1f))
						}
					}
				}
			}
		}

		val path = storageManager.getDownloadPath(song.id, extension)
		try {
			request.execute { response ->
				val contentType = response.headers[HttpHeaders.ContentType].orEmpty().substringBefore(';').lowercase()
				if (contentType in setOf("application/json", "application/xml", "text/xml", "text/html")) {
					throw InvalidAudioResponseException()
				}
				storageManager.saveFile(path, response.bodyAsChannel())
				check(storageManager.getFileSize(path) > 0L) { "The downloaded audio file is empty" }
				downloadDao.insertDownload(DownloadEntity(song.id, DownloadStatus.DOWNLOADED, 1f, path))
			}
		} catch (e: Exception) {
			withContext(NonCancellable) { storageManager.deleteFile(path) }
			throw e
		} finally { transferProgress.update { it - song.id } }
	}

	companion object {
		private const val MAX_CONCURRENT_DOWNLOADS = 4
		private const val ADOPT_MIN_AGE_MS = 5 * 60_000L
		private const val ORPHAN_FILE_MAX_AGE_MS = 7 * 24 * 60 * 60_000L
		private const val MAX_DOWNLOAD_RETRIES = 3
		private const val RETRY_BASE_DELAY_MS = 2_000L
		private const val RETRY_MAX_DELAY_MS = 30_000L
		private const val COVER_TIMEOUT_MS = 30_000L
		private const val COVER_SYNC_DELAY_MS = 15_000L
		private const val COVER_SYNC_MAX_FAILURES = 5
		private const val COVER_PRUNE_DELAY_MS = 2_000L
		private const val QUERY_CHUNK_SIZE = 500
	}
}

private class InvalidAudioResponseException : IllegalStateException("The server returned an error page instead of audio")
