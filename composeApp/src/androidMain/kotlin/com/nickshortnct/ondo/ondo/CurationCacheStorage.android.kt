package com.nickshortnct.ondo.ondo

import android.content.Context
import java.io.File

actual class CurationCacheStorage(context: Context) {
	private val file = File(context.filesDir, "curation.json")

	actual fun read(): String? = file.takeIf { it.exists() }?.readText()

	actual fun write(text: String) {
		val tmp = File(file.parentFile, "${file.name}.tmp")
		tmp.writeText(text)
		if (!tmp.renameTo(file)) {
			file.writeText(text)
			tmp.delete()
		}
	}
}
