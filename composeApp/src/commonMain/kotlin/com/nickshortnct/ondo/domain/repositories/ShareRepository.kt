package com.nickshortnct.ondo.domain.repositories

import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.domain.manager.SessionManager

class ShareRepository(
	private val sessionManager: SessionManager
) {
	suspend fun getShares() = sessionManager.api.getShares().map { it.toDomainModel() }
}
