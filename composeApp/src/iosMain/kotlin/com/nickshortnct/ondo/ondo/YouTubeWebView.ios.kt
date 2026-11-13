package com.nickshortnct.ondo.ondo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// YouTube search/playback is off in the iPhone build (see mediaAcquisitionEnabled),
// so nothing ever shows this.
@Composable
actual fun YouTubeWebView(url: String, modifier: Modifier) = Unit
