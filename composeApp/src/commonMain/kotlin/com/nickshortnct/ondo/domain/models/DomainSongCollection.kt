package com.nickshortnct.ondo.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Immutable
@Serializable
sealed interface DomainSongCollection {
	val id: String
	val name: String
	val coverArtId: String?
	val duration: Duration?
	val songCount: Int
	val songs: List<DomainSong>
}

fun DomainSongCollection.songIds(): List<String> = when (this) {
	is DomainAlbum -> cachedSongIds.takeIf { songs.isEmpty() }.orEmpty().ifEmpty { songs.map { it.id } }
	is DomainPlaylist -> cachedSongIds.takeIf { songs.isEmpty() }.orEmpty().ifEmpty { songs.map { it.id } }
}
