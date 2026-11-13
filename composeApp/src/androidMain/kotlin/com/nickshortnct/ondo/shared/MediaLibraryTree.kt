package com.nickshortnct.ondo.shared

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.nickshortnct.ondo.data.database.dao.AlbumDao
import com.nickshortnct.ondo.data.database.dao.ArtistDao
import com.nickshortnct.ondo.data.database.dao.PlaylistDao
import com.nickshortnct.ondo.data.database.dao.SongDao
import com.nickshortnct.ondo.data.database.mappers.toDomainModel
import com.nickshortnct.ondo.data.database.LibrarySearchTable
import com.nickshortnct.ondo.data.database.librarySearchQuery
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.PlaybackSearchRequest
import com.nickshortnct.ondo.domain.models.ResultKind
import com.nickshortnct.ondo.domain.models.mergeSearchResults
import com.nickshortnct.ondo.domain.models.normalizeForMatch
import com.nickshortnct.ondo.domain.models.normalizeSearchQuery
import com.nickshortnct.ondo.domain.models.searchRelevance
import com.nickshortnct.ondo.util.Logger
import com.nickshortnct.ondo.util.chunkedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Media ids used by the Android Auto / MediaBrowser tree.
 *
 * Track ids are hierarchical on purpose. Auto hands the session nothing but a
 * media id when the user taps a row, so the id is the only place the *context*
 * of the tap can live. Without it, tapping track 4 of an album would enqueue
 * that one track and stop at the end of it; with it, [MediaLibraryTree.resolve]
 * can rebuild the whole album and start at track 4, which is what every other
 * car app does and what users expect.
 */
object MediaId {
	const val ROOT = "ondo/root"

	const val PLAYLISTS = "ondo/playlists"
	const val ALBUMS = "ondo/albums"
	const val ARTISTS = "ondo/artists"
	const val RECENT = "ondo/recent"
	const val STARRED = "ondo/starred"
	const val SHUFFLE = "ondo/songs"
	const val MORE = "ondo/more"

	private const val ALBUM_PREFIX = "ondo/album/"
	private const val ARTIST_PREFIX = "ondo/artist/"
	private const val PLAYLIST_PREFIX = "ondo/playlist/"
	private const val TRACK_PREFIX = "ondo/track/"

	fun album(id: String) = "$ALBUM_PREFIX$id"
	fun artist(id: String) = "$ARTIST_PREFIX$id"
	fun playlist(id: String) = "$PLAYLIST_PREFIX$id"

	fun albumIdOf(mediaId: String) = mediaId.removePrefix(ALBUM_PREFIX).takeIf { mediaId.startsWith(ALBUM_PREFIX) }
	fun artistIdOf(mediaId: String) = mediaId.removePrefix(ARTIST_PREFIX).takeIf { mediaId.startsWith(ARTIST_PREFIX) }
	fun playlistIdOf(mediaId: String) = mediaId.removePrefix(PLAYLIST_PREFIX).takeIf { mediaId.startsWith(PLAYLIST_PREFIX) }

	/** A track, remembering which folder it was browsed from. */
	fun track(parent: TrackParent, songId: String) = when (parent) {
		is TrackParent.Album -> "${TRACK_PREFIX}album/${parent.id}/$songId"
		is TrackParent.Playlist -> "${TRACK_PREFIX}playlist/${parent.id}/$songId"
		TrackParent.None -> "${TRACK_PREFIX}plain/$songId"
	}

	fun parseTrack(mediaId: String): Pair<TrackParent, String>? {
		if (!mediaId.startsWith(TRACK_PREFIX)) return null
		val rest = mediaId.removePrefix(TRACK_PREFIX)
		val parts = rest.split('/')
		return when {
			parts.size == 3 && parts[0] == "album" -> TrackParent.Album(parts[1]) to parts[2]
			parts.size == 3 && parts[0] == "playlist" -> TrackParent.Playlist(parts[1]) to parts[2]
			parts.size == 2 && parts[0] == "plain" -> TrackParent.None to parts[1]
			else -> null
		}
	}
}

sealed interface TrackParent {
	data class Album(val id: String) : TrackParent
	data class Playlist(val id: String) : TrackParent
	data object None : TrackParent
}

/** What the user picked, expanded into a queue and a position to start at. */
data class ResolvedSelection(val songs: List<DomainSong>, val startIndex: Int)

/**
 * The browsable content Android Auto (and any other MediaBrowser client) sees.
 *
 * Everything is read from the local Room cache rather than the Subsonic API:
 * Auto expects browse calls to answer fast, the cache is already what the app's
 * own library screens render, and it keeps working on a flaky car connection.
 * The flip side is that an unsynced library browses empty -- that is a sync
 * problem, not a browse problem.
 */
@OptIn(UnstableApi::class)
class MediaLibraryTree(
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val itemFactory: OndoMediaItemFactory
) {
	/** Auto's lists get long; cap them so a huge library can't blow the binder limit. */
	private val maxRows = 400

	/**
	 * How many tabs the connected head unit shows at the root (Auto passes
	 * this as a root hint, usually 4). Categories past the limit were simply
	 * never shown in the car; they now go into a "More" tab instead.
	 */
	@Volatile
	var rootChildrenLimit: Int = Int.MAX_VALUE

	fun rootItem(): MediaItem = browsableItem(
		mediaId = MediaId.ROOT,
		title = "Ondo",
		mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
	)

	/**
	 * Root hints telling Auto how to lay the tree out: top level as big
	 * category tiles, and the leaf lists as rows.
	 */
	fun rootExtras(): Bundle = Bundle().apply {
		putInt(
			MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
			MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
		)
		putInt(
			MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
			MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
		)
	}

	suspend fun children(parentId: String): List<MediaItem> = try {
		when (parentId) {
			MediaId.ROOT -> rootCategories().let { all ->
				if (all.size <= rootChildrenLimit) all
				else all.take((rootChildrenLimit - 1).coerceAtLeast(0)) + category(MediaId.MORE, "More", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
			}
			MediaId.MORE -> rootCategories().drop((rootChildrenLimit - 1).coerceAtLeast(0))
			MediaId.PLAYLISTS -> playlists()
			MediaId.ALBUMS -> albums()
			MediaId.ARTISTS -> artists()
			MediaId.RECENT -> recentAlbums()
			MediaId.STARRED -> starredAlbums()
			MediaId.SHUFFLE -> shuffleSongs()
			else -> when {
				MediaId.albumIdOf(parentId) != null -> albumTracks(MediaId.albumIdOf(parentId)!!)
				MediaId.artistIdOf(parentId) != null -> artistAlbums(MediaId.artistIdOf(parentId)!!)
				MediaId.playlistIdOf(parentId) != null -> playlistTracks(MediaId.playlistIdOf(parentId)!!)
				else -> emptyList()
			}
		}
	} catch (ex: CancellationException) { throw ex
	} catch (ex: Exception) {
		// A browse call that throws shows up in the car as a spinner that never
		// resolves. An empty folder is a worse library but a better failure.
		Logger.e("MediaLibraryTree", "children($parentId) failed", ex)
		emptyList()
	}

	suspend fun item(mediaId: String): MediaItem? {
		MediaId.parseTrack(mediaId)?.let { (parent, songId) ->
			val song = songDao.getSongById(songId)?.toDomainModel() ?: return null
			return itemFactory.toMediaItem(song, MediaId.track(parent, songId))
		}
		MediaId.albumIdOf(mediaId)?.let { id ->
			val album = albumDao.getAlbumById(id)?.toDomainModel() ?: return null
			return browsableItem(
				mediaId = mediaId,
				title = album.name,
				subtitle = album.artistName + if (album.songCount > maxRows) " · first $maxRows tracks shown" else "",
				artworkUri = itemFactory.coverArtUri(album.coverArtId)?.toString(),
				mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
			)
		}
		MediaId.playlistIdOf(mediaId)?.let { id ->
			val playlist = playlistDao.getPlaylistById(id)?.toDomainModel() ?: return null
			return browsableItem(
				mediaId = mediaId,
				title = playlist.name,
				subtitle = "${playlist.songCount} tracks",
				artworkUri = itemFactory.coverArtUri(playlist.coverArtId)?.toString(),
				mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
			)
		}
		if (mediaId == MediaId.ROOT) return rootItem()
		if (mediaId == MediaId.MORE) return category(MediaId.MORE, "More", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
		rootCategories().firstOrNull { it.mediaId == mediaId }?.let { return it }
		// Bare song id: what the playback queue uses, and what a controller that
		// never browsed (a widget, the watch) would ask for.
		val song = songDao.getSongById(mediaId)?.toDomainModel() ?: return null
		return itemFactory.toMediaItem(song)
	}

	fun toPlayableMediaItem(song: DomainSong, mediaId: String = song.id): MediaItem =
		itemFactory.toMediaItem(song, mediaId)

	/**
	 * Turns a tapped media id into the queue that should actually play. This is
	 * the half of Android Auto support that makes the tree *work* rather than
	 * merely render: browse items carry no URI, so without this every tap
	 * enqueues an unplayable item.
	 */
	suspend fun resolve(mediaId: String): ResolvedSelection? = try {
		val track = MediaId.parseTrack(mediaId)
		when {
			track != null -> resolveTrack(track.first, track.second)
			MediaId.albumIdOf(mediaId) != null ->
				albumDao.getAlbumById(MediaId.albumIdOf(mediaId)!!)
					?.toDomainModel()
					?.songs
					?.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
					?.let { ResolvedSelection(it, 0) }

			MediaId.playlistIdOf(mediaId) != null ->
				playlistDao.getPlaylistById(MediaId.playlistIdOf(mediaId)!!)
					?.toDomainModel()
					?.songs
					?.let { ResolvedSelection(it, 0) }

			MediaId.artistIdOf(mediaId) != null ->
				songDao.getSongsByArtistId(MediaId.artistIdOf(mediaId)!!)
					.map { it.toDomainModel() }
					.takeIf { it.isNotEmpty() }
					?.let { ResolvedSelection(it, 0) }

			mediaId == MediaId.SHUFFLE || mediaId == MediaId.ROOT ->
				songDao.getRandomSongs(100)
					.map { it.toDomainModel() }
					.takeIf { it.isNotEmpty() }
					?.let { ResolvedSelection(it, 0) }

			else -> songDao.getSongById(mediaId)
				?.toDomainModel()
				?.let { ResolvedSelection(listOf(it), 0) }
		}
	} catch (ex: CancellationException) { throw ex
	} catch (ex: Exception) {
		Logger.e("MediaLibraryTree", "resolve($mediaId) failed", ex)
		null
	}

	private suspend fun resolveTrack(parent: TrackParent, songId: String): ResolvedSelection? {
		val siblings = when (parent) {
			is TrackParent.Album -> albumDao.getAlbumById(parent.id)
				?.toDomainModel()
				?.songs
				?.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))

			is TrackParent.Playlist -> playlistDao.getPlaylistById(parent.id)?.toDomainModel()?.songs
			TrackParent.None -> null
		}
		if (siblings != null) {
			val index = siblings.indexOfFirst { it.id == songId }
			// The cache can lag the id in the tapped row (a re-sync between
			// browsing and tapping). Falling back to the single track beats
			// starting the album from the wrong place.
			if (index >= 0) return ResolvedSelection(siblings, index)
		}
		val song = songDao.getSongById(songId)?.toDomainModel() ?: return null
		return ResolvedSelection(listOf(song), 0)
	}

	suspend fun search(query: String): List<MediaItem> {
		val cleaned = normalizeSearchQuery(query)
		if (cleaned.isEmpty()) return emptyList()
		return searchLibrary(cleaned)
	}

	private suspend fun searchLibrary(query: String): List<MediaItem> = try {
		val songs = songDao.searchSongsList(librarySearchQuery(query, LibrarySearchTable.SONG)).take(maxRows).map {
			itemFactory.toMediaItem(
				it.toDomainModel(),
				MediaId.track(TrackParent.None, it.songId)
			)
		}
		val albums = albumDao.searchAlbumEntities(librarySearchQuery(query, LibrarySearchTable.ALBUM)).take(50).map { it.toDomainModel() }.map {
			browsableItem(
				mediaId = MediaId.album(it.id),
				title = it.name,
				subtitle = it.artistName,
				artworkUri = itemFactory.coverArtUri(it.coverArtId)?.toString(),
				mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
			)
		}
		albums + songs
	} catch (ex: CancellationException) {
		throw ex
	} catch (ex: Exception) {
		Logger.e("MediaLibraryTree", "search($query) failed", ex)
		emptyList()
	}

	suspend fun resolveSearch(request: PlaybackSearchRequest): ResolvedSelection? {
		val term = request.lookupTerm
		if (term.isBlank()) return resolve(MediaId.SHUFFLE)
		val matches = coroutineScope {
			val songs = async { songDao.searchSongsList(librarySearchQuery(term, LibrarySearchTable.SONG)).map { it.toDomainModel() } }
			val artists = async { artistDao.searchArtistsList(librarySearchQuery(term, LibrarySearchTable.ARTIST)).map { it.toDomainModel() } }
			val albums = async { albumDao.searchAlbumsList(librarySearchQuery(term, LibrarySearchTable.ALBUM)).map { it.toDomainModel() } }
			val playlists = async { playlistDao.searchPlaylistsList(librarySearchQuery(term, LibrarySearchTable.PLAYLIST)).map { it.toDomainModel() } }
			songs.await() + artists.await() + albums.await() + playlists.await()
		}
		val artist = normalizeForMatch(request.artist)
		val candidates = mergeSearchResults(term, matches, emptyList()).filter { result ->
			(request.focus == null || result.kind == request.focus) &&
				(artist.isBlank() || result.kind == ResultKind.PLAYLIST ||
					normalizeForMatch(result.artist).contains(artist))
		}.sortedWith(compareByDescending<com.nickshortnct.ondo.domain.models.UnifiedSearchResult> {
			searchRelevance(term, it)
		}.thenBy { if (it.kind == ResultKind.SONG) 0 else 1 })
		for (candidate in candidates) {
			val songs = when (val item = candidate.library) {
				is DomainSong -> listOf(item)
				is DomainAlbum -> item.songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
				is DomainPlaylist -> item.songs
				is DomainArtist -> songDao.getSongsByArtistId(item.id).map { it.toDomainModel() }
				else -> emptyList()
			}
			if (songs.isNotEmpty()) return ResolvedSelection(songs.take(maxRows), 0)
		}
		// A cache can contain playable songs before its artist/album record arrives.
		val matchingSongs = matches.filterIsInstance<DomainSong>().filter { song ->
			val artistMatches = artist.isBlank() || normalizeForMatch(song.artistName).contains(artist)
			artistMatches && when (request.focus) {
				ResultKind.ARTIST -> normalizeForMatch(song.artistName).contains(normalizeForMatch(term))
				ResultKind.ALBUM -> normalizeForMatch(song.albumTitle.orEmpty()) == normalizeForMatch(term)
				else -> false
			}
		}.sortedWith(compareBy({ it.albumTitle }, { it.discNumber }, { it.trackNumber }))
		return matchingSongs.takeIf { it.isNotEmpty() }?.let { ResolvedSelection(it.take(maxRows), 0) }
	}

	private fun rootCategories() = listOf(
		category(MediaId.PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
		category(MediaId.ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
		category(MediaId.ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
		category(MediaId.RECENT, "Recently added", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
		category(MediaId.STARRED, "Starred", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
		category(MediaId.SHUFFLE, "Shuffle all", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
	)

		private suspend fun playlists() = playlistDao.browsePlaylists(maxRows).map { it.toDomainModel() }.map {
		browsableItem(mediaId = MediaId.playlist(it.id), title = it.name, subtitle = "${it.songCount} tracks" + if (it.songCount > maxRows) " · first $maxRows shown" else "",
			artworkUri = itemFactory.coverArtUri(it.coverArtId)?.toString(), mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST)
	}

	private suspend fun albums() = albumDao.browseAlbums(maxRows).map { it.toDomainModel() }.map(::albumRow)
	private suspend fun recentAlbums() = albumDao.browseRecentAlbums(maxRows).map { it.toDomainModel() }.map(::albumRow)
	private suspend fun starredAlbums() = albumDao.browseStarredAlbums(maxRows).map { it.toDomainModel() }.map(::albumRow)

	private suspend fun artists() = artistDao.getArtistsAlphabeticalByName()
		.take(maxRows)
		.map { it.toDomainModel() }
		.map {
			browsableItem(
				mediaId = MediaId.artist(it.id),
				title = it.name,
				subtitle = "${it.albumCount} albums",
				artworkUri = it.artistImageUrl ?: itemFactory.coverArtUri(it.coverArtId)?.toString(),
				mediaType = MediaMetadata.MEDIA_TYPE_ARTIST
			)
		}

	private suspend fun artistAlbums(artistId: String) = albumDao.browseArtistAlbums(artistId, maxRows)
		.map { it.toDomainModel() }.map(::albumRow)

	private suspend fun albumTracks(albumId: String): List<MediaItem> {
		val album = albumDao.getAlbumById(albumId)?.toDomainModel() ?: return emptyList()
		return album.songs
			.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
			.take(maxRows)
			.map {
				itemFactory.toMediaItem(
					it,
					MediaId.track(TrackParent.Album(albumId), it.id)
				)
			}
	}

	private suspend fun playlistTracks(playlistId: String): List<MediaItem> {
		val playlist = playlistDao.getPlaylistById(playlistId)?.toDomainModel() ?: return emptyList()
		return playlist.songs.take(maxRows).map {
			itemFactory.toMediaItem(
				it,
				MediaId.track(TrackParent.Playlist(playlistId), it.id)
			)
		}
	}

	private suspend fun shuffleSongs() = songDao.getRandomSongs(100)
		.map { it.toDomainModel() }
		.map {
			itemFactory.toMediaItem(
				it,
				MediaId.track(TrackParent.None, it.id)
			)
		}

	private fun albumRow(album: com.nickshortnct.ondo.domain.models.DomainAlbum) = browsableItem(
		mediaId = MediaId.album(album.id),
		title = album.name,
		subtitle = album.artistName,
		artworkUri = itemFactory.coverArtUri(album.coverArtId)?.toString(),
		mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
	)

	private fun category(mediaId: String, title: String, mediaType: Int) = browsableItem(
		mediaId = mediaId,
		title = title,
		mediaType = mediaType,
		// Only "Shuffle all" (and the root itself) are actually resolvable as a
		// whole -- resolve() has no "play every playlist"/"play every album"/
		// "play every artist" case, it falls through to songDao.getSongById(),
		// finds nothing, and resolveForPlayback() ends up handing Media3 an
		// empty item list. Some Auto head units treat a tap on a browsable+
		// playable row as "play it" rather than "browse into it", so a category
		// wrongly marked playable just silently does nothing when tapped --
		// this is what made Playlists/Albums/Artists inert. Only mark the ones
		// resolve() can actually fulfill as playable.
		isPlayable = mediaId == MediaId.SHUFFLE,
		extras = Bundle().apply {
			// Albums and artists read better as artwork tiles; the rest as rows.
			val style = if (mediaId == MediaId.ALBUMS || mediaId == MediaId.ARTISTS) {
				MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
			} else {
				MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
			}
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
			putInt(
				MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
				MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
			)
		}
	)

	private fun browsableItem(
		mediaId: String,
		title: String,
		subtitle: String? = null,
		artworkUri: String? = null,
		mediaType: Int,
		// A browsable that is also playable makes Auto offer "play the whole
		// thing" on the folder itself. Default true because that's correct for
		// the common case (albums/playlists/artists rows -- resolve() supports
		// all three); category() overrides it to false for the containers that
		// don't have a "play all of these" meaning.
		isPlayable: Boolean = true,
		extras: Bundle? = null
	): MediaItem {
		val metadata = MediaMetadata.Builder()
			.setTitle(title)
			.setSubtitle(subtitle)
			.setIsBrowsable(true)
			.setIsPlayable(isPlayable)
			.setMediaType(mediaType)
			.apply {
				artworkUri?.let { setArtworkUri(android.net.Uri.parse(it)) }
				extras?.let { setExtras(it) }
			}
			.build()

		return MediaItem.Builder()
			.setMediaId(mediaId)
			.setMediaMetadata(metadata)
			.build()
	}
}
