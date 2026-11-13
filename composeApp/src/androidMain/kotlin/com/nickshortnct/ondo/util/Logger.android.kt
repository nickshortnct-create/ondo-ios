package com.nickshortnct.ondo.util

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

actual object Logger {
	private const val MAX_LOG_BYTES = 2L * 1024 * 1024
	@Volatile private var logFile: File? = null
	private var writer: BufferedWriter? = null
	private var writtenBytes = 0L
	private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
		Thread(runnable, "ondo-log-writer").apply { isDaemon = true }
	}
	private val timestampFormat = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US) }

	fun install(context: Context) {
		logFile = File(File(context.filesDir, "logs").apply { mkdirs() }, "ondo-debug.log")
		executor.execute {
			for (file in listOfNotNull(logFile, logFile?.parentFile?.let { File(it, "ondo-debug.1.log") })) {
				runCatching { if (file.isFile) file.writeText(redactLogText(file.readText())) }
			}
		}
		executor.scheduleAtFixedRate({ runCatching { writer?.flush() } }, 1, 1, TimeUnit.SECONDS)
	}

	fun logFile(): File? { flushBlocking(); return logFile }

	/** Called by the uncaught-exception handler before it terminates the process. */
	fun flushBlocking() { runCatching { executor.submit { writer?.flush() }.get(1500, TimeUnit.MILLISECONDS) } }

	actual fun e(tag: String, msg: String, tr: Throwable?) = write("E", tag, msg, tr)
	actual fun i(tag: String, msg: String, tr: Throwable?) = write("I", tag, msg, tr)
	actual fun w(tag: String, msg: String, tr: Throwable?) = write("W", tag, msg, tr)

	private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
		val safe = redactLogText(msg + if (tr != null) "\n" + Log.getStackTraceString(tr) else "")
		when (level) { "E" -> Log.e(tag, safe); "W" -> Log.w(tag, safe); else -> Log.i(tag, safe) }
		val line = "${timestampFormat.get()!!.format(Date())} $level/$tag: $safe\n"
		executor.execute {
			val file = logFile ?: return@execute
			runCatching {
				if (writer == null) { writer = java.io.FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8); writtenBytes = file.length() }
				if (writtenBytes >= MAX_LOG_BYTES) {
					writer?.close()
					val previous = File(file.parentFile, "ondo-debug.1.log")
					previous.delete()
					file.renameTo(previous)
					writer = java.io.FileOutputStream(file, false).bufferedWriter(Charsets.UTF_8)
					writtenBytes = 0L
				}
				writer?.write(line)
				writtenBytes += line.toByteArray(Charsets.UTF_8).size
				if (level == "E") writer?.flush()
			}.onFailure { runCatching { writer?.close() }; writer = null }
		}
	}
}
