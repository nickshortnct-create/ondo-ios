package com.nickshortnct.ondo.ui.screens.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.domain.models.UnifiedSearchResult

/** All previews a few matches; See all retains the query and opens its category. */
fun LazyGridScope.searchResultShelf(
    title: StringResource,
    results: List<UnifiedSearchResult>,
    onSeeAll: () -> Unit,
    itemContent: @Composable LazyItemScope.(UnifiedSearchResult) -> Unit
) {
    if (results.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onSeeAll) { Text("See all (${results.size})") }
        }
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(results.take(8), key = { it.key }, contentType = { it.kind }) { item -> itemContent(item) }
        }
    }
}
