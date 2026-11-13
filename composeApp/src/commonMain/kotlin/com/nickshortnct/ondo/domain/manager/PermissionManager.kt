package com.nickshortnct.ondo.domain.manager

expect class PermissionManager {
	fun openPermissionsSettings()
	suspend fun requestLocalNetworkPermission(): Boolean
	suspend fun requestNotificationPermission(): Boolean
}
