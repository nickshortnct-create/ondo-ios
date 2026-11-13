package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_status_idle
import org.jetbrains.compose.resources.StringResource
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.SyncActionDao
import com.nickshortnct.ondo.data.database.entities.SyncActionEntity
import com.nickshortnct.ondo.data.database.entities.SyncActionType
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.util.Logger
import com.nickshortnct.ondo.util.findSerializationException
import dev.zt64.subsonic.api.model.SubsonicException
import io.ktor.client.plugins.ClientRequestException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.concurrent.Volatile

private val FOREGROUND_SYNC_INTERVAL = 15.minutes
private val BACKGROUND_SYNC_INTERVAL = 1.hours

data class SyncState(
	val isSyncing: Boolean = false,
	val progress: Float = 0f,
	val message: StringResource = Res.string.info_status_idle
)

class SyncManager(
	private val repository: DbRepository,
	private val syncDao: SyncActionDao,
	private val albumDao: AlbumDao,
	private val connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	// Everything a login can cancel (periodic loop, in-flight cycles) lives under this job.
	private val workJob = SupervisorJob()
	private val workScope = CoroutineScope(workJob + Dispatchers.IO)
	private var syncJob: Job? = null
	private val syncMutex = Mutex()
	private val queueMutex = Mutex()
	private val maintenanceMutex = Mutex()
	@Volatile private var maintenanceActive = false

	// The login screen owns the very first sync (it shows progress); the background loop stays
	// out of its way until that finishes.
	@Volatile private var initialSyncActive = false
	@Volatile private var lastCycle: TimeSource.Monotonic.ValueTimeMark? = null

	// A cheap "light" cycle runs every 15 minutes (hourly in the background); the heavy full library pull is once a day
	// and only on an unmetered network (or when the user asks for it).
	private val fullSyncThreshold = 24.hours

	val syncState: StateFlow<SyncState>
		field = MutableStateFlow(SyncState())

	init {
		scope.launch {
			sessionManager.isLoggedIn.collectLatest { loggedIn ->
				if (loggedIn) {
					startPeriodicSync()
				} else {
					stopPeriodicSync()
					workJob.children.forEach { it.cancel() }
				}
			}
		}
		scope.launch {
			connectivityManager.isOnline.collect { isOnline ->
				if (isOnline && canAutoSync() && !syncMutex.isLocked) {
					// Mobile data drops and returns often; send queued changes every time, but only pull the
					// library again if the last pull was a while ago.
					val recent = lastCycle?.let { it.elapsedNow() < FOREGROUND_SYNC_INTERVAL } == true
					workScope.launch { if (recent) processQueue() else runSyncCycle() }
				}
			}
		}
	}

	private fun canAutoSync() = sessionManager.isLoggedIn.value && !initialSyncActive && !maintenanceActive

	fun startPeriodicSync() {
		if (syncJob?.isActive == true) return
		Logger.i("SyncManager", "Starting periodic sync cycle.")

		syncJob = workScope.launch {
			while (isActive) {
				if (canAutoSync()) runSyncCycle()
				awaitNextCycle()
			}
		}
	}

	/**
	 * Every 15 minutes while the app is on screen, hourly while it's in the background (a sync wakes the
	 * phone's radio, and during a long listen nobody is looking at the library). Opening the app after a
	 * longer gap syncs straight away.
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	private suspend fun awaitNextCycle() {
		val started = TimeSource.Monotonic.markNow()
		withTimeoutOrNull(BACKGROUND_SYNC_INTERVAL) {
			AppVisibility.visible.transformLatest { visible ->
				if (visible) {
					delay(FOREGROUND_SYNC_INTERVAL - started.elapsedNow())
					emit(Unit)
				}
			}.first()
		}
	}

	/** Forces a full library sync; the returned job completes when it has finished. */
	fun triggerManualSync(): Job = workScope.launch {
		preferenceManager.lastFullSyncTime = 0
		runSyncCycle(forceFull = true)
	}

	fun stopPeriodicSync() {
		syncJob?.cancel()
		syncJob = null
		syncState.value = SyncState(isSyncing = false)
	}

	suspend fun clearSessionCache() = maintenanceMutex.withLock {
		maintenanceActive = true
		try {
			val jobs = workJob.children.toList()
			jobs.forEach { it.cancel() }
			jobs.forEach { it.join() }
			syncJob = null
			syncMutex.withLock { queueMutex.withLock { repository.removeEverything() } }
			preferenceManager.lastFullSyncTime = 0
		} finally {
			maintenanceActive = false
			syncState.value = SyncState()
		}
	}

	suspend fun rebuildLibraryCache() = maintenanceMutex.withLock {
		maintenanceActive = true
		try {
			syncJob?.cancelAndJoin()
			syncJob = null
			syncMutex.withLock {
				queueMutex.withLock { repository.removeEverything() }
				preferenceManager.lastFullSyncTime = 0
				if (sessionManager.isLoggedIn.value && connectivityManager.isOnline.value) {
					runFullSync { _, _ -> }.getOrThrow()
				}
			}
		} finally {
			maintenanceActive = false
			syncState.value = SyncState()
			if (sessionManager.isLoggedIn.value) startPeriodicSync()
		}
	}

	/** Called by login before the session flips to logged-in, so the background loop waits. */
	fun beginInitialSync() { initialSyncActive = true }

	fun endInitialSync() { initialSyncActive = false }

	/** The sync right after login: a full pull with progress, recorded so the loop doesn't repeat it. */
	suspend fun runInitialSync(onProgress: (Float, StringResource) -> Unit): Result<Unit> =
		syncMutex.withLock { runFullSync(onProgress) }

	fun enqueueAction(actionType: SyncActionType, itemId: String) {
		scope.launch {
			syncDao.enqueue(SyncActionEntity(actionType = actionType, itemId = itemId))
			// During a sync the action just waits in the queue: the pull protects it from being
			// overwritten (see DbRepository.keepPendingLocalState) and the cycle sends it afterwards.
			if (!syncMutex.isLocked && !maintenanceActive) processQueue()
		}
	}

	private suspend fun runSyncCycle(forceFull: Boolean = false) {
		if (maintenanceActive || !sessionManager.isLoggedIn.value || !connectivityManager.isOnline.value) return
		syncMutex.withLock {
			if (maintenanceActive || !sessionManager.isLoggedIn.value || !connectivityManager.isOnline.value) return@withLock
			lastCycle = TimeSource.Monotonic.markNow()
			processQueue()

			val lastFull = preferenceManager.lastFullSyncTime
			val neverSynced = lastFull <= 0L
			val fullDue = neverSynced ||
				Clock.System.now() - Instant.fromEpochMilliseconds(lastFull) > fullSyncThreshold
			// A first-ever sync on mobile data is allowed only when there is nothing cached to show.
			val fullAllowed = forceFull ||
				!connectivityManager.isCellular.value ||
				(neverSynced && albumDao.getAlbumCount() == 0)

			if (forceFull || (fullDue && fullAllowed)) {
				runFullSync { _, _ -> }
			} else {
				Logger.i("SyncManager", "Running light sync")
				repository.syncLight().onFailure { Logger.e("SyncManager", "Light sync failed", it) }
			}

			// Anything starred/rated while the pull was running goes out now.
			processQueue()
		}
	}

	private suspend fun runFullSync(onProgress: (Float, StringResource) -> Unit): Result<Unit> {
		Logger.i("SyncManager", "Starting full library pull...")
		val startedAt = Clock.System.now()

		syncState.update { it.copy(isSyncing = true) }

		val result = repository.syncEverything { progress, message ->
			syncState.update { it.copy(isSyncing = true, progress = progress, message = message) }
			onProgress(progress, message)
		}

		if (result.isSuccess) {
			preferenceManager.lastFullSyncTime = startedAt.toEpochMilliseconds()
			Logger.i("SyncManager", "Full library sync complete.")
		}

		syncState.update { it.copy(isSyncing = false, message = Res.string.info_status_idle) }
		return result
	}

	private suspend fun processQueue() = queueMutex.withLock {
		if (!connectivityManager.isOnline.value) return@withLock
		val actions = syncDao.getPendingActions()
		if (actions.isEmpty()) return@withLock

		drainActions(
			actions = actions,
			isOnline = { connectivityManager.isOnline.value },
			send = ::sendAction,
			store = object : SyncActionStore {
				override suspend fun remove(id: Int) = syncDao.removeAction(id)
				override suspend fun recordFailure(id: Int, error: String?) = syncDao.recordFailure(id, error)
			}
		)
	}

	private suspend fun sendAction(action: SyncActionEntity) {
		when (action.actionType) {
			SyncActionType.STAR -> sessionManager.api.star(action.itemId)
			SyncActionType.UNSTAR -> sessionManager.api.unstar(action.itemId)
			SyncActionType.DELETE_PLAYLIST -> sessionManager.api.deletePlaylist(action.itemId)
			// Offline plays are submitted with the time they were played, not the time we got back online.
			SyncActionType.SCROBBLE -> sessionManager.api.scrobble(
				action.itemId,
				action.createdAt.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds) ?: Clock.System.now(),
				true
			)

			SyncActionType.STAR_0 -> sessionManager.api.setRating(action.itemId, 0)
			SyncActionType.STAR_1 -> sessionManager.api.setRating(action.itemId, 1)
			SyncActionType.STAR_2 -> sessionManager.api.setRating(action.itemId, 2)
			SyncActionType.STAR_3 -> sessionManager.api.setRating(action.itemId, 3)
			SyncActionType.STAR_4 -> sessionManager.api.setRating(action.itemId, 4)
			SyncActionType.STAR_5 -> sessionManager.api.setRating(action.itemId, 5)
		}
	}
}

/** Actions the server keeps rejecting are dropped after this many attempts. */
internal const val MAX_ACTION_ATTEMPTS = 3

internal interface SyncActionStore {
	suspend fun remove(id: Int)
	suspend fun recordFailure(id: Int, error: String?)
}

/**
 * A rejection (the server understood the request and said no, or sent something unreadable) will
 * fail the same way next time; anything else (offline, timeout, 5xx) is worth retrying later.
 */
internal fun isPermanentRejection(e: Throwable): Boolean =
	e is SubsonicException || e is ClientRequestException || e.findSerializationException() != null

/**
 * Sends queued actions in order. A transient failure (network) stops the run so order is
 * preserved; a rejection counts an attempt and the run continues with the next action, so
 * one bad action can never block everything behind it.
 */
internal suspend fun drainActions(
	actions: List<SyncActionEntity>,
	isOnline: () -> Boolean,
	send: suspend (SyncActionEntity) -> Unit,
	store: SyncActionStore,
	isRejection: (Throwable) -> Boolean = ::isPermanentRejection,
	log: (String, Throwable?) -> Unit = { message, error -> if (error != null) Logger.w("SyncManager", message, error) else Logger.i("SyncManager", message) }
) {
	for (action in actions) {
		if (!isOnline()) return
		try {
			send(action)
			store.remove(action.id)
			log("Successfully synced ${action.actionType} for ${action.itemId}", null)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			if (!isRejection(e)) {
				log("Network failed. Action left in queue.", e)
				return
			}
			if (action.attempts + 1 >= MAX_ACTION_ATTEMPTS) {
				log("Server keeps rejecting ${action.actionType} for ${action.itemId}; dropping it", e)
				store.remove(action.id)
			} else {
				log("Server rejected ${action.actionType} for ${action.itemId}; will retry", e)
				store.recordFailure(action.id, e.message)
			}
		}
	}
}
