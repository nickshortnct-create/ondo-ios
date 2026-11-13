package com.nickshortnct.ondo.domain.manager

actual class PermissionManager {
	actual fun openPermissionsSettings() {
		//UIApplication.sharedApplication.openURL(NSURL(string = UIApplicationOpenSettingsURLString))
	}
	actual suspend fun requestLocalNetworkPermission(): Boolean {
		// it's annoying to trigger this dialogue on iOS
		return true
	}
	actual suspend fun requestNotificationPermission(): Boolean {
		return true
	}
}
