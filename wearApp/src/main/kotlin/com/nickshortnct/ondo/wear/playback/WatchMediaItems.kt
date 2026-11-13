package com.nickshortnct.ondo.wear.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.nickshortnct.ondo.wear.data.Song
import com.nickshortnct.ondo.wear.data.SubsonicApi
import com.nickshortnct.ondo.wear.data.WatchSettings
import com.nickshortnct.ondo.wear.downloads.DownloadStore

class WatchMediaItems(
	private val api: SubsonicApi,
	private val downloads: DownloadStore,
	private val settings: WatchSettings,
) {
	fun build(song: Song): MediaItem = withPlayableUri(
		MediaItem.Builder()
			.setMediaId(song.id)
			.setMediaMetadata(
				MediaMetadata.Builder()
					.setTitle(song.title)
					.setArtist(song.artist)
					.setAlbumTitle(song.album)
					.setArtworkUri(downloads.coverFile(song.coverArt)?.toUri() ?: api.coverArtUri(song.coverArt))
					.setDurationMs(song.durationSec * 1000L)
					.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
					.setIsPlayable(true)
					.setIsBrowsable(false)
					.setExtras(Bundle().apply {
						putString(EXTRA_SONG_JSON, song.toJson().toString())
					})
					.build()
			)
			.build()
	)

	/** Local download if present, otherwise a stream at the configured bitrate. */
	fun withPlayableUri(item: MediaItem): MediaItem {
		val uri = downloads.songFile(item.mediaId)?.toUri()
			?: runCatching { api.streamUri(item.mediaId, settings.streamBitrate.value) }.getOrNull()
			?: return item
		return item.buildUpon().setUri(uri).build()
	}

	companion object {
		const val EXTRA_SONG_JSON = "ondo.song"
	}
}
