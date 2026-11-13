package com.nickshortnct.ondo.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlaylistCoverPicker(onPicked: (ByteArray?) -> Unit): () -> Unit = {
	onPicked(null)
}
