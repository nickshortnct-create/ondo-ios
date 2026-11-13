package com.nickshortnct.ondo.domain.models.settings

import androidx.compose.runtime.Composable
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_quality_high
import ondo.composeapp.generated.resources.option_quality_lossless
import ondo.composeapp.generated.resources.option_quality_low
import ondo.composeapp.generated.resources.option_quality_medium
import org.jetbrains.compose.resources.StringResource
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.PlatformType

enum class StreamingQuality(
	val displayName: StringResource,
	val bitrateAndroid: Int,
	val bitrateIos: Int,
	val containerAndroid: String?,
	val containerIos: String?
) {
	Low(
		displayName = Res.string.option_quality_low,
		bitrateAndroid = 80,
		bitrateIos = 96,
		containerAndroid = "opus",
		containerIos = "aac"
	),
	Medium(
		displayName = Res.string.option_quality_medium,
		bitrateAndroid = 128,
		bitrateIos = 160,
		containerAndroid = "opus",
		containerIos = "aac"
	),
	High(
		displayName = Res.string.option_quality_high,
		bitrateAndroid = 192,
		bitrateIos = 256,
		containerAndroid = "opus",
		containerIos = "aac"
	),
	Lossless(
		displayName = Res.string.option_quality_lossless,
		bitrateAndroid = 0,
		bitrateIos = 0,
		containerAndroid = null,
		containerIos = null
	)
}

@Composable
fun StreamingQuality.description(): String? {
	val platformContext = LocalPlatformContext.current
	return if (containerAndroid != null && containerIos != null) {
		if (platformContext.platformType == PlatformType.IOS) {
			"${bitrateIos}kbps, ${containerIos.uppercase()}"
		} else {
			"${bitrateAndroid}kbps, ${containerAndroid.uppercase()}"
		}
	} else {
		null
	}
}
