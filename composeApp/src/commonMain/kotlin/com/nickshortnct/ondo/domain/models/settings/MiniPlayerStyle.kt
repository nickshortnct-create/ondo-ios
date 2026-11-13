package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_mini_player_style_detached
import ondo.composeapp.generated.resources.option_mini_player_style_unified
import org.jetbrains.compose.resources.StringResource

enum class MiniPlayerStyle(val displayName: StringResource) {
	Unified(Res.string.option_mini_player_style_unified),
	Detached(Res.string.option_mini_player_style_detached)
}
