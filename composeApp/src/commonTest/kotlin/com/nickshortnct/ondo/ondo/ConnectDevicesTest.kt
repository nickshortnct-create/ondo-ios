package com.nickshortnct.ondo.ondo

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectDevicesTest {
	@Test
	fun devicesWithTheSameNameStayDistinctAndGetASuffix() {
		val devices = disambiguateDeviceNames(
			listOf(ConnectDevice("aaaa-1", "Pixel"), ConnectDevice("bbbb-2", "Pixel"), ConnectDevice("cccc-3", "Tablet"))
		)

		assertEquals(listOf("Pixel · aaaa", "Pixel · bbbb", "Tablet"), devices.map { it.name })
	}
}

class ConnectHandoffTest {
	private val playing = ConnectSnapshot(playing = true, queue = listOf(ConnectTrack("a")), currentIndex = 0, playingSince = 2_000)
	private fun follow(snapshot: ConnectSnapshot = playing, selected: String? = null, localPlaying: Boolean = false, localSince: Long? = null, claimAt: Long = 0, handoff: Long = 0, now: Long = 60_000) =
		shouldFollow(snapshot, "desk", selected, "phone", localPlaying, localSince, claimAt, handoff, now)

	@Test
	fun theDeviceThatStartedLastKeepsPlaying() {
		kotlin.test.assertTrue(keepsPlaying("phone", 3_000, "desk", 2_000))
		kotlin.test.assertFalse(keepsPlaying("phone", 1_000, "desk", 2_000))
		// Ties are settled by id, the same way on both devices.
		kotlin.test.assertTrue(keepsPlaying("phone", 2_000, "desk", 2_000))
		kotlin.test.assertFalse(keepsPlaying("desk", 2_000, "phone", 2_000))
		// An older build sends no start time and never stops itself.
		kotlin.test.assertFalse(keepsPlaying("phone", 9_000, "desk", null))
	}

	@Test
	fun followsADeviceThatStartsPlaying() {
		kotlin.test.assertTrue(follow())
		kotlin.test.assertFalse(follow(snapshot = playing.copy(playing = false)))
		kotlin.test.assertFalse(follow(selected = "desk"))
	}

	@Test
	fun stopsHereOnlyWhenTheOtherDeviceStartedLater() {
		kotlin.test.assertTrue(follow(localPlaying = true, localSince = 1_000))
		kotlin.test.assertFalse(follow(localPlaying = true, localSince = 5_000))
	}

	@Test
	fun ignoresTheOldDeviceRightAfterAHandoff() {
		kotlin.test.assertFalse(follow(handoff = 61_000))
		kotlin.test.assertFalse(follow(claimAt = 58_000))
		kotlin.test.assertTrue(follow(claimAt = 50_000))
	}

	@Test
	fun streamQualityTravelsWithTheSnapshot() {
		val song = ConnectTrack("a").song()
		val local = com.nickshortnct.ondo.ui.core.PlayerUiState(queue = listOf(song), currentIndex = 0, currentSong = song, isPaused = false, playbackMimeType = "audio/flac", playbackBitrate = 892_000, playbackSampleRate = 44_100)
		val sent = local.snapshot()
		assertEquals("FLAC", sent.format); assertEquals(892, sent.bitRate); assertEquals(44_100, sent.sampleRate)
		val shown = sent.state()
		assertEquals("audio/flac", shown.playbackMimeType); assertEquals(892_000, shown.playbackBitrate); assertEquals(44_100, shown.playbackSampleRate)
	}

	@Test
	fun theStartTimeIsOnlySentWhilePlaying() {
		val song = ConnectTrack("a").song()
		val state = com.nickshortnct.ondo.ui.core.PlayerUiState(queue = listOf(song), currentIndex = 0, currentSong = song, isPaused = false)
		assertEquals(7L, state.snapshot(playingSince = 7).playingSince)
		assertEquals(null, state.copy(isPaused = true).snapshot(playingSince = 7).playingSince)
	}

	@Test
	fun theRemotePositionCountsForwardBetweenUpdates() {
		val playing = ConnectSnapshot(playing = true, currentTime = 30.0, duration = 200.0, currentIndex = 0, queue = listOf(ConnectTrack("a", duration = 200.0))).state()
		assertEquals(0.15f, playing.progress, 0.0001f)
		assertEquals(0.2f, playing.advancedBy(10_000).progress, 0.0001f)
		val paused = playing.copy(isPaused = true)
		assertEquals(paused, paused.advancedBy(10_000))
		assertEquals(1f, playing.advancedBy(1_000_000).progress)
	}

	@Test
	fun takingOverStartsWhereThePlayingDeviceIsNow() {
		val snap = ConnectSnapshot(playing = true, currentTime = 30.0, duration = 200.0, currentIndex = 0, queue = listOf(ConnectTrack("a", duration = 200.0)))
		assertEquals(38.0, snap.at(nowMs = 18_000, receivedAtMs = 10_000).currentTime, 0.001)
		assertEquals(30.0, snap.copy(playing = false).at(18_000, 10_000).currentTime, 0.001)
		assertEquals(200.0, snap.at(nowMs = 1_000_000, receivedAtMs = 0).currentTime, 0.001)
	}

	@Test
	fun albumAndArtistIdsTravelWithTheTrack() {
		val sent = ConnectTrack("s", "Song", "Artist", "Album", 100.0, "cover", albumId = "al1", artistId = "ar1")
		val song = sent.song()
		assertEquals("al1", song.albumId); assertEquals("ar1", song.artistId)
		assertEquals(sent, song.connectTrack())
		assertEquals(null, ConnectTrack("s").song().connectTrack().albumId)
	}
}
