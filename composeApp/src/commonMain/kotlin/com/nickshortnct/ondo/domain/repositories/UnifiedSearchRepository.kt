package com.nickshortnct.ondo.domain.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import com.nickshortnct.ondo.domain.models.UnifiedSearchResult
import com.nickshortnct.ondo.domain.models.mergeSearchResults
import com.nickshortnct.ondo.domain.models.normalizeSearchQuery
import com.nickshortnct.ondo.ondo.DownloadSearchOutcome
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.mediaAcquisitionEnabled

data class UnifiedSearchOutcome(
	val results: List<UnifiedSearchResult>,
	val failures: List<String>,
	val isSearching: Boolean = false
)

class UnifiedSearchRepository(
	private val searchRepository: SearchRepository,
	private val downloads: OndoDownloads
) {
	val libraryRevision get() = downloads.libraryRevision

	suspend fun search(query: String): UnifiedSearchOutcome = searchFlow(query).last()

	fun searchFlow(query: String): Flow<UnifiedSearchOutcome> {
		val cleaned = normalizeSearchQuery(query)
		if (cleaned.isEmpty()) return flowOf(UnifiedSearchOutcome(emptyList(), emptyList()))
		val discovery = if (mediaAcquisitionEnabled && searchRepository.isOnline.value && cleaned.length >= 2) downloads.searchAllFlow(cleaned)
			else flowOf(DownloadSearchOutcome())
		return combine(searchRepository.searchFlow(cleaned), discovery) { library, discovered ->
			UnifiedSearchOutcome(
				results = mergeSearchResults(cleaned, library.results, discovered.results),
				failures = (library.failures + discovered.failures).distinct(),
				isSearching = library.isSearching || discovered.isSearching
			)
		}.flowOn(Dispatchers.Default)
	}
}
