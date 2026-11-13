package com.nickshortnct.ondo.data.database.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Clock

enum class SyncActionType {
	STAR, UNSTAR, DELETE_PLAYLIST, SCROBBLE,

	// this is dumb but it works so whatever
	STAR_0, STAR_1, STAR_2, STAR_3, STAR_4, STAR_5
}

@Entity
data class SyncActionEntity(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	val actionType: SyncActionType,
	val itemId: String,
	/** How many times the server rejected this action; it is dropped after a few. */
	@ColumnInfo(defaultValue = "0") val attempts: Int = 0,
	val lastError: String? = null,
	/** When the user did it (epoch ms); offline scrobbles are submitted with this time. 0 = unknown (older rows). */
	@ColumnInfo(defaultValue = "0") val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)
