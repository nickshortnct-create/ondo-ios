package com.nickshortnct.ondo.util

import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
	Result.success(block())
} catch (e: CancellationException) {
	throw e
} catch (e: Throwable) {
	Result.failure(e)
}
