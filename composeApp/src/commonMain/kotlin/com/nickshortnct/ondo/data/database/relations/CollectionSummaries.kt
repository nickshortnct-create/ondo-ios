package com.nickshortnct.ondo.data.database.relations

import androidx.room3.Embedded
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity

/** List projections carry IDs for download checks without constructing every song. */
data class AlbumSummary(@Embedded val album: AlbumEntity, val songIds: List<String>?)
data class PlaylistSummary(@Embedded val playlist: PlaylistEntity, val songIds: List<String>?, val latestSongYear: Int?)
