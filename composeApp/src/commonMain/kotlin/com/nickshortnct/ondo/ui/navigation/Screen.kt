package com.nickshortnct.ondo.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainArtistListType
import com.nickshortnct.ondo.domain.models.DomainSongListType

@Serializable
enum class OndoPage {
	@SerialName("connect") Connect,
	@SerialName("downloads") Downloads,
	@SerialName("upgrades") Upgrades,
	@SerialName("settings") Integrations;
	companion object {
		fun fromLegacy(value: String): OndoPage = when (value) {
			"connect" -> Connect; "downloads" -> Downloads; "upgrades" -> Upgrades; else -> Integrations
		}
	}
}

@Immutable
@Serializable
sealed interface Screen : NavKey {

	// tabs
	@Immutable
	@Serializable
	data class Library(
		val nested: Boolean = false
	) : Screen

	@Immutable
	@Serializable
	data class Starred(
		val nested: Boolean = false,
		val listType: DomainArtistListType = DomainArtistListType.AlphabeticalByName
	) : Screen

	@Immutable
	@Serializable
	data class PlaylistList(
		val nested: Boolean = false
	) : Screen

	@Immutable
	@Serializable
	data class ArtistList(
		val nested: Boolean = false,
		val listType: DomainArtistListType = DomainArtistListType.AlphabeticalByName
	) : Screen

	@Immutable
	@Serializable
	data class AlbumList(
		val nested: Boolean = false,
		val listType: DomainAlbumListType = DomainAlbumListType.AlphabeticalByArtist
	) : Screen

	@Immutable
	@Serializable
	data class GenreList(
		val nested: Boolean = false
	) : Screen

	@Immutable
	@Serializable
	data class GenreDetail(
		val genreName: String
	) : Screen

	@Immutable
	@Serializable
	data class SongList(
		val nested: Boolean = false,
		val listType: DomainSongListType = DomainSongListType.FrequentlyPlayed
	) : Screen

	@Immutable
	@Serializable
	data class RadioList(
		val nested: Boolean = false
	) : Screen

	@Serializable
	data class Ondo(val page: OndoPage) : Screen

	// misc
	@Immutable
	@Serializable
	data object Login : Screen

	@Immutable
	@Serializable
	data object NowPlaying : Screen

	@Immutable
	@Serializable
	data object Lyrics : Screen

	@Immutable
	@Serializable
	data object Queue : Screen

	@Immutable
	@Serializable
	data object PlaybackSpeed : Screen

	@Immutable
	@Serializable
	data class CollectionDetail(
		val collectionId: String,
		val tab: String
	) : Screen

	@Immutable
	@Serializable
	data class SongDetailSheet(val songId: String, val coverArtId: String? = null) : Screen

	@Immutable
	@Serializable
	data class SongDetailScreen(val songId: String, val coverArtId: String? = null) : Screen

	@Immutable
	@Serializable
	data class Search(
		val nested: Boolean = false,
		val query: String? = null
	) : Screen

	@Immutable
	@Serializable
	data object ShareList : Screen

	@Immutable
	@Serializable
	data class ArtistDetail(val artist: String) : Screen

	// An artist found via a decknas downloader (Lidarr/SoulSync/etc.) that isn't
	// in the local library yet, so there's no local artistId for ArtistDetail.
	// skipLocalRedirect: normally this screen bounces straight to ArtistDetail
	// when the artist turns out to already be local -- set true when navigating
	// here *from* ArtistDetail itself (e.g. a discography carousel's "See all"),
	// so it shows the full list instead of bouncing right back.
	@Immutable
	@Serializable
	data class DiscoveryArtist(val name: String, val musicBrainzId: String = "", val skipLocalRedirect: Boolean = false) : Screen

	// A not-yet-downloaded album/EP found via discography() (see OndoDownloads) --
	// shows its real tracklist (from MusicBrainz) plus download/choose-release
	// actions, since a compact bottom sheet has no room for a full tracklist.
	@Immutable
	@Serializable
	data class DiscographyAlbum(val foreignAlbumId: String, val title: String, val artistName: String) : Screen

	// settings
	@Immutable
	@Serializable
	sealed interface Settings : Screen {
		@Immutable
		@Serializable
		data object Root : Settings

		@Immutable
		@Serializable
		data object Appearance : Settings

		@Immutable
		@Serializable
		data object Playback : Settings

		@Immutable
		@Serializable
		data object Developer : Settings

		@Immutable
		@Serializable
		data object BottomAppBar : Settings

		@Immutable
		@Serializable
		data object NowPlaying : Settings

		@Immutable
		@Serializable
		data object About : Settings

		@Immutable
		@Serializable
		data object DataStorage : Settings

		@Immutable
		@Serializable
		data object Fonts : Settings

		@Immutable
		@Serializable
		data object Themes : Settings

		@Immutable
		@Serializable
		data object Effects: Settings
		@Immutable
		@Serializable
		data object CustomHeaders : Settings

		@Immutable
		@Serializable
		data object StreamingQuality : Settings

		@Immutable
		@Serializable
		data object DownloadQuality : Settings

		@Immutable
		@Serializable
		data object Logs : Settings

		@Immutable
		@Serializable
		data object AppIcon : Settings

		@Immutable
		@Serializable
		data object Equaliser : Settings
	}
}
