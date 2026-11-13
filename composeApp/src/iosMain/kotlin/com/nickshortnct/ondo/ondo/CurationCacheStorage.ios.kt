package com.nickshortnct.ondo.ondo

/** iOS keeps the curation in memory only for now; it's fetched again on launch. */
actual class CurationCacheStorage {
	actual fun read(): String? = null

	actual fun write(text: String) = Unit
}
