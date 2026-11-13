package com.nickshortnct.ondo.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.entities.SongEntity

data class AlbumWithSongs(
	@Embedded val album: AlbumEntity,
	@Relation(
		parentColumns = ["albumId"],
		entityColumns = ["belongsToAlbumId"]
	)
	val songs: List<SongEntity>
)
