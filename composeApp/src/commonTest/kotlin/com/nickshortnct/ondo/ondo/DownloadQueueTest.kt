package com.nickshortnct.ondo.ondo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class DownloadQueueTest {
    @Test
    fun sameNamedReleasesKeepTheirExactGuidAndIndexerIdentity() {
        fun release(guid: String, indexer: Int) = MusicResult("Lidarr", "release", guid, "Album", "", jsonObject("guid" to guid.j(), "indexerId" to indexer.j()))
        assertEquals("release:Lidarr:7:GUID-one", acquisitionKey(release("GUID-one", 7)))
        assertNotEquals(acquisitionKey(release("GUID-one", 7)), acquisitionKey(release("GUID-two", 7)))
        assertNotEquals(acquisitionKey(release("GUID-one", 7)), acquisitionKey(release("GUID-one", 8)))
    }

    @Test
    fun acceptedRequestsUseTheSameIdentityAcrossMatchingProviders() {
        val first = MusicResult("SoulSync", "track", "one", "Don't Stop", "Artist", jsonObject())
        val other = MusicResult("Squid", "track", "two", "Dont Stop", " ARTIST ", jsonObject())
        assertEquals(acquisitionKey(first), acquisitionKey(other))
        assertFalse(acquisitionKey(first) == acquisitionKey(first.copy(title = "Don't Stop (Live)")))
    }

    @Test
    fun failuresAndCompletedTasksRemainDistinguishableFromActiveWork() {
        assertEquals(DownloadQueueState.ACTIVE, downloadQueueState(" Post-processing "))
        assertEquals(DownloadQueueState.FAILED, downloadQueueState("not_found"))
        assertEquals(DownloadQueueState.FAILED, downloadQueueState("downloadClientUnavailable"))
        assertEquals(DownloadQueueState.COMPLETE, downloadQueueState("done"))
        assertEquals(DownloadQueueState.CANCELLED, downloadQueueState("canceled"))
        assertEquals(DownloadQueueState.UNKNOWN, downloadQueueState("future_provider_state"))
    }

    @Test
    fun absentOrInvalidProgressStaysIndeterminate() {
        assertNull(queueProgress("SoulSync", jsonObject()))
        assertNull(queueProgress("SoulSync", jsonObject("progress" to "NaN".j())))
        assertNull(queueProgress("Squid", jsonObject("progress" to jsonObject("current" to 120.j(), "total" to 0.j()))))
        assertEquals(0.45f, queueProgress("SoulSync", jsonObject("progress" to 45.j())))
        assertEquals(1f, queueProgress("Squid", jsonObject("progress" to jsonObject("current" to 120.j(), "total" to 100.j()))))
    }

    @Test
    fun aSameNamedAlbumByAnotherArtistIsNeverAcquired() {
        assertTrue(matchesAcquisitionAlbum("Discovery", "Daft Punk", " discovery ", "DAFT PUNK"))
        assertFalse(matchesAcquisitionAlbum("Discovery", "Daft Punk", "Discovery", "Electric Light Orchestra"))
        assertFalse(matchesAcquisitionAlbum("Discovery", "Daft Punk", "Discovery (Deluxe)", "Daft Punk"))
        assertFalse(matchesAcquisitionAlbum("c", "Artist", "Music", "Artist"))
        assertFalse(matchesAcquisitionAlbum("Discovery", "Daft Punk", "Discovery", ""))
    }

    @Test
    fun unavailableActionsAreNotOfferedForSourcesWithoutAnApiContract() {
        val task = jsonObject("title" to "Song".j(), "artist" to "Artist".j())
        assertTrue(DownloadRow("YouTube", "task", "Song", "error", "", null, task).canRetry)
        assertFalse(DownloadRow("Squid", "task", "Song", "error", "", null, task).canRetry)
        assertFalse(DownloadRow("YouTube", "task", "Song", "queued", "", null, task).canCancel)
        assertTrue(DownloadRow("Lidarr", "task", "Album", "downloading", "", null, task).canCancel)
    }

    @Test
    fun lidarrImportFailuresAreNotShownAsAddingToTheLibrary() {
        fun row(status: String, tracked: String = "", trackedStatus: String = "") =
            jsonObject("status" to status.j(), "trackedDownloadState" to tracked.j(), "trackedDownloadStatus" to trackedStatus.j())

        assertEquals("imported", lidarrQueueStatus(row("completed", "imported")))
        assertEquals("processing", lidarrQueueStatus(row("completed", "importPending")))
        for (failed in listOf("importBlocked", "importFailed", "failedPending", "downloadFailed", "downloadFailedPending")) {
            assertEquals(DownloadQueueState.FAILED, downloadQueueState(lidarrQueueStatus(row("completed", failed, "warning"))), failed)
        }
        assertEquals(DownloadQueueState.FAILED, downloadQueueState(lidarrQueueStatus(row("downloading", "downloading", "error"))))
        assertEquals(DownloadQueueState.ACTIVE, downloadQueueState(lidarrQueueStatus(row("delay"))))
        assertEquals("Waiting (delay profile)", DownloadRow("Lidarr", "1", "Album", "fallback", "", null, row("fallback")).statusLabel)
    }

    @Test
    fun soulSyncRequestsThatKeepFailingCanBeRemovedOrSentToYouTube() {
        fun request(retries: Int, reason: String = "No matching track found") = jsonObject(
            "id" to 43.j(), "spotify_track_id" to "abc123".j(), "track_name" to "turn up the speakers".j(),
            "artist_name" to "AnGelato".j(), "retry_count" to retries.j(), "failure_reason" to reason.j()
        )
        fun row(request: kotlinx.serialization.json.JsonElement) =
            DownloadRow("SoulSync", "wishlist:43", request.str("track_name"), soulSyncWishlistStatus(request), "", null, request)

        // A new request is still pending and can only be removed.
        val fresh = row(request(1))
        assertEquals(DownloadQueueState.ACTIVE, fresh.state)
        assertTrue(fresh.canCancel)
        assertFalse(fresh.canRetry)
        assertEquals("Remove", fresh.cancelLabel)
        assertEquals("pending", soulSyncWishlistStatus(request(5, reason = "")))

        // After repeated failed searches it stops spinning and offers both actions.
        val stuck = row(request(SOULSYNC_GIVE_UP_RETRIES))
        assertEquals(DownloadQueueState.FAILED, stuck.state)
        assertEquals("Music not found", stuck.statusLabel)
        assertTrue(stuck.canRetry)
        assertTrue(stuck.canCancel)
        assertEquals("Try YouTube", stuck.retryLabel)
        assertEquals("abc123", stuck.wishlistTrackId)

        // A running SoulSync download task is not a wishlist request.
        assertFalse(DownloadRow("SoulSync", "task-1", "Song", "error", "", null, request(9)).canCancel)
        assertFalse(DownloadRow("SoulSync", "task-1", "Song", "error", "", null, request(9)).canRetry)
    }

    @Test
    fun soulseekAlbumProgressUsesTransferredBytes() {
        val album = jsonObject(
            "id" to "soularr:22386".j(), "title" to "Luh Leakyz Vol. 1".j(), "artist" to "Yeat".j(), "status" to "downloading".j(),
            "progress" to jsonObject("current" to 40.j(), "total" to 160.j())
        )
        val row = DownloadRow("Soulseek", "soularr:22386", "Luh Leakyz Vol. 1", "downloading", "", queueProgress("Soulseek", album), album)
        assertEquals(0.25f, row.progress)
        assertEquals("Yeat", row.artist)
        assertEquals(DownloadQueueState.ACTIVE, row.state)
        assertFalse(row.canCancel)
        assertFalse(row.canRetry)
        assertEquals(DownloadQueueState.COMPLETE, downloadQueueState("imported"))
    }
}
