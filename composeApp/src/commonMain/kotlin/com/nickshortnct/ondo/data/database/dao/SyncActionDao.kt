package com.nickshortnct.ondo.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.nickshortnct.ondo.data.database.entities.SyncActionEntity

@Dao
interface SyncActionDao {
	@Insert
	suspend fun enqueue(action: SyncActionEntity)

	@Transaction
	@Query("SELECT * FROM SyncActionEntity ORDER BY id ASC")
	suspend fun getPendingActions(): List<SyncActionEntity>

	@Query("DELETE FROM SyncActionEntity WHERE id = :id")
	suspend fun removeAction(id: Int)

	@Query("UPDATE SyncActionEntity SET attempts = attempts + 1, lastError = :error WHERE id = :id")
	suspend fun recordFailure(id: Int, error: String?)

	@Query("DELETE FROM SyncActionEntity")
	suspend fun clearAllActions()
}
