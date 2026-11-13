package com.nickshortnct.ondo.ui.screens.album.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainFilter
import com.nickshortnct.ondo.domain.models.settings.ListViewMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Sort
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton
import com.nickshortnct.ondo.ui.components.sheets.SortSheet
import com.nickshortnct.ondo.ui.util.label

@Composable
fun AlbumListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainAlbumListType,
	onSetSorting: (DomainAlbumListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit,
	selectedViewMode: ListViewMode,
	onSetViewMode: (ListViewMode) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember {
		persistentListOf(
			DomainAlbumListType.AlphabeticalByArtist,
			DomainAlbumListType.AlphabeticalByName,
			DomainAlbumListType.Frequent,
			DomainAlbumListType.Recent,
			DomainAlbumListType.Newest,
			DomainAlbumListType.Highest,
			DomainAlbumListType.Random,
			DomainAlbumListType.Year
		)
	}
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
			label = { it.label() },
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
