package com.nickshortnct.ondo.ondo

// JioSaavn downloads are Android-only; nothing calls this while mediaAcquisitionEnabled is false.
actual fun decryptSaavnUrl(encrypted: String): String =
	throw UnsupportedOperationException("JioSaavn downloads are not available on iOS")
