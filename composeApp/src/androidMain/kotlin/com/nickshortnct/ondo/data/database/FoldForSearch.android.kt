package com.nickshortnct.ondo.data.database

import java.text.Normalizer
import java.util.Locale

actual fun foldForSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
	.replace(Regex("\\p{M}+"), "")
	.lowercase(Locale.ROOT)
	.replace(Regex("[^\\p{L}\\p{N}%_\\\\]+"), " ")
	.trim()
