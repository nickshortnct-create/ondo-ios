package com.nickshortnct.ondo.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.domain.repositories.CollectionRepository
import org.koin.compose.koinInject

@Composable
fun rememberHydratedCollection(collection: DomainSongCollection, active: Boolean): DomainSongCollection? {
	val repository = koinInject<CollectionRepository>()
	val resolved by produceState<DomainSongCollection?>(collection.takeIf { it.songs.isNotEmpty() || it.songCount == 0 }, collection.id, active) {
		if (active) repository.getCollectionFlow(false, collection.id).collect { state ->
			state.data?.let { value = it }
		}
	}
	return resolved
}
