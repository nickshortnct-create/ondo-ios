package com.nickshortnct.ondo.domain.models.settings

import androidx.compose.ui.graphics.vector.ImageVector
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_list_view_mode_grid
import ondo.composeapp.generated.resources.option_list_view_mode_list
import org.jetbrains.compose.resources.StringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Grid
import com.nickshortnct.ondo.icons.outlined.List

enum class ListViewMode(val displayName: StringResource, val icon: ImageVector) {
	Grid(Res.string.option_list_view_mode_grid, Icons.Outlined.Grid),
	List(Res.string.option_list_view_mode_list, Icons.Outlined.List)
}
