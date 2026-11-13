package com.nickshortnct.ondo.ui.screens.search.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Check
import com.nickshortnct.ondo.ui.screens.search.SearchCategory

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreenChips(
	selectedCategory: SearchCategory,
	onCategorySelect: (SearchCategory) -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
			.selectableGroup(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		SearchCategory.entries.filter { it != SearchCategory.YOUTUBE || com.nickshortnct.ondo.ondo.mediaAcquisitionEnabled }.forEach { category ->
			val isSelected = category == selectedCategory
			FilterChip(
				modifier = Modifier
					.animateContentSize(
						if (isSelected)
							MaterialTheme.motionScheme.fastSpatialSpec()
						else MaterialTheme.motionScheme.defaultEffectsSpec()
					),
				selected = isSelected,
				onClick = {
					onCategorySelect(category)
				},
				label = {
					Text(
						category.res?.let { stringResource(it) } ?: "YouTube",
						maxLines = 1
					)
				},
				shape = MaterialTheme.shapes.small,
				leadingIcon = if (isSelected) {
					{
						Icon(
							imageVector = Icons.Outlined.Check,
							contentDescription = null,
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
		}
	}
}
