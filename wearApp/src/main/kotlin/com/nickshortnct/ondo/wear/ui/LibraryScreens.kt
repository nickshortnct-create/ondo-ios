package com.nickshortnct.ondo.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import com.nickshortnct.ondo.wear.OndoWearApp
import com.nickshortnct.ondo.wear.R
import com.nickshortnct.ondo.wear.data.PlayTarget
import com.nickshortnct.ondo.wear.data.WearContract
import com.nickshortnct.ondo.wear.data.Album
import com.nickshortnct.ondo.wear.data.SongCollection
import com.nickshortnct.ondo.wear.downloads.WatchDownloadService

@Composable
fun PlaylistsScreen(navigate: (String) -> Unit) {
	val app = ondoApp
	val loader = rememberLoad("playlists") { app.api.playlists() }
	ScreenList(title = "Playlists") {
		loadItems(loader) { playlists ->
			if (playlists.isEmpty()) item { Text("No playlists", textAlign = TextAlign.Center) }
			items(playlists) { p ->
				MediaRow(p.name, "${p.songCount} songs", app.api.coverArtUri(p.coverArt, 96)) {
					navigate(Routes.collection("playlist", p.id))
				}
			}
		}
	}
}

@Composable
fun AlbumsScreen(type: String, title: String, navigate: (String) -> Unit) {
	val app = ondoApp
	val loader = rememberLoad(type) { app.api.albums(type) }
	ScreenList(title = title) {
		loadItems(loader) { albums -> albumItems(app, albums, navigate) }
	}
}

private fun ScalingLazyListScope.albumItems(app: OndoWearApp, albums: List<Album>, navigate: (String) -> Unit) {
	if (albums.isEmpty()) item { Text("No albums", textAlign = TextAlign.Center) }
	items(albums) { a ->
		MediaRow(a.name, a.artist, app.api.coverArtUri(a.coverArt, 96)) { navigate(Routes.collection("album", a.id)) }
	}
}

@Composable
fun ArtistsScreen(navigate: (String) -> Unit) {
	val app = ondoApp
	val loader = rememberLoad("artists") { app.api.artists() }
	ScreenList(title = "Artists") {
		loadItems(loader) { artists ->
			items(artists) { a ->
				MediaRow(a.name, "${a.albumCount} albums", app.api.coverArtUri(a.coverArt, 96), round = true) {
					navigate(Routes.artist(a.id))
				}
			}
		}
	}
}

@Composable
fun ArtistScreen(id: String, navigate: (String) -> Unit) {
	val app = ondoApp
	val loader = rememberLoad(id) { app.api.artistAlbums(id) }
	val name = (loader.state as? Load.Ready)?.value?.first
	ScreenList(title = name ?: "Artist") {
		loadItems(loader) { (_, albums) ->
			item {
				NavButton("Play artist", R.drawable.ic_play) {
					if (app.settings.playTarget.value == PlayTarget.Phone) {
						// The phone resolves "ondo/artist/<id>" into every song by the artist.
						app.phoneLink.sendAsync(WearContract.CMD_PLAY_MEDIA, "ondo/artist/$id")
						navigate(Routes.PLAYER_PHONE)
					} else {
						app.scopeLaunch {
							val songs = albums.take(20).flatMap { runCatching { app.api.album(it.id).songs }.getOrDefault(emptyList()) }
							app.player.play(songs)
						}
						navigate(Routes.PLAYER_WATCH)
					}
				}
			}
			albumItems(app, albums, navigate)
		}
	}
}

/**
 * Song list for an album, playlist, favourites, random mix or a download.
 * [kind] = album | playlist | starred | random | downloaded.
 */
@Composable
fun CollectionScreen(kind: String, id: String, navigate: (String) -> Unit) {
	val app = ondoApp
	val downloaded by app.downloads.collections.collectAsState()
	val progress by app.downloads.progress.collectAsState()
	val pending by app.downloads.pending.collectAsState()
	val loader = rememberLoad(kind, id) {
		when (kind) {
			"album" -> app.api.album(id)
			"playlist" -> app.api.playlist(id)
			"starred" -> SongCollection("Favourite songs", "", null, app.api.starredSongs(), null, "starred")
			"random" -> SongCollection("Shuffle all", "Random mix", null, app.api.randomSongs(), "ondo/songs", "random")
			"downloaded" -> app.downloads.collections.value.firstOrNull { it.key == id }
				?.let { SongCollection(it.title, it.subtitle, it.coverArt, it.songs, it.phoneMediaId, it.key) }
				?: error("This download was removed")
			else -> error("Unknown list")
		}
	}
	val offline = kind == "downloaded"
	val title = (loader.state as? Load.Ready)?.value?.title
	ScreenList(title = title ?: "Loading") {
		loadItems(loader) { collection ->
			val songs = collection.songs
			if (collection.subtitle.isNotBlank()) item {
				Text(collection.subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
			}
			item {
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
					FilledIconButton(onClick = {
						navigate(Playback.play(app, songs, 0, false, collection, forceWatch = offline))
					}) { Icon(painterResource(R.drawable.ic_play), "Play") }
					FilledTonalIconButton(onClick = {
						navigate(Playback.play(app, songs, 0, true, collection, forceWatch = offline))
					}) { Icon(painterResource(R.drawable.ic_shuffle_on), "Shuffle") }
					val key = collection.downloadKey
					val isDownloaded = offline || downloaded.any { it.key == key }
					val isQueued = progress?.key == key || pending.any { it.downloadKey == key }
					if (kind != "random") FilledTonalIconButton(onClick = {
						when {
							isDownloaded -> { app.downloads.delete(key); if (offline) navigate(Routes.DOWNLOADS) }
							!isQueued -> { app.downloads.enqueue(collection); WatchDownloadService.start(app) }
						}
					}) {
						Icon(
							painterResource(if (isDownloaded) R.drawable.ic_delete else if (isQueued) R.drawable.ic_refresh else R.drawable.ic_download),
							if (isDownloaded) "Remove download" else if (isQueued) "Downloading" else "Download to watch",
						)
					}
				}
			}
			val p = progress
			if (p != null && p.key == collection.downloadKey) item {
				Text("Downloading ${p.done}/${p.total}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
			}
			if (songs.isEmpty()) item { Text("No songs", textAlign = TextAlign.Center) }
			itemsIndexed(songs) { index, song ->
				val isLocal = app.downloads.songFile(song.id) != null
				MediaRow(
					title = song.title,
					subtitle = listOfNotNull(song.artist.ifBlank { null }, formatDuration(song.durationSec * 1000L), if (isLocal) "on watch" else null).joinToString(" · "),
					cover = if (kind == "album") null else app.downloads.coverFile(song.coverArt) ?: app.api.coverArtUri(song.coverArt, 96),
				) {
					navigate(Playback.play(app, songs, index, false, collection, forceWatch = offline))
				}
			}
		}
	}
}
