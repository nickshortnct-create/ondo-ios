package com.nickshortnct.ondo.ondo

// Nothing is baked into the iPhone build: the login screen asks for the server
// address, and the download integrations are off (see mediaAcquisitionEnabled).
actual fun bundledIntegrationConfig(name: String): ServiceConfig? = null

actual fun bundledNavidromeUrl(): String = ""
