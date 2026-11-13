package com.nickshortnct.ondo.data.database

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.stringByFoldingWithOptions

// Same result as the Android actual: strip accents, lowercase, collapse punctuation to spaces.
@Suppress("CAST_NEVER_SUCCEEDS")
actual fun foldForSearch(value: String): String = (value as NSString)
	.stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
	.lowercase()
	.replace(Regex("[^\\p{L}\\p{N}%_\\\\]+"), " ")
	.trim()
