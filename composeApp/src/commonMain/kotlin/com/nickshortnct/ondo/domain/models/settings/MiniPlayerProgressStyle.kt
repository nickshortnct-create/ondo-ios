package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_mini_player_progress_style_hidden
import ondo.composeapp.generated.resources.option_mini_player_progress_style_seekable
import ondo.composeapp.generated.resources.option_mini_player_progress_style_visible
import org.jetbrains.compose.resources.StringResource

enum class MiniPlayerProgressStyle(val displayName: StringResource) {
	Hidden(Res.string.option_mini_player_progress_style_hidden),
	Visible(Res.string.option_mini_player_progress_style_visible),
	Seekable(Res.string.option_mini_player_progress_style_seekable)
}
