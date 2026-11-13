package com.nickshortnct.ondo.shared

import java.util.UUID
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.nickshortnct.ondo.domain.manager.ConnectivityManager
import com.nickshortnct.ondo.domain.manager.DownloadManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainSong
import java.io.File

/**
 * Builds the playable [MediaItem] for a song: picks the downloaded file when we
 * have one and falls back to a transcoded stream URL, and attaches artwork.
 *
 * This used to live as a private extension inside [AndroidMediaPlayerViewModel].
 * Android Auto needs exactly the same resolution -- a browse item the user taps
 * arrives at the session as a bare media id with no URI, and if the service
 * resolved it differently from the app (wrong bitrate, ignoring downloads) the
 * same track would stream differently depending on which surface started it.
 * One factory, injected in both places, keeps that honest.
 */
@OptIn(UnstableApi::class)
class OndoMediaItemFactory(
	private val downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager,
	private val connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager
) {
	fun streamUri(id: String): Uri {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid
		}
		return sessionManager.api.getStreamUrl(id, bitrate, container?.takeIf { it.isNotBlank() })
			.toUri()
			.buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
	}

	fun coverArtUri(coverArtId: String?): Uri? =
		coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() }

	/**
	 * @param mediaId overrides the item's media id. Browse trees need
	 * hierarchical ids (see [MediaId]); the playback queue wants the plain song
	 * id, because the rest of the app indexes its queue mirror by it.
	 * Artwork is always an *URI*, never embedded bytes: a queue of cached
	 * full-size covers would put hundreds of MB in every MediaItem copy and
	 * across the binder. The session's [CoilBitmapLoader] resolves the URI from
	 * Coil's cache at a bounded size.
	 */
	fun toMediaItem(
		song: DomainSong,
		mediaId: String = song.id
	): MediaItem {
		val displayArtist = song.artists.joinToString { it.name }.ifBlank { song.artistName }
		val albumArtistName = song.albumArtists.joinToString { it.name }.ifBlank { song.artistName }

		val metadataBuilder = MediaMetadata.Builder()
			.setTitle(song.title)
			.setSubtitle(displayArtist)
			.setArtist(displayArtist)
			.setAlbumArtist(albumArtistName)
			.setAlbumTitle(song.albumTitle)
			.setDurationMs(song.duration.inWholeMilliseconds)
			.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
			.setIsBrowsable(false)
			.setIsPlayable(true)
			.setExtras(GainExtras.write(song).apply { putString("ondo.queueEntryId", UUID.randomUUID().toString()) })

		metadataBuilder.setArtworkUri(coverArtUri(song.coverArtId))

		val radioStream = song.filePath?.takeIf { song.id.startsWith("radio_") && it.isNotEmpty() }
		val uri = radioStream?.toUri()
			?: downloadManager.getDownloadedFilePath(song.id)?.let { File(it).toUri() }
			?: streamUri(song.id)

		val builder = MediaItem.Builder()
			.setUri(uri)
			.setMediaId(mediaId)
			.setMediaMetadata(metadataBuilder.build())

		if (song.id.startsWith("radio_")) {
			builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
		}

		return builder.build()
	}
}
