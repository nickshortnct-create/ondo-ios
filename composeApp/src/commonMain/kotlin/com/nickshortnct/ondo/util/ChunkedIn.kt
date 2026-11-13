package com.nickshortnct.ondo.util

/**
 * Runs an `IN (:ids)` style query in chunks so it never exceeds SQLite's bound-variable
 * limit (32,766 by default; Room expands one `?` per element). Results keep chunk order.
 */
suspend fun <T> chunkedIn(
	ids: Collection<String>,
	size: Int = 900,
	query: suspend (List<String>) -> List<T>
): List<T> = ids.distinct().chunked(size).flatMap { query(it) }
