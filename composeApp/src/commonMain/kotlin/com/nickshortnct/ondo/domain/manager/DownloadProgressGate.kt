package com.nickshortnct.ondo.domain.manager

internal class DownloadProgressGate(private var lastAt: Long) {
	private var lastProgress = 0f
	fun shouldPersist(progress: Float, now: Long): Boolean {
		if (progress < 1f && progress - lastProgress < 0.1f && now - lastAt < 5000) return false
		lastAt = now; lastProgress = progress
		return true
	}
}
