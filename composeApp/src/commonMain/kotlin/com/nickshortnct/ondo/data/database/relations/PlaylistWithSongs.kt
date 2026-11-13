package com.nickshortnct.ondo.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistSongCrossRef
import com.nickshortnct.ondo.data.database.entities.SongEntity

data class PlaylistWithSongs(
	@Embedded val playlist: PlaylistEntity,
	@Relation(
		entity = PlaylistSongCrossRef::class,
		parentColumns = ["playlistId"],
		entityColumns = ["playlistId"]
	)
	val songs: List<PlaylistSong>
)

data class PlaylistSong(
	@Embedded val crossRef: PlaylistSongCrossRef,
	@Relation(
		parentColumns = ["songId"],
		entityColumns = ["songId"]
	)
	val song: SongEntity
)
