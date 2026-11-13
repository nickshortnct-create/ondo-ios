package com.nickshortnct.ondo.ui.screens.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.ondo.acquisitionKey
import com.nickshortnct.ondo.ondo.MusicResult
import com.nickshortnct.ondo.ondo.OndoDownloads
import com.nickshortnct.ondo.ondo.previewImageUrl
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.navigation.Screen

/** A song can be added without a second confirmation sheet or choosing a provider. */
@Composable
fun DiscoverySongRow(
    item: MusicResult,
    downloads: OndoDownloads,
    alternativeSources: List<MusicResult> = emptyList(),
    enabled: Boolean,
    onOpen: () -> Unit,
    onRequested: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val nav = LocalNavStack.current
    val preferences = koinInject<PreferenceManager>()
    var localBusy by remember(acquisitionKey(item)) { mutableStateOf(false) }
    val requestedItems by downloads.requestedItems.collectAsStateWithLifecycle()
    val pendingItems by downloads.pendingItems.collectAsStateWithLifecycle()
    val requestKey = acquisitionKey(item)
    val busy = localBusy || requestKey in pendingItems
    val requested = requestKey in requestedItems
    var error by remember(item) { mutableStateOf<String?>(null) }
    var sourceIndex by remember(acquisitionKey(item)) { mutableStateOf(0) }
    val sources = remember(item, alternativeSources) { (listOf(item) + alternativeSources).distinctBy { "${it.service}:${it.id}" } }
    Column {
        ListItem(
            modifier = Modifier.clickable(onClick = onOpen),
            headlineContent = { Text(item.title) },
            supportingContent = { Text(item.artist.ifBlank { "Song" } + if (requested) " · Adding to library" else " · Add to your library") },
            leadingContent = {
                CoverArt(coverArtId = null, localImageUri = item.previewImageUrl(), modifier = Modifier.size(50.dp), shape = preferences.coverArtShape.decreasedShape)
            },
            trailingContent = {
                TextButton(
                    enabled = enabled && !busy,
                    onClick = {
                        if (requested) nav.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.Downloads))
                        else if (!busy) {
                            localBusy = true
                            if (error != null && sources.size > 1) sourceIndex = (sourceIndex + 1) % sources.size
                            error = null
                            scope.launch {
                                try {
                                    downloads.acquire(sources[sourceIndex.coerceAtMost(sources.lastIndex)])
                                    onRequested()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "Couldn't add this song. Try again." }
                                finally { localBusy = false }
                            }
                        }
                    }
                ) { Text(if (busy) "Adding…" else if (requested) "View progress" else if (error != null && sources.size > 1) "Try another" else if (error != null) "Retry" else "Add") }
            }
        )
        error?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
    }
}
