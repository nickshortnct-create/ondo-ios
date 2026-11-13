package com.nickshortnct.ondo.ondo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class DownloadSearchOutcome(
	val results: List<MusicResult> = emptyList(),
	val failures: List<String> = emptyList(),
	val isSearching: Boolean = false
)

/** Each completed source is visible immediately; a slow source has a bounded wait. */
internal fun searchSources(
	services: List<String>,
	timeoutMillis: Long = 8_000,
	search: suspend (String) -> List<MusicResult>
): Flow<DownloadSearchOutcome> = channelFlow {
	val sources = services.distinct()
	send(DownloadSearchOutcome(isSearching = sources.isNotEmpty()))
	val completed = mutableMapOf<String, DownloadSearchOutcome>()
	val mutex = Mutex()
	for (service in sources) {
		launch {
			val outcome = try {
				val hits = withTimeoutOrNull(timeoutMillis) { search(service) }
				if (hits == null) DownloadSearchOutcome(failures = listOf("$service timed out"))
				else DownloadSearchOutcome(results = hits)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Transport messages can contain URLs or credentials. Keep the UI clear.
				DownloadSearchOutcome(failures = listOf("$service is temporarily unavailable"))
			}
			mutex.withLock {
				completed[service] = outcome
				val ordered = sources.mapNotNull(completed::get)
				send(DownloadSearchOutcome(
					results = ordered.flatMap { it.results },
					failures = ordered.flatMap { it.failures },
					isSearching = completed.size < sources.size
				))
			}
		}
	}
}
