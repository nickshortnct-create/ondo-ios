package com.nickshortnct.ondo.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveForPlaybackTest {
	@Test fun anAppBuiltSongWithAUriPassesThroughEvenIfItIsNotCached() {
		assertTrue(isPlayableAsIs("uncached-song", hasUri = true))
		assertFalse(isPlayableAsIs("uncached-song", hasUri = false))
	}

	@Test fun autoTrackIdsRetainTheirAlbumAndPlaylistContext() {
		for (parent in listOf(TrackParent.Album("album"), TrackParent.Playlist("playlist"), TrackParent.None)) {
			val id = MediaId.track(parent, "song")
			assertFalse(isPlayableAsIs(id, hasUri = true))
			assertFalse(isPlayableAsIs(id, hasUri = false))
		}
	}
}
