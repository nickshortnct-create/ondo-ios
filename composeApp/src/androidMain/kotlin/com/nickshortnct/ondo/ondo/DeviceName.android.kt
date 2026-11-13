package com.nickshortnct.ondo.ondo

import android.os.Build

actual fun defaultDeviceName(): String =
	listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
		.replaceFirstChar { it.uppercase() }
		.ifBlank { "Ondo Android" }
