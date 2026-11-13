package com.nickshortnct.ondo.data.database.entities

import androidx.room3.ColumnInfo
import com.nickshortnct.ondo.data.database.foldForSearch
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
@Entity
data class PlaylistEntity(
	@PrimaryKey val playlistId: String,
	val name: String,
	val comment: String?,
	val owner: String,
	val coverArtId: String?,
	val customCoverPath: String? = null,
	val songCount: Int,
	val duration: Duration,
	val public: Boolean?,
	val readOnly: Boolean?,
	val createdAt: Instant,
	val modifiedAt: Instant,
	val validUntil: Instant?,
	val allowedUsers: List<String>,
	@ColumnInfo(defaultValue = "''")
	val searchText: String = foldForSearch(listOfNotNull(name).joinToString(" "))
)
