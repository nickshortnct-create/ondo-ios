package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_position_bottom
import ondo.composeapp.generated.resources.option_position_top
import org.jetbrains.compose.resources.StringResource

enum class ToolbarPosition(val displayName: StringResource) {
	Top(Res.string.option_position_top),
	Bottom(Res.string.option_position_bottom)
}
