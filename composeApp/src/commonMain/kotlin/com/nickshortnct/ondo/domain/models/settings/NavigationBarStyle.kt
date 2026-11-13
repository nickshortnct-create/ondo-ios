package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_navigation_bar_style_normal
import ondo.composeapp.generated.resources.option_navigation_bar_style_short
import org.jetbrains.compose.resources.StringResource

enum class NavigationBarStyle(val displayName: StringResource) {
	Normal(Res.string.option_navigation_bar_style_normal),
	Short(Res.string.option_navigation_bar_style_short)
}
