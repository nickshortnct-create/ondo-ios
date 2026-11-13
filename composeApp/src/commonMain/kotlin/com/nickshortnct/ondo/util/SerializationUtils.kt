package com.nickshortnct.ondo.util

import kotlinx.serialization.SerializationException

fun Throwable.findSerializationException(): SerializationException? {
	var current: Throwable? = this
	while (current != null) {
		if (current is SerializationException) {
			return current
		}
		current = current.cause
	}
	return null
}
