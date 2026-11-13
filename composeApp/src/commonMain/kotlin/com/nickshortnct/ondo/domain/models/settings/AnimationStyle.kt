package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_animation_style_expressive
import ondo.composeapp.generated.resources.option_animation_style_standard
import org.jetbrains.compose.resources.StringResource

enum class AnimationStyle(val displayName: StringResource) {
	Expressive(Res.string.option_animation_style_expressive),
	Standard(Res.string.option_animation_style_standard)
}
