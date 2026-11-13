package com.nickshortnct.ondo.domain.models.settings

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_now_playing_slider_style_flat
import ondo.composeapp.generated.resources.option_now_playing_slider_style_slim
import ondo.composeapp.generated.resources.option_now_playing_slider_style_squiggly
import ondo.composeapp.generated.resources.option_now_playing_slider_style_yoyo
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingSliderStyle(val displayName: StringResource) {
	Flat(Res.string.option_now_playing_slider_style_flat),
	Squiggly(Res.string.option_now_playing_slider_style_squiggly),
	Slim(Res.string.option_now_playing_slider_style_slim),
	Yoyo(Res.string.option_now_playing_slider_style_yoyo)
}
