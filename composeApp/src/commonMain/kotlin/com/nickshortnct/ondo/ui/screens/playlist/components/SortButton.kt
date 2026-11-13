package com.nickshortnct.ondo.ui.screens.playlist.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.DomainPlaylistListType
import com.nickshortnct.ondo.domain.models.settings.ListViewMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Sort
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton
import com.nickshortnct.ondo.ui.components.sheets.SortSheet

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainPlaylistListType,
	onSetSorting: (DomainPlaylistListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit,
	selectedViewMode: ListViewMode,
	onSetViewMode: (ListViewMode) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember { DomainPlaylistListType.entries.toImmutableList() }
	var expanded by remember { mutableStateOf(false) }
	if (!nested) {
		IconButton(onClick = {
			expanded = true
		}) {
			Icon(
				Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	} else {
		TopBarButton({ expanded = true }) {
			Icon(
				Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	}
	if (expanded) {
		SortSheet(
			entries = entries,
			selectedSorting = selectedSorting,
			selectedReversed = selectedReversed,
			label = { stringResource(it.displayName) },
			onSetSorting = onSetSorting,
			onSetReversed = onSetReversed,
			onDismissRequest = { expanded = false },
			selectedViewMode = selectedViewMode,
			onSetViewMode = onSetViewMode,
			selectedFilters = selectedFilters,
			onToggleFilter = onToggleFilter
		)
	}
}
