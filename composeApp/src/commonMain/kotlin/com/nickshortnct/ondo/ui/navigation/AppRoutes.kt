package com.nickshortnct.ondo.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/** Lets platform entry points (notifications, intents) ask the app to open an Ondo page. */
object AppRoutes {
	val pendingOndoPage = MutableStateFlow<String?>(null)
}
