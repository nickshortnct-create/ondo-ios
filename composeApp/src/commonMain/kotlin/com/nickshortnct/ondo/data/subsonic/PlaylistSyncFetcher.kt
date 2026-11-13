package com.nickshortnct.ondo.data.subsonic

import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.entities.SongEntity
import com.nickshortnct.ondo.domain.models.DomainContributor
import com.nickshortnct.ondo.domain.models.DomainExplicitStatus
import com.nickshortnct.ondo.domain.models.DomainSongArtist
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.russhwolf.settings.Settings
import dev.zt64.subsonic.client.SubsonicAuth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val tolerantSubsonicJson = Json { ignoreUnknownKeys = true }

data class PlaylistSyncResult(
	val playlist: PlaylistEntity,
	val entries: List<PlaylistEntrySync>,
)

data class PlaylistEntrySync(
	val id: String,
	val title: String,
)

/**
 * Raw Subsonic JSON fetcher for Navidrome minimal-client responses.
 *
 * Navidrome strips playlist metadata (owner, comment, coverArt, entry fields)
 * for clients in ND_SUBSONIC_MINIMALCLIENTS. The stock subsonic-kotlin models
 * require those fields, so login/sync must bypass the library for playlist APIs.
 */
class PlaylistSyncFetcher(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager,
) {
	private val httpClient = HttpClient {
		install(UserAgent) {
			agent = SubsonicClientNames.MINIMAL
		}
		install(HttpTimeout)
	}

	/**
	 * The playlist list carries no songs, so it's fetched as [SubsonicClientNames.METADATA]: the minimal
	 * response drops owner, comment and coverArt, and without them the weekly mixes (marked in the comment)
	 * were never recognised and got recreated on every refresh.
	 */
	suspend fun fetchAllPlaylists(): List<PlaylistEntity> {
		val response = requestSubsonic("getPlaylists", clientName = SubsonicClientNames.METADATA)
		return response.playlists?.toEntities().orEmpty()
	}

	suspend fun fetch(playlistId: String): PlaylistSyncResult {
		val response = requestSubsonic("getPlaylist", mapOf("id" to playlistId))
		val playlist = response.playlist
			?: throw IllegalStateException("Playlist $playlistId missing from Subsonic response")

		return PlaylistSyncResult(
			playlist = playlist.toEntity(),
			entries = playlist.entries(),
		)
	}

	private suspend fun requestSubsonic(
		endpoint: String,
		extraParams: Map<String, String> = emptyMap(),
		clientName: String = SubsonicClientNames.MINIMAL,
	): SubsonicResponseBody {
		val responseText = httpClient.get(buildUrl(endpoint, extraParams, clientName)) {
			preferenceManager.customHeadersMap().forEach { (key, value) ->
				header(key, value)
			}
		}.body<String>()

		val envelope = tolerantSubsonicJson.decodeFromString<SubsonicEnvelope>(responseText)
		val response = envelope.response
		check(response.status == "ok") {
			response.error?.message ?: "Subsonic $endpoint failed"
		}
		return response
	}

	private fun buildUrl(endpoint: String, extraParams: Map<String, String>, clientName: String): String {
		val instanceUrl = settings.getString("instanceUrl", "")
		val username = settings.getString("username", "")
		val password = settings.getString("password", "")
		val auth = SubsonicAuth.Token(username = username, password = password)

		return URLBuilder().apply {
			takeFrom("$instanceUrl/rest/$endpoint")
			parameters.append("f", "json")
			parameters.append("v", API_VERSION)
			parameters.append("c", clientName)
			parameters.append("u", auth.username)
			parameters.append("t", auth.token)
			parameters.append("s", auth.salt)
			extraParams.forEach { (key, value) -> parameters.append(key, value) }
		}.buildString()
	}

	companion object {
		private const val API_VERSION = "1.16.1"
	}
}

@Serializable
private data class SubsonicEnvelope(
	@SerialName("subsonic-response") val response: SubsonicResponseBody,
)

@Serializable
private data class SubsonicResponseBody(
	val status: String,
	val playlist: RawPlaylist? = null,
	val playlists: RawPlaylistsContainer? = null,
	val error: SubsonicError? = null,
)

@Serializable
private data class SubsonicError(
	val code: Int,
	val message: String,
)

@Serializable
private data class RawPlaylistsContainer(
	@SerialName("playlist") val items: JsonElement? = null,
)

private fun RawPlaylistsContainer.toEntities(): List<PlaylistEntity> {
	val element = items ?: return emptyList()
	val rawPlaylists = when (element) {
		is JsonArray -> element.mapNotNull { playlistElement ->
			(playlistElement as? JsonObject)?.let { tolerantSubsonicJson.decodeFromJsonElement<RawPlaylist>(it) }
		}

		is JsonObject -> listOf(tolerantSubsonicJson.decodeFromJsonElement<RawPlaylist>(element))
		else -> emptyList()
	}
	return rawPlaylists.map { it.toEntity() }
}

@Serializable
private data class RawPlaylist(
	val id: String,
	val name: String,
	val owner: String? = null,
	val comment: String? = null,
	@SerialName("coverArt") val coverArt: String? = null,
	val songCount: Int = 0,
	val duration: Int = 0,
	@SerialName("created") val createdAt: String? = null,
	@SerialName("changed") val modifiedAt: String? = null,
	val public: Boolean? = null,
	@SerialName("readonly") val readOnly: Boolean? = null,
	@SerialName("entry") val entry: JsonElement? = null,
)

private fun RawPlaylist.entries(): List<PlaylistEntrySync> {
	val entryElement = entry ?: return emptyList()
	return when (entryElement) {
		is JsonArray -> entryElement.mapNotNull { it.toPlaylistEntry() }
		is JsonObject -> listOfNotNull(entryElement.toPlaylistEntry())
		else -> emptyList()
	}
}

private fun JsonElement.toPlaylistEntry(): PlaylistEntrySync? {
	val objectElement = this as? JsonObject ?: return null
	val id = objectElement["id"]?.jsonPrimitive?.contentOrNull ?: return null
	val title = objectElement["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
	return PlaylistEntrySync(id = id, title = title)
}

private fun RawPlaylist.toEntity() = PlaylistEntity(
	playlistId = id,
	name = name,
	owner = owner.orEmpty(),
	comment = comment,
	coverArtId = coverArt,
	songCount = songCount,
	duration = duration.seconds,
	public = public,
	readOnly = readOnly,
	createdAt = createdAt?.let(Instant::parse) ?: Instant.DISTANT_PAST,
	modifiedAt = modifiedAt?.let(Instant::parse) ?: Instant.DISTANT_PAST,
	validUntil = null,
	allowedUsers = emptyList(),
)

fun PlaylistEntrySync.toPlaceholderEntity() = SongEntity(
	songId = id,
	title = title,
	artistName = "Unknown Artist",
	artistId = "unknown artist",
	albumTitle = null,
	belongsToAlbumId = null,
	parentId = null,
	comment = null,
	trackNumber = null,
	discNumber = null,
	isrc = emptyList(),
	year = null,
	genre = null,
	genres = emptyList(),
	moods = emptyList(),
	duration = 0.seconds,
	bpm = null,
	contributors = emptyList<DomainContributor>(),
	playCount = 0,
	userRating = null,
	averageRating = null,
	bitRate = null,
	bitDepth = null,
	sampleRate = null,
	audioChannelCount = null,
	replayGain = null,
	fileSize = 0L,
	fileExtension = "",
	mimeType = "application/octet-stream",
	filePath = null,
	starredAt = null,
	coverArtId = null,
	musicBrainzId = null,
	explicitStatus = DomainExplicitStatus.Unknown,
	artists = emptyList<DomainSongArtist>(),
	albumArtists = emptyList<DomainSongArtist>(),
	isExternal = false,
)
