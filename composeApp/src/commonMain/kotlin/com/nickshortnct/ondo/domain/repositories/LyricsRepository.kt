package com.nickshortnct.ondo.domain.repositories

import com.russhwolf.settings.Settings
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.nickshortnct.ondo.data.database.dao.LyricDao
import com.nickshortnct.ondo.data.database.entities.LyricEntity
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.lyrics.LyricsConfig
import com.nickshortnct.ondo.domain.models.lyrics.LyricsLine
import com.nickshortnct.ondo.domain.models.lyrics.LyricsProvider
import com.nickshortnct.ondo.domain.models.lyrics.LyricsResult
import com.nickshortnct.ondo.domain.parser.LyricsContentParser
import com.nickshortnct.ondo.util.Logger
import kotlin.time.Duration.Companion.milliseconds

class LyricsRepository(
	private val lyricDao: LyricDao,
	private val settings: Settings,
	private val preferenceManager: com.nickshortnct.ondo.domain.manager.PreferenceManager,
	private val sessionManager: SessionManager
) {

	private val client = HttpClient {
		defaultRequest { preferenceManager.customHeadersMap().forEach { (key, value) -> header(key, value) } }
		install(HttpTimeout) {
			requestTimeoutMillis = 40000
			connectTimeoutMillis = 40000
			socketTimeoutMillis = 40000
		}
	}
	private val json = Json { ignoreUnknownKeys = true }

	// regex for finding parenthesis as they are often for crediting producers which can make lrclib
	// miss a search. example "songTitle (prod. producer123)"
	private companion object {
		val PARENTHETICAL = Regex("""\([^)]*\)""")
		val REPEATED_WHITESPACE = Regex("""\s+""")
	}

	private fun getConfig(): LyricsConfig {
		try {
			val raw = settings.getStringOrNull(LyricsConfig.KEY)
				?: return LyricsConfig.Default
			val config: LyricsConfig = json.decodeFromString(raw)
			return config.takeIf { it.version == LyricsConfig.VERSION }
				?: LyricsConfig.Default
		} catch (ex: Exception) {
			if (ex is CancellationException) throw ex
			Logger.w("LyricsRepository", "failed to load config", ex)
			return LyricsConfig.Default
		}
	}

	suspend fun fetchLyrics(song: DomainSong): LyricsResult? {
		try {
			val cached = lyricDao.getLyrics(song.id)
			if (cached != null) {
				val parsed = LyricsContentParser.parse(cached.rawContent)
				if (!parsed.isNullOrEmpty()) return LyricsResult(
					lines = parsed,
					providerName = cached.providerName,
					rawContent = cached.rawContent
				)
			}
		} catch (ex: Exception) {
			if (ex is CancellationException) throw ex
			Logger.w("LyricsRepository", "failed getting cached lyrics", ex)
		}

		val currentConfig = getConfig()
		// Ondo: the first provider with word-by-word timing wins. A line-only result is kept
		// as the fallback while the remaining providers are tried.
		var lineOnly: Triple<List<LyricsLine>, LyricsProvider.Id, String?>? = null
		for (provider in currentConfig.providers.filter { it.enabled }) {
			try {
				var rawContentToCache: String? = null

				val parsedLyrics = when (provider.id) {
					LyricsProvider.Id.LYRICS_PLUS -> {
						val raw = fetchRawLyricsPlus(song, currentConfig)
						rawContentToCache = raw
						raw?.let { LyricsContentParser.parse(it) }
					}

					LyricsProvider.Id.LRCLIB -> {
						val raw = fetchRawLrcLib(song, currentConfig)
						rawContentToCache = raw
						raw?.let { LyricsContentParser.parse(it) }
					}

					LyricsProvider.Id.SUBSONIC -> {
						val subsonicLyrics = sessionManager.api.getLyrics(song.id).firstOrNull()

						val lines = subsonicLyrics?.lines?.flatMap { line ->
							if (!subsonicLyrics.synced && line.value.contains("\n")) {
								line.value.lineSequence()
									.filter { it.isNotBlank() }
									.map { LyricsLine(time = null, text = it.trim()) }
									.toList()
							} else {
								val time =
									if (subsonicLyrics.synced) line.start?.milliseconds else null
								listOf(LyricsLine(time = time, text = line.value))
							}
						}

						if (!lines.isNullOrEmpty()) {
							rawContentToCache = lines.joinToString("\n") { l ->
								val t = l.time
								if (t != null) {
									val m = t.inWholeMinutes.toString().padStart(2, '0')
									val s = (t.inWholeSeconds % 60).toString().padStart(2, '0')
									val ms = ((t.inWholeMilliseconds % 1000) / 10).toString()
										.padStart(2, '0')
									"[$m:$s.$ms]${l.text}"
								} else l.text
							}
						}
						lines
					}
				}

				if (!parsedLyrics.isNullOrEmpty() && parsedLyrics.none { !it.words.isNullOrEmpty() }) {
					if (lineOnly == null) lineOnly = Triple(parsedLyrics, provider.id, rawContentToCache)
					continue
				}
				if (!parsedLyrics.isNullOrEmpty()) {
					try {
						rawContentToCache?.let { content ->
							val entity = LyricEntity(
								songId = song.id,
								providerName = provider.id.name,
								rawContent = content
							)
							lyricDao.insertLyrics(entity)
						}
					} catch (e: Exception) {
			if (e is CancellationException) throw e
						Logger.e("LyricRepository", "Failed to cache lyrics for ${song.title}", e)
					}
					return LyricsResult(
						lines = parsedLyrics,
						providerName = provider.id.name,
						rawContent = rawContentToCache
					)
				}
			} catch (e: Exception) {
			if (e is CancellationException) throw e
				Logger.e("LyricRepository", "Provider ${provider.id.name} failed!", e)
				continue
			}
		}
		val (lines, providerId, raw) = lineOnly ?: return null
		try {
			raw?.let { lyricDao.insertLyrics(LyricEntity(songId = song.id, providerName = providerId.name, rawContent = it)) }
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e("LyricRepository", "Failed to cache lyrics for ${song.title}", e)
		}
		return LyricsResult(lines = lines, providerName = providerId.name, rawContent = raw)
	}

	private suspend fun fetchRawLrcLib(song: DomainSong, config: LyricsConfig): String? {
		return try {
			val response = client.get(config.lrcLibBaseUrl) {
				// using /api/search with q param to search for lyrics more loosely, finds lyrics
				// more consistently because of how one may tag their music. Also looking at how i.e
				// navidrome tags albumless songs with [Unknown Album] which will never find a result
				parameter("q", "${song.title.withoutParentheticals()} ${song.artistName}")
				accept(ContentType.Application.Json)
			}
			if (!response.status.isSuccess()) {
				throw Exception("unsuccessful status code ${response.status.value}")
			}

			// lrclib returns up to 20 results in the form of a json object, select the first one
			// with synced lyrics, or first one with unsynced lyrics if none have any synced. Better
			// matching can be added further to then look for songs that do have a similar album or
			// duration though first result should be fine?
			val results = json.parseToJsonElement(response.bodyAsText()).jsonArray
			val match = results.firstOrNull { it.lyricsOrNull("syncedLyrics") != null }
				?: results.firstOrNull { it.lyricsOrNull("plainLyrics") != null }

			match?.toString()
		} catch (ex: Exception) {
			if (ex is CancellationException) throw ex
			Logger.w(
				"LyricsRepository",
				"failed fetching from lrclib",
				ex
			)
			null
		}
	}

	private fun JsonElement.lyricsOrNull(key: String): String? =
		jsonObject[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

	// remove parts of song title as defined by regex PARENTHETICAL
	private fun String.withoutParentheticals(): String =
		replace(PARENTHETICAL, " ")
			.replace(REPEATED_WHITESPACE, " ")
			.trim()
			.ifEmpty { this }

	private suspend fun fetchRawLyricsPlus(song: DomainSong, config: LyricsConfig): String? =
		coroutineScope {
			val resultChannel = Channel<String>(Channel.UNLIMITED)
			val jobs = config.lyricsPlusMirrors.map { baseUrl ->
				launch {
					try {
						val response = client.get("$baseUrl/v2/lyrics/get") {
							parameter("title", song.title)
							parameter("artist", song.artistName)
							parameter("album", song.albumTitle)
							parameter("duration", song.duration.inWholeSeconds)
							accept(ContentType.Application.Json)
						}
						if (response.status.isSuccess()) {
							resultChannel.send(response.bodyAsText())
						} else {
							throw Exception("unsuccessful status code ${response.status.value}")
						}
					} catch (ex: Exception) {
			if (ex is CancellationException) throw ex
						Logger.w("LyricsRepository", "failed fetching from provider $baseUrl", ex)
					}
				}
			}

			launch {
				jobs.joinAll()
				resultChannel.close()
			}

			val result = resultChannel.receiveCatching().getOrNull()
			jobs.forEach { it.cancel() }
			result
		}
}
