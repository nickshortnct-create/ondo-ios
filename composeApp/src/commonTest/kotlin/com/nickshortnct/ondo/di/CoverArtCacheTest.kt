package com.nickshortnct.ondo.di

import coil3.disk.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.*

class CoverArtCacheTest {
	@Test fun splitsNavidromeVersionStampFromCoverId() {
		assertEquals(CoverArtRef("al-5BbkzawxTLgR0Q9pPPT0dM", "6ad1d0af"), splitCoverArtId("al-5BbkzawxTLgR0Q9pPPT0dM_6ad1d0af"))
		assertEquals(CoverArtRef("ar-abc", "6ac86227"), splitCoverArtId("ar-abc_6ac86227"))
	}

	@Test fun navidromeZeroStampIsNotAVersion() {
		assertEquals(CoverArtRef("mf-3TjFytww19EMtVjUxhFsp1", null), splitCoverArtId("mf-3TjFytww19EMtVjUxhFsp1_0"))
		assertEquals(CoverArtRef("al-abc", null), splitCoverArtId("al-abc_0"))
		assertEquals(CoverArtRef("dc-abc:1", null), splitCoverArtId("dc-abc:1_0"))
	}

	@Test fun newestCoverIdWinsForTheSameCover() {
		assertEquals(mapOf("al-a" to "al-a_6ad1"), newestCoverIds(listOf("al-a_6ad1", "al-a_0")))
		assertEquals(mapOf("al-a" to "al-a_6ad1"), newestCoverIds(listOf("al-a_0", "al-a_6ad1")))
		assertEquals(mapOf("al-a" to "al-a_6a000002"), newestCoverIds(listOf("al-a_6a000002", "al-a_6a000001")))
		assertEquals(mapOf("al-a" to "al-a_6a000002"), newestCoverIds(listOf("al-a_6a000001", "al-a_6a000002")))
		assertEquals(mapOf("al-a" to "al-a_10"), newestCoverIds(listOf("al-a_f", "al-a_10")))
		assertEquals(
			mapOf("al-a" to "al-a_0", "mf-b" to "mf-b_12"),
			newestCoverIds(listOf(null, "", "al-a_0", "mf-b_12"))
		)
	}

	@Test fun leavesOtherServersCoverIdsWhole() {
		listOf("12345", "cover_1", "al-abc", "al-abc_xyz", "album_6ad1d0af", "al-a_b_6ad1d0af").forEach {
			assertEquals(CoverArtRef(it, null), splitCoverArtId(it), it)
		}
	}

	@Test fun readsCoverIdFromSubsonicUrlOnly() {
		val url = "https://decknas.duckdns.org/navidrome/rest/getCoverArt?id=al-5Bbk_6ad1d0af&size=300&u=nick&s=01&t=dd&v=1.16.1&c=Ondo"
		assertEquals(CoverArtRef("al-5Bbk", "6ad1d0af"), coverArtRefFromUrl(url))
		assertEquals(CoverArtRef("al-5Bbk", "6ad1d0af"), coverArtRefFromUrl(url.replace("getCoverArt", "getCoverArt.view")))
		assertNull(coverArtRefFromUrl("https://decknas.duckdns.org/ondo-curation/v1/covers/mood-1.jpg"))
		assertNull(coverArtRefFromUrl("https://decknas.duckdns.org/navidrome/rest/getCoverArt?size=300"))
	}

	private fun withCovers(block: suspend (Path, () -> DownloadedCoverArt) -> Unit) = runBlocking {
		val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "downloaded-covers-test-${Random.nextLong()}"
		val scope = CoroutineScope(SupervisorJob())
		try {
			block(dir) { DownloadedCoverArt(dir / "downloaded", CoverArtVersions(dir / "versions.tsv", scope), scope) }
		} finally {
			scope.cancel()
			FileSystem.SYSTEM.deleteRecursively(dir)
		}
	}

	private fun Path.writeText(text: String) = FileSystem.SYSTEM.write(this) { writeUtf8(text) }
	private fun Path.readText() = FileSystem.SYSTEM.read(this) { readUtf8() }

	@Test fun savedCoverIsKeptUntilItsVersionChanges() = withCovers { _, open ->
		val covers = open()
		var downloads = 0
		val save: suspend (String) -> Unit = { id ->
			covers.store(id, null) { target -> downloads++; target.writeText(id) }
		}
		assertNull(covers.pathOf("al-a"))
		assertFalse(covers.isCurrent("al-a_1"))

		save("al-a_1")
		save("al-a_1")
		save("al-a_0")
		assertEquals(1, downloads)
		assertEquals("al-a_1", covers.pathOf("al-a")!!.readText())
		assertTrue(covers.isCurrent("al-a_0"))

		assertFalse(covers.isCurrent("al-a_2"))
		assertTrue(covers.contains("al-a_2"))
		save("al-a_2")
		assertEquals(2, downloads)
		assertEquals("al-a_2", covers.pathOf("al-a")!!.readText())
	}

	@Test fun failedRefreshKeepsTheOldCover() = withCovers { _, open ->
		val covers = open()
		covers.store("al-a_1", null) { it.writeText("old") }

		assertFailsWith<IllegalStateException> { covers.store("al-a_2", null) { error("offline") } }
		assertFailsWith<IllegalStateException> { covers.store("al-a_2", null) { it.writeText("") } }

		assertEquals("old", covers.pathOf("al-a")!!.readText())
		assertFalse(covers.isCurrent("al-a_2"))
		assertEquals(listOf("al-a.img"), FileSystem.SYSTEM.list(covers.pathOf("al-a")!!.parent!!).map { it.name }.filter { !it.endsWith(".tsv") })
	}

	@Test fun savedCoversSurviveRestartAndOddIdsWork() = withCovers { _, open ->
		val first = open()
		first.store("dc-album:1_0", null) { it.writeText("disc") }
		first.store("../al/b_5", null) { it.writeText("odd") }
		delay(1_500)

		val reloaded = open()
		assertEquals("disc", reloaded.pathOf("dc-album:1")!!.readText())
		assertEquals("odd", reloaded.pathOf("../al/b_5")!!.readText())
		assertEquals(first.pathOf("dc-album:1")!!.parent, reloaded.pathOf("../al/b_5")!!.parent)
	}

	@Test fun retainOnlyRemovesUnneededCovers() = withCovers { _, open ->
		val covers = open()
		covers.store("al-a_1", null) { it.writeText("a") }
		covers.store("dc-b:1_0", null) { it.writeText("b") }
		val removed = covers.pathOf("al-a")!!

		covers.retainOnly(setOf("dc-b:1", "al-unsaved"))

		assertNull(covers.pathOf("al-a"))
		assertFalse(FileSystem.SYSTEM.exists(removed))
		assertEquals("b", covers.pathOf("dc-b:1")!!.readText())

		covers.clear()
		assertNull(covers.pathOf("dc-b:1"))
	}

	@Test fun usesTheImageCachesCopyOnlyForTheSameVersion() = withCovers { dir, _ ->
		val scope = CoroutineScope(SupervisorJob())
		val imageVersions = CoverArtVersions(dir / "versions.tsv", scope)
		val covers = DownloadedCoverArt(dir / "downloaded", imageVersions, scope)
		val imageCache = DiskCache.Builder().directory(dir / "images").maxSizeBytes(1L shl 20).build()
		imageCache.openEditor(coverArtCacheKey("al-a"))!!.apply {
			data.writeText("cached")
			commit()
		}
		imageVersions.put("al-a", "1")

		covers.store("al-a_2", imageCache) { it.writeText("server") }
		assertEquals("server", covers.pathOf("al-a")!!.readText())

		covers.clear()
		covers.store("al-a_1", imageCache) { error("should not reach the server") }
		assertEquals("cached", covers.pathOf("al-a")!!.readText())

		covers.clear()
		covers.store("al-a_0", imageCache) { error("should not reach the server") }
		assertEquals("cached", covers.pathOf("al-a")!!.readText())
		scope.cancel()
	}

	@Test fun versionsSurviveRestart() = runBlocking {
		val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cover-art-test-${Random.nextLong()}"
		val file = dir / "versions.tsv"
		try {
			val scope = CoroutineScope(SupervisorJob())
			val first = CoverArtVersions(file, scope)
			assertNull(first.get("al-a"))
			first.put("al-a", "1")
			first.put("al-b", "2")
			first.put("al-a", "3")
			assertEquals("3", first.get("al-a"))
			delay(1_500)
			scope.cancel()

			val reloaded = CoverArtVersions(file, CoroutineScope(SupervisorJob()))
			assertEquals("3", reloaded.get("al-a"))
			assertEquals("2", reloaded.get("al-b"))
			assertNull(reloaded.get("al-c"))
		} finally {
			FileSystem.SYSTEM.deleteRecursively(dir)
		}
	}
}
