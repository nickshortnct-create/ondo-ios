package com.nickshortnct.ondo.ui.screens.queue.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager

class QueueViewModel(
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager
) : ViewModel() {
	val listState = LazyListState()
	val isOnline = connectivityManager.isOnline
	val downloadedSongs = downloadManager.downloadedSongs
}
