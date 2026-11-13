package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.flow.Flow
import com.nickshortnct.ondo.domain.parser.LogLine

actual class LogManager {
	actual fun clearLogs() { TODO() }
	actual fun logFlow(): Flow<LogLine> = TODO()
}
