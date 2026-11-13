package com.nickshortnct.ondo.di

interface AppUpdater {
	val installedVersionCode: Long
	suspend fun download(url: String, expectedVersionCode: Long): Boolean
	fun install(expectedVersionCode: Long): UpdateInstallResult
}

enum class UpdateInstallResult { Launched, PermissionRequired, Unavailable }

internal fun releaseBuildNumber(tag: String): Long? = Regex("\\+(\\d+)$").find(tag)?.groupValues?.get(1)?.toLongOrNull()

internal fun compareUpdateVersions(left: String, right: String): Int {
	fun parts(value: String) = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+(\\d+))?")
		.find(value)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: 0L }
	val a = parts(left) ?: return -1
	val b = parts(right) ?: return 1
	for (index in a.indices) if (a[index] != b[index]) return a[index].compareTo(b[index])
	return 0
}

internal fun isExpectedUpdateVersion(candidate: Long, installed: Long, expected: Long): Boolean =
	candidate == expected && candidate > installed
