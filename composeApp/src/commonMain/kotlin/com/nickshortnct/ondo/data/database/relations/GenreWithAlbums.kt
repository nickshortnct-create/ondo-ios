package com.nickshortnct.ondo.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.entities.GenreEntity

data class GenreWithAlbums(
	@Embedded val genre: GenreEntity,
	@Relation(
		entity = AlbumEntity::class,
		parentColumns = ["genreName"],
		entityColumns = ["genre"]
	)
	val albums: List<AlbumWithSongs>
)
