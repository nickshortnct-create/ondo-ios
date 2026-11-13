package com.nickshortnct.ondo.domain.manager

import kotlinx.coroutines.flow.Flow
import com.nickshortnct.ondo.domain.parser.LogLine

expect class LogManager {
	fun clearLogs()
	fun logFlow(): Flow<LogLine>
}
