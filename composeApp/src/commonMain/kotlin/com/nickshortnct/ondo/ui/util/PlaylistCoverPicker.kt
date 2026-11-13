package com.nickshortnct.ondo.ui.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPlaylistCoverPicker(onPicked: (ByteArray?) -> Unit): () -> Unit
