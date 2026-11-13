package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.shared.PlaybackBoundary
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

sealed interface SleepTimerMode {
	data object Disabled : SleepTimerMode
	data class Time(val endTime: Instant) : SleepTimerMode
	data class Songs(val remaining: Int) : SleepTimerMode
	data object EndOfQueue : SleepTimerMode
}

class SleepTimerManager(
	private val player: MediaPlayerViewModel
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var job: Job? = null
	private var observerJob: Job? = null
	private var remainingSongs = 0

	private val _mode = MutableStateFlow<SleepTimerMode>(SleepTimerMode.Disabled)
	val mode = _mode.asStateFlow()

	val endTimeStamp: Instant?
		get() = (mode.value as? SleepTimerMode.Time)?.endTime

	val timeLeft: Duration?
		get() = endTimeStamp?.let { it - Clock.System.now() }

	fun startTimer(duration: Duration) {
		stopTimer()
		val endTime = Clock.System.now() + duration
		_mode.value = SleepTimerMode.Time(endTime)

		job = scope.launch {
			delay(duration)
			player.pauseLocal()
			stopTimer()
		}
	}

	fun startSongsTimer(count: Int) {
		stopTimer()
		remainingSongs = count.coerceAtLeast(1)
		_mode.value = SleepTimerMode.Songs(remainingSongs)
		observeSongs()
	}

	fun startEndOfQueueTimer() {
		stopTimer()
		if (player.localUiState.value.queue.isEmpty()) return
		_mode.value = SleepTimerMode.EndOfQueue
		player.setPauseAtSongEnd(true, queueEndOnly = true)
		observeSongs()
	}

	private fun observeSongs() {
		if (_mode.value is SleepTimerMode.Songs) player.setPauseAtSongEnd(remainingSongs == 1)
		observerJob = scope.launch {
			player.playbackBoundaries.collect { boundary ->
				when (boundary) {
					PlaybackBoundary.AutomaticAdvance -> if (_mode.value is SleepTimerMode.Songs) {
						remainingSongs--
						_mode.value = SleepTimerMode.Songs(remainingSongs.coerceAtLeast(1))
						player.setPauseAtSongEnd(remainingSongs <= 1)
					}
					PlaybackBoundary.PausedAtSongEnd, PlaybackBoundary.QueueEnded -> stopTimer()
				}
			}
		}
	}

	fun stopTimer() {
		player.setPauseAtSongEnd(false)
		job?.cancel()
		job = null
		observerJob?.cancel()
		observerJob = null
		remainingSongs = 0
		_mode.value = SleepTimerMode.Disabled
	}
}
