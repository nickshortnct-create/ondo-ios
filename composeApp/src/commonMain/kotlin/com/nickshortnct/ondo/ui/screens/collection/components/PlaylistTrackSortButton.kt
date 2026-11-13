package com.nickshortnct.ondo.ui.screens.collection.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_sort_ascending
import ondo.composeapp.generated.resources.option_sort_descending
import ondo.composeapp.generated.resources.title_sort_by
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Sort
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton

@Composable
internal fun PlaylistTrackSortButton(
	selectedSort: PlaylistTrackSort,
	descending: Boolean,
	onSelectSort: (PlaylistTrackSort) -> Unit,
	onSetDescending: (Boolean) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		TopBarButton(onClick = { expanded = true }) {
			Icon(Icons.Outlined.Sort, contentDescription = stringResource(Res.string.title_sort_by))
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			PlaylistTrackSort.entries.forEach { sort ->
				DropdownMenuItem(
					text = { Text(stringResource(sort.label)) },
					leadingIcon = { RadioButton(selected = selectedSort == sort, onClick = null) },
					onClick = {
						onSelectSort(sort)
						onSetDescending(sort == PlaylistTrackSort.ReleaseYear)
						expanded = false
					}
				)
			}
			HorizontalDivider()
			DropdownMenuItem(
				text = { Text(stringResource(Res.string.option_sort_ascending)) },
				leadingIcon = { RadioButton(selected = !descending, onClick = null) },
				onClick = { onSetDescending(false); expanded = false }
			)
			DropdownMenuItem(
				text = { Text(stringResource(Res.string.option_sort_descending)) },
				leadingIcon = { RadioButton(selected = descending, onClick = null) },
				onClick = { onSetDescending(true); expanded = false }
			)
		}
	}
}
