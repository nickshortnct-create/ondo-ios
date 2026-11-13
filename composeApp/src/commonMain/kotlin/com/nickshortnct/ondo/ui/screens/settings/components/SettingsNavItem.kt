package com.nickshortnct.ondo.ui.screens.settings.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.ChevronForward
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsNavItem(
	onClick: () -> Unit,
	enabled: Boolean = true,
	shapes: ListItemShapes,
	leadingContent: @Composable (() -> Unit)? = null,
	supportingContent: @Composable (() -> Unit)? = null,
	content: @Composable () -> Unit
) {
	SegmentedListItem(
		onClick = onClick,
		enabled = enabled,
		shapes = shapes,
		supportingContent = supportingContent,
		leadingContent = leadingContent,
		trailingContent = { Icon(Icons.Outlined.ChevronForward, null) },
		content = content
	)
}
