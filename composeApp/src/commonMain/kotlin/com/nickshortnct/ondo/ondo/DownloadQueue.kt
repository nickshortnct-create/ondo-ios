package com.nickshortnct.ondo.ondo

import kotlinx.serialization.json.JsonPrimitive
import com.nickshortnct.ondo.domain.models.normalizeForMatch

enum class DownloadQueueState { ACTIVE, FAILED, COMPLETE, CANCELLED, UNKNOWN }

data class DownloadQueueSnapshot(
    val rowsByService: Map<String, List<DownloadRow>>,
    val errorsByService: Map<String, String>
)

fun normalizedDownloadStatus(status: String): String = status.trim().lowercase().replace('-', '_').replace(' ', '_')

fun downloadQueueState(status: String): DownloadQueueState = when (normalizedDownloadStatus(status)) {
    "queued", "pending", "waiting", "searching", "matched", "downloading", "processing", "post_processing", "postprocessing", "started", "running", "in_progress", "importpending", "importing", "paused", "retrying", "delay", "fallback" -> DownloadQueueState.ACTIVE
    "failed", "error", "not_found", "notfound", "rejected", "warning", "downloadclientunavailable", "importfailed" -> DownloadQueueState.FAILED
    "done", "completed", "complete", "finished", "success", "downloaded", "imported" -> DownloadQueueState.COMPLETE
    "cancelled", "canceled", "aborted" -> DownloadQueueState.CANCELLED
    else -> DownloadQueueState.UNKNOWN
}

/**
 * Lidarr reports a finished download as status "completed" even when it could not be imported.
 * The real outcome is in trackedDownloadState/trackedDownloadStatus: map import failures to a
 * failed status instead of leaving them "Adding to library" forever.
 */
fun lidarrQueueStatus(row: kotlinx.serialization.json.JsonElement): String {
    val tracked = normalizedDownloadStatus(row.str("trackedDownloadState"))
    return when {
        tracked == "imported" -> "imported"
        tracked in setOf("importblocked", "importfailed", "failedpending", "downloadfailed", "downloadfailedpending") -> "importfailed"
        normalizedDownloadStatus(row.str("trackedDownloadStatus")) == "error" -> "error"
        row.str("status").equals("completed", ignoreCase = true) -> "processing"
        else -> row.str("status")
    }
}

val DownloadRow.state: DownloadQueueState get() = downloadQueueState(status)
val DownloadRow.key: String get() = "$service:$id"
val DownloadRow.artist: String get() = raw.str("artist_name").ifBlank { raw.str("artist") }.ifBlank {
    raw.obj()["artist"]?.str("artistName").orEmpty()
}.ifBlank {
    raw.obj()["track_data"]?.let { track ->
        track.str("artist").ifBlank { track.obj()["artist"]?.str("name", track.obj()["artist"]?.str("artistName").orEmpty()).orEmpty() }
            .ifBlank { track.obj()["artists"].orEmptyArray().joinToString(", ") { if (it is JsonPrimitive) it.content else it.str("name") } }
    }.orEmpty()
}
val DownloadRow.statusLabel: String get() = when (normalizedDownloadStatus(status)) {
    "queued", "pending", "waiting", "matched" -> "Queued"
    "searching" -> "Finding music"
    "downloading", "started", "running", "in_progress" -> "Downloading"
    "processing", "post_processing", "postprocessing", "importpending", "importing" -> "Adding to library"
    "paused" -> "Paused"
    "delay", "fallback" -> "Waiting (delay profile)"
    "retrying" -> "Retrying"
    "not_found", "notfound" -> "Music not found"
    else -> when (state) {
        DownloadQueueState.COMPLETE -> "Added to server"
        DownloadQueueState.FAILED -> "Needs attention"
        DownloadQueueState.CANCELLED -> "Cancelled"
        DownloadQueueState.ACTIVE -> "In progress"
        DownloadQueueState.UNKNOWN -> "Waiting for an update"
    }
}

/** A durable SoulSync request (its /wishlist entry) rather than a running download task. */
val DownloadRow.isSoulSyncWishlist: Boolean get() = service == "SoulSync" && id.startsWith("wishlist:")
val DownloadRow.wishlistTrackId: String get() = raw.str("spotify_track_id").ifBlank { raw.str("track_id") }

/**
 * SoulSync retries a wishlist track forever and never marks it failed. After this many
 * unsuccessful searches Ondo shows it as not found so it can be removed or sent to YouTube.
 */
const val SOULSYNC_GIVE_UP_RETRIES = 3

fun soulSyncWishlistStatus(request: kotlinx.serialization.json.JsonElement): String =
    if (request.num("retry_count") >= SOULSYNC_GIVE_UP_RETRIES && request.str("failure_reason").isNotBlank()) "not_found" else "pending"

val DownloadRow.canCancel: Boolean get() = when {
    isSoulSyncWishlist -> wishlistTrackId.isNotBlank()
    else -> service == "Lidarr" && !id.startsWith("history:") && state != DownloadQueueState.COMPLETE && state != DownloadQueueState.CANCELLED
}
val DownloadRow.canRetry: Boolean get() = state == DownloadQueueState.FAILED && when (service) {
    "YouTube" -> raw.str("artist").isNotBlank() && raw.str("title").isNotBlank()
    "Lidarr" -> (raw.obj()["album"]?.num("id") ?: raw.num("albumId")) > 0
    "SoulSync" -> isSoulSyncWishlist && artist.isNotBlank() && title.isNotBlank()
    else -> false
}
val DownloadRow.retryLabel: String get() = if (isSoulSyncWishlist) "Try YouTube" else "Retry"
val DownloadRow.cancelLabel: String get() = if (isSoulSyncWishlist) "Remove" else "Cancel"

// Unknown progress stays indeterminate; a missing value must not look like a
// stalled zero-percent transfer. SoulSync's contract reports percentages.
fun queueProgress(service: String, row: kotlinx.serialization.json.JsonElement): Float? = when (service) {
    "Lidarr" -> row.num("size").takeIf { it > 0 }?.let { ((it - row.num("sizeleft")) / it).toFloat() }
    "SoulSync" -> (row.obj()["progress"] as? JsonPrimitive)?.content?.toFloatOrNull()?.let { it / 100f }
    else -> row.obj()["progress"]?.let { progress ->
        progress.num("total").takeIf { it > 0 }?.let { (progress.num("current") / it).toFloat() }
    }
}?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)

internal fun acquisitionIdentity(value: String): String = value.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

fun acquisitionKey(item: MusicResult): String = if (item.kind == "release" && item.raw.str("guid").isNotBlank()) {
    "release:${item.service}:${item.raw.num("indexerId").toInt()}:${item.raw.str("guid")}"
} else acquisitionKey(item.kind, item.title, item.artist)
fun acquisitionKey(kind: String, title: String, artist: String): String = "$kind:${normalizeForMatch(title)}:${normalizeForMatch(artist)}"
fun acquisitionKey(row: DownloadRow): String = acquisitionKey(if (row.service == "Lidarr") "album" else "track",
    if (row.service == "Lidarr") row.raw.obj()["album"]?.str("title").orEmpty().ifBlank { row.title } else row.title, row.artist)

internal fun matchesAcquisitionAlbum(expectedTitle: String, expectedArtist: String, candidateTitle: String, candidateArtist: String): Boolean =
    expectedTitle.isNotBlank() && expectedArtist.isNotBlank() && candidateTitle.isNotBlank() && candidateArtist.isNotBlank() &&
        acquisitionIdentity(expectedTitle) == acquisitionIdentity(candidateTitle) && acquisitionIdentity(expectedArtist) == acquisitionIdentity(candidateArtist)
