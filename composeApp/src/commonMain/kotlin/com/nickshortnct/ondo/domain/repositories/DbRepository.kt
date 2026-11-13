package com.nickshortnct.ondo.domain.repositories

import androidx.room3.concurrent.AtomicInt
import dev.zt64.subsonic.api.model.SubsonicException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.nickshortnct.ondo.data.subsonic.PlaylistSyncFetcher
import com.nickshortnct.ondo.data.subsonic.toPlaceholderEntity
import com.nickshortnct.ondo.util.chunkedIn
import com.nickshortnct.ondo.util.findSerializationException
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_syncing
import ondo.composeapp.generated.resources.info_syncing_albums
import ondo.composeapp.generated.resources.info_syncing_artists
import ondo.composeapp.generated.resources.info_syncing_finished
import ondo.composeapp.generated.resources.info_syncing_genres
import ondo.composeapp.generated.resources.info_syncing_playlists
import ondo.composeapp.generated.resources.info_syncing_radios
import ondo.composeapp.generated.resources.info_syncing_saved
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.dao.GenreDao
import com.nickshortnct.ondo.data.database.dao.LyricDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.dao.RadioDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.dao.SyncActionDao
import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.PlaylistSongCrossRef
import com.nickshortnct.ondo.data.database.entities.SongEntity
import com.nickshortnct.ondo.data.database.entities.isMetadataPlaceholder
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.mappers.toEntity
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.util.Logger
import kotlin.coroutines.cancellation.CancellationException
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.AlbumListType as ApiAlbumListType

class DbRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val genreDao: GenreDao,
	private val artistDao: ArtistDao,
	private val radioDao: RadioDao,
	private val lyricDao: LyricDao,
	private val syncDao: SyncActionDao,
	private val sessionManager: SessionManager,
	private val playlistSyncFetcher: PlaylistSyncFetcher,
) {
	private val concurrentRequestLimit = Semaphore(20)

	private val dbChunkSize = 500 // should be enough

	/** A light sync refreshes at most this many albums' track lists; the daily full sync catches the rest. */
	private val MAX_LIGHT_ALBUM_REFRESH = 100

	private suspend fun <T> runDbOp(block: suspend () -> T): Result<T> =
		withContext(Dispatchers.IO) {
			try {
				Result.success(block())
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Result.failure(e)
			}
		}

	suspend fun removeEverything(): Result<Unit> = runDbOp {
		albumDao.clearAllAlbums()
		playlistDao.clearAllPlaylists()
		songDao.clearAllSongs()
		genreDao.clearAllGenres()
		artistDao.clearAllArtists()
		radioDao.clearAllRadios()
		lyricDao.clearAllLyrics()
		syncDao.clearAllActions()
		Logger.i("DbRepository", "Database wiped completely.")
	}

	suspend fun syncEverything(
		onProgress: (Float, StringResource) -> Unit = { _, _ -> }
	): Result<Unit> = runDbOp {
		val progressCallback = suspend { progress: Float, message: StringResource ->
			Logger.i("DbRepository", "$progress ${getString(message)}")
			onProgress(progress, message)
		}

		progressCallback(0.0f, Res.string.info_syncing)

		progressCallback(0.01f, Res.string.info_syncing_genres)
		syncGenres().getOrThrow()

		progressCallback(0.02f, Res.string.info_syncing_radios)
		try {
			syncRadios().getOrThrow()
		} catch (ex: SubsonicException) {
			Logger.e(
				tag = "DbRepository",
				msg = "could not sync radio stations, maybe this server doesn't support it",
				tr = ex
			)
		}

		progressCallback(0.04f, Res.string.info_syncing_artists)
		syncArtists().getOrThrow()

		progressCallback(0.07f, Res.string.info_syncing_playlists)
		val playlists = syncPlaylists().getOrThrow()

		val libraryResult = syncLibrarySongs { localProgress, message ->
			val globalProgress = 0.10f + (localProgress * 0.65f)
			progressCallback(globalProgress, message)
		}.getOrThrow()

		val validAlbumIds = libraryResult.albumIds
		val validSongIds = libraryResult.songIds.toMutableSet()
		var syncComplete = libraryResult.skippedAlbums == 0

		val totalPlaylists = playlists.size
		if (totalPlaylists > 0) {
			val completedPlaylists = AtomicInt(0)

			// Each task returns its own set; merging happens after awaitAll() so no
			// shared collection is written from several threads.
			val playlistResults = coroutineScope {
				playlists.map { playlist ->
					async {
						val outcome = concurrentRequestLimit.withPermit {
							syncPlaylistSongs(playlist.playlistId, enrich = false)
						}
						val done = completedPlaylists.incrementAndGet()
						val globalProgress = 0.75f + (0.25f * (done.toFloat() / totalPlaylists))
						progressCallback(globalProgress, Res.string.info_syncing_playlists)
						playlist.playlistId to outcome
					}
				}.awaitAll()
			}

			val merged = mergePlaylistOutcomes(
				outcomes = playlistResults,
				cachedSongIds = { playlistDao.getPlaylistSongIds(it) },
				onFailure = { playlistId, error ->
					Logger.e("DbRepository", "could not sync playlist $playlistId; keeping its cached songs", error)
				}
			)
			validSongIds.addAll(merged.songIds)
			if (!merged.complete) syncComplete = false
		}

		// Placeholders are enriched once, outside any request permit.
		enrichIncompleteSongs(validSongIds)

		if (syncComplete) {
			albumDao.deleteObsoleteAlbums(validAlbumIds)
			songDao.deleteObsoleteSongs(validSongIds)
		} else {
			Logger.w(
				"DbRepository",
				"sync was incomplete; skipping deletion of albums/songs missing from the server"
			)
		}

		progressCallback(1.0f, Res.string.info_syncing_finished)
	}

	/** Pages through every album summary (one cheap call per 500 albums). */
	private suspend fun fetchAlbumSummaries(): List<ApiAlbum> {
		val pageSize = 500
		var offset = 0
		val summaries = mutableListOf<ApiAlbum>()
		while (true) {
			val batch = sessionManager.api.getAlbums(ApiAlbumListType.AlphabeticalByName, pageSize, offset)
			if (batch.isEmpty()) break
			summaries.addAll(batch)
			if (batch.size < pageSize) break
			offset += pageSize
		}
		return summaries
	}

	private suspend fun loadExistingAlbums(summaries: List<ApiAlbum>): Map<String, AlbumEntity> =
		chunkedIn(summaries.map { it.id }, dbChunkSize) { albumDao.getAlbumEntitiesByIds(it) }
			.associateBy { it.albumId }

	/**
	 * Refreshes the album list without fetching every album's full track list.
	 * Album tracks are loaded on demand when a collection is opened.
	 */
	suspend fun syncLibraryAlbumSummaries(): Result<Unit> = runDbOp {
		val summaries = fetchAlbumSummaries()

		// An empty response can mean a temporarily unavailable server. Keep the
		// cached library intact instead of treating it as a request to erase it.
		if (summaries.isEmpty()) return@runDbOp

		writeAlbumSummaries(summaries, loadExistingAlbums(summaries))
	}

	private suspend fun writeAlbumSummaries(summaries: List<ApiAlbum>, existingAlbums: Map<String, AlbumEntity>) {
		summaries.map { summary ->
			val remote = summary.toEntity()
			val local = existingAlbums[remote.albumId] ?: return@map remote
			remote.copy(
				name = remote.name.ifBlank { local.name },
				artistName = remote.artistName.ifBlank { local.artistName },
				artistId = remote.artistId.takeUnless { it == "unknown artist" } ?: local.artistId,
				coverArtId = remote.coverArtId.ifBlank { local.coverArtId },
				year = remote.year ?: local.year,
				genre = remote.genre ?: local.genre,
				genres = remote.genres.ifEmpty { local.genres },
				duration = remote.duration ?: local.duration,
				starredAt = local.starredAt,
				lastPlayedAt = remote.lastPlayedAt ?: local.lastPlayedAt,
				playCount = maxOf(remote.playCount, local.playCount),
				userRating = local.userRating,
				version = remote.version ?: local.version,
				musicBrainzId = remote.musicBrainzId ?: local.musicBrainzId
			)
		}.chunked(dbChunkSize).forEach { albumDao.insertAlbums(it) }

		Logger.i("DbRepository", "Album summaries synced: ${summaries.size} albums")
	}


	/**
	 * Cheap periodic refresh: changed playlists, the album list, and full details only for albums
	 * that are new or whose track count/duration changed. Never deletes anything; the daily full
	 * sync owns removals.
	 */
	suspend fun syncLight(): Result<Unit> = runDbOp {
		syncPlaylistsIncremental()

		val summaries = fetchAlbumSummaries()
		if (summaries.isEmpty()) return@runDbOp
		val existing = loadExistingAlbums(summaries)

		val changed = summaries.filter { summary ->
			val local = existing[summary.id] ?: return@filter true
			val remote = summary.toEntity()
			local.songCount != remote.songCount || local.duration != remote.duration
		}.take(MAX_LIGHT_ALBUM_REFRESH)

		if (changed.isNotEmpty()) {
			Logger.i("DbRepository", "Light sync refreshing ${changed.size} new/changed album(s)")
			coroutineScope {
				changed.map { summary ->
					async {
						concurrentRequestLimit.withPermit {
							try {
								saveFullAlbum(sessionManager.api.getAlbum(summary.id), summary)
							} catch (e: Exception) {
								if (e is CancellationException) throw e
								Logger.e("DbRepository", "could not refresh album ${summary.id}; skipping it", e)
							}
						}
					}
				}.awaitAll()
			}
		}

		writeAlbumSummaries(summaries, existing)
	}

	/** Fetches the playlist list and re-reads the songs of playlists that are new or changed. */
	private suspend fun syncPlaylistsIncremental() {
		val before = playlistDao.getAllPlaylistEntities().associateBy { it.playlistId }
		val remote = syncPlaylists().getOrThrow()
		val changed = remote.filter { playlist ->
			val local = before[playlist.playlistId] ?: return@filter true
			local.modifiedAt != playlist.modifiedAt || local.songCount != playlist.songCount
		}
		for (playlist in changed) {
			syncPlaylistSongs(playlist.playlistId).onFailure {
				Logger.e("DbRepository", "could not refresh playlist ${playlist.playlistId}", it)
			}
		}
	}

	private suspend fun saveFullAlbum(album: ApiAlbum, summary: ApiAlbum?) {
		val albumEntity = album.toEntity(
			artistIdOverride = summary?.artistId,
			artistNameOverride = summary?.artistName
		)
		val songEntities = album.songs.map { song ->
			song.toEntity(
				artistIdOverride = albumEntity.artistId.takeIf { song.artistId.isNullOrBlank() },
				artistNameOverride = albumEntity.artistName.takeIf { song.artistName.isBlank() }
			)
		}
		val (albums, songs) = keepPendingLocalState(listOf(albumEntity), songEntities)
		albumDao.insertAlbums(albums)
		songDao.insertSongs(songs)
	}

	/**
	 * Items with a queued star/rating action keep their local star/rating: the server hasn't heard
	 * about the change yet, so a pull must not overwrite it with the old value.
	 */
	private suspend fun keepPendingLocalState(
		albums: List<AlbumEntity>,
		songs: List<SongEntity>
	): Pair<List<AlbumEntity>, List<SongEntity>> {
		val pending = syncDao.getPendingActions().mapTo(mutableSetOf()) { it.itemId }
		if (pending.isEmpty()) return albums to songs

		val localSongs = chunkedIn(songs.map { it.songId }.filter { it in pending }, dbChunkSize) { songDao.getSongsByIds(it) }
			.associateBy { it.songId }
		val localAlbums = chunkedIn(albums.map { it.albumId }.filter { it in pending }, dbChunkSize) { albumDao.getAlbumEntitiesByIds(it) }
			.associateBy { it.albumId }
		return albums.map { album ->
			localAlbums[album.albumId]?.let { album.copy(starredAt = it.starredAt, userRating = it.userRating) } ?: album
		} to songs.map { song ->
			localSongs[song.songId]?.let { song.copy(starredAt = it.starredAt, userRating = it.userRating) } ?: song
		}
	}

	/** [skippedAlbums] counts albums that could not be read, so callers know the id sets are partial. */
	data class LibrarySyncResult(
		val albumIds: Set<String>,
		val songIds: Set<String>,
		val skippedAlbums: Int
	)

	suspend fun syncLibrarySongs(
		onProgress: suspend (Float, StringResource) -> Unit = { _, _ -> }
	): Result<LibrarySyncResult> = runDbOp {
		val pageSize = 500
		var offset = 0
		val allAlbumSummaries = mutableListOf<ApiAlbum>()

		onProgress(0.0f, Res.string.info_syncing_albums)
		while (true) {
			val batch =
				sessionManager.api.getAlbums(ApiAlbumListType.AlphabeticalByName, pageSize, offset)
			if (batch.isEmpty()) break
			allAlbumSummaries.addAll(batch)
			if (batch.size < pageSize) break
			offset += pageSize
		}

		// An empty answer may be a temporarily unavailable server; flag it as partial so nothing is deleted.
		if (allAlbumSummaries.isEmpty()) return@runDbOp LibrarySyncResult(emptySet(), emptySet(), 1)

		val totalAlbums = allAlbumSummaries.size
		val completedAlbums = AtomicInt(0)
		val skippedAlbums = AtomicInt(0)
		var finalSongsSynced = 0

		val allValidAlbumIds = mutableSetOf<String>()
		val allValidSongIds = mutableSetOf<String>()

		onProgress(0.1f, Res.string.info_syncing_albums)

		val albumChannel = Channel<ApiAlbum>(capacity = 100)

		coroutineScope {
			launch(Dispatchers.IO) {
				allAlbumSummaries.map { summary ->
					launch {
						concurrentRequestLimit.withPermit {
							try {
								val album = sessionManager.api.getAlbum(summary.id)

								val done = completedAlbums.incrementAndGet()
								val fetchProgress = 0.1f + (0.8f * (done.toFloat() / totalAlbums))
								onProgress(fetchProgress, Res.string.info_syncing_albums)

								albumChannel.send(album)
							} catch (e: Exception) {
								if (e.findSerializationException() != null) {
									Logger.e(
										"DbRepository",
										"could not deserialize album ${summary.id} (${summary.name}); skipping it",
										e
									)
								} else {
									throw e
								}
							}
						}
					}
				}.joinAll()
				albumChannel.close()
			}

			launch(Dispatchers.IO) {
				val albumBatch = mutableListOf<AlbumEntity>()
				val songBatch = mutableListOf<SongEntity>()
				val summariesMap = allAlbumSummaries.associateBy { it.id }

				for (album in albumChannel) {
					val summary = summariesMap[album.id]
					val albumEntity = album.toEntity(
						artistIdOverride = summary?.artistId,
						artistNameOverride = summary?.artistName
					)
					albumBatch.add(albumEntity)
					allValidAlbumIds.add(albumEntity.albumId)

					album.songs.forEach { song ->
						val songEntity = song.toEntity(
							artistIdOverride = albumEntity.artistId.takeIf { song.artistId.isNullOrBlank() },
							artistNameOverride = albumEntity.artistName.takeIf { song.artistName.isBlank() }
						)
						songBatch.add(songEntity)
						allValidSongIds.add(songEntity.songId)
					}

					if (albumBatch.size >= dbChunkSize || songBatch.size >= 1500) {
						val (albumsToWrite, songsToWrite) = keepPendingLocalState(albumBatch, songBatch)
						albumDao.insertAlbums(albumsToWrite)
						songDao.insertSongs(songsToWrite)

						finalSongsSynced += songBatch.size
						albumBatch.clear()
						songBatch.clear()
					}
				}

				if (albumBatch.isNotEmpty() || songBatch.isNotEmpty()) {
					val (albumsToWrite, songsToWrite) = keepPendingLocalState(albumBatch, songBatch)
					if (albumsToWrite.isNotEmpty()) albumDao.insertAlbums(albumsToWrite)
					if (songsToWrite.isNotEmpty()) songDao.insertSongs(songsToWrite)
					finalSongsSynced += songBatch.size
				}
			}
		}

		Logger.i(
			"DbRepository",
			"- Songs Synced: $totalAlbums albums, $finalSongsSynced songs"
		)

		onProgress(1.0f, Res.string.info_syncing_saved)
		LibrarySyncResult(allValidAlbumIds, allValidSongIds, skippedAlbums.get())
	}

	suspend fun syncPlaylists(): Result<List<PlaylistEntity>> = runDbOp {
		val playlistEntities = playlistSyncFetcher.fetchAllPlaylists()
		val validPlaylistIds = playlistEntities.map { it.playlistId }.toSet()

		val customCovers = playlistDao.getAllPlaylistEntities()
			.mapNotNull { entity -> entity.customCoverPath?.let { entity.playlistId to it } }
			.toMap()
		playlistEntities.chunked(dbChunkSize).forEach { chunk ->
			playlistDao.insertPlaylists(chunk.map { it.copy(customCoverPath = customCovers[it.playlistId]) })
		}

		playlistDao.deleteObsoletePlaylists(validPlaylistIds)

		Logger.i("DbRepository", "- Playlists Synced: ${playlistEntities.size} playlists found")

		playlistEntities
	}

	suspend fun syncPlaylistSongs(
		playlistId: String,
		tolerateDeserializeError: Boolean = true,
		enrich: Boolean = true,
	): Result<Set<String>> = runDbOp {
		val syncResult = try {
			playlistSyncFetcher.fetch(playlistId)
		} catch (e: Exception) {
			// Only an unreadable payload is tolerated; network and server errors must surface so a
			// flaky connection is never mistaken for an empty playlist.
			if (tolerateDeserializeError && e.findSerializationException() != null) {
				Logger.e(
					"DbRepository",
					"could not deserialize playlist $playlistId; keeping its cached songs",
					e
				)
				return@runDbOp playlistDao.getPlaylistSongIds(playlistId).toSet()
			}
			throw e
		}

		val playlistEntity = syncResult.playlist.copy(
			customCoverPath = playlistDao.getCustomCoverPath(playlistId)
		)
		val songEntities = syncResult.entries.map { it.toPlaceholderEntity() }
		val songIds = syncResult.entries.map { it.id }.toSet()

		songEntities.chunked(dbChunkSize).forEach { chunk ->
			// Minimal Navidrome playlist entries only carry id/title; keep richer
			// metadata already synced from albums/library scans.
			songDao.insertSongsIgnoringConflicts(chunk)
		}

		val crossRefs = syncResult.entries.mapIndexed { index, entry ->
			PlaylistSongCrossRef(playlistId = playlistId, songId = entry.id, position = index)
		}
		playlistDao.replacePlaylistWithSongs(playlistEntity, crossRefs)

		Logger.i("DbRepository", "- Playlist [$playlistId] synced: ${songEntities.size} songs")
		if (enrich) enrichIncompleteSongs(songIds)
		songIds
	}

	private suspend fun enrichIncompleteSongs(songIds: Set<String>) {
		val candidates = songDao.getPlaceholderSongs().filter { it.songId in songIds }
		if (candidates.isEmpty()) return

		Logger.i("DbRepository", "- Enriching ${candidates.size} placeholder songs")

		coroutineScope {
			candidates.map { local ->
				async {
					concurrentRequestLimit.withPermit {
						try {
							val remote = sessionManager.api.getSong(local.songId)
							songDao.insertSong(remote.toEntity())
						} catch (e: Exception) {
							if (e.findSerializationException() != null) {
								Logger.e(
									"DbRepository",
									"could not enrich song ${local.songId}; leaving placeholder",
									e
								)
							} else {
								throw e
							}
						}
					}
				}
			}.awaitAll()
		}
	}

	suspend fun syncGenres(): Result<Unit> = runDbOp {
		val remoteGenres = sessionManager.api.getGenres()
		val entities = remoteGenres.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			genreDao.insertGenres(chunk)
		}
		genreDao.deleteObsoleteGenres(entities.map { it.genreName }.toSet())

		Logger.i("DbRepository", "- Genres Synced: ${entities.size} genres found")
	}

	suspend fun syncArtists(): Result<Unit> = runDbOp {
		val remoteArtistsWrapper = sessionManager.api.getArtists()
		val flatArtists = remoteArtistsWrapper.flatMap { indexGroup ->
			indexGroup.artists
		}
		val existing = chunkedIn(flatArtists.map { it.id }) { artistDao.getArtistsByIds(it) }.associateBy { it.artistId }
		val entities = flatArtists.map { remote ->
			val incoming = remote.toEntity()
			val cached = existing[incoming.artistId]
			incoming.copy(
				biography = incoming.biography?.takeIf { it.isNotBlank() } ?: cached?.biography,
				similarArtistIds = incoming.similarArtistIds.ifEmpty { cached?.similarArtistIds.orEmpty() },
				lastFmUrl = incoming.lastFmUrl?.takeIf { it.isNotBlank() } ?: cached?.lastFmUrl
			)
		}

		entities.chunked(dbChunkSize).forEach { chunk ->
			artistDao.insertArtists(chunk)
		}
		artistDao.deleteObsoleteArtists(entities.map { it.artistId }.toSet())

		Logger.i("DbRepository", "- Artists Synced: ${entities.size} artists found")
	}

	suspend fun syncRadios(): Result<Unit> = runDbOp {
		val remoteRadios = sessionManager.api.getInternetRadioStations()
		val entities = remoteRadios.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			radioDao.insertRadios(chunk)
		}
		radioDao.deleteObsoleteRadios(entities.map { it.radioId }.toSet())

		Logger.i("DbRepository", "- Radios Synced: ${entities.size} stations found")
	}

	suspend fun fetchArtistMetadata(artistId: String): Result<DomainArtist> = runDbOp {
		val artistInfo = sessionManager.api.getArtistInfo(artistId)
		val simIds = artistInfo.similarArtists.map { it.id }

		val currentEntity = artistDao.getArtistById(artistId)
			?: throw Exception("Artist not found in local DB")

		val updatedEntity = currentEntity.copy(
			biography = artistInfo.biography,
			similarArtistIds = simIds,
			lastFmUrl = artistInfo.lastFmUrl
		)

		artistDao.insertArtist(updatedEntity)

		updatedEntity.toDomainModel()
	}
}

internal data class PlaylistMerge(val songIds: Set<String>, val complete: Boolean)

/**
 * Merges per-playlist sync outcomes. A playlist that failed to sync keeps its cached songs
 * (they stay "valid") and marks the whole run incomplete so nothing gets deleted because of
 * a flaky connection.
 */
internal suspend fun mergePlaylistOutcomes(
	outcomes: List<Pair<String, Result<Set<String>>>>,
	cachedSongIds: suspend (String) -> Collection<String>,
	onFailure: (String, Throwable?) -> Unit = { _, _ -> }
): PlaylistMerge {
	val songIds = mutableSetOf<String>()
	var complete = true
	for ((playlistId, outcome) in outcomes) {
		val synced = outcome.getOrNull()
		if (synced != null) {
			songIds.addAll(synced)
		} else {
			onFailure(playlistId, outcome.exceptionOrNull())
			songIds.addAll(cachedSongIds(playlistId))
			complete = false
		}
	}
	return PlaylistMerge(songIds, complete)
}
