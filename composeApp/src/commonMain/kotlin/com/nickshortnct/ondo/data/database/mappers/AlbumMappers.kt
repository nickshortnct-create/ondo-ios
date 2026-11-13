package com.nickshortnct.ondo.data.database.mappers

import com.nickshortnct.ondo.data.database.entities.AlbumEntity
import com.nickshortnct.ondo.data.database.relations.AlbumWithSongs
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainAlbumInfo
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.AlbumInfo as ApiAlbumInfo

fun ApiAlbum.toEntity(
	artistIdOverride: String? = null,
	artistNameOverride: String? = null
) = AlbumEntity(
	albumId = this.id,
	name = this.name,
	// TODO: figure out why this can be null and how to handle it
	artistId = artistIdOverride ?: this.artistId ?: "unknown artist",
	artistName = artistNameOverride ?: this.artistName,
	coverArtId = this.coverArtId,
	songCount = this.songCount,
	duration = this.duration,
	year = this.year,
	genre = this.genre,
	starredAt = this.starredAt,
	userRating = this.userRating,
	musicBrainzId = this.musicBrainzId,
	createdAt = this.createdAt,
	lastPlayedAt = this.lastPlayedAt,
	playCount = this.playCount,
	genres = this.genres,
	version = this.version,
	isExternal = this.isExternal
)

fun AlbumWithSongs.toDomainModel() = DomainAlbum(
	id = album.albumId,
	name = album.name,
	artistId = album.artistId,
	artistName = album.artistName,
	coverArtId = album.coverArtId,
	songCount = album.songCount,
	duration = album.duration,
	year = album.year,
	genre = album.genre,
	starredAt = album.starredAt,
	userRating = album.userRating,
	musicBrainzId = album.musicBrainzId,
	createdAt = album.createdAt,
	lastPlayedAt = album.lastPlayedAt,
	playCount = album.playCount,
	genres = album.genres,
	version = album.version,
	songs = songs
		.map { it.toDomainModel() }
		.sortedWith(compareBy({ it.discNumber }, { it.trackNumber })),
	isExternal = album.isExternal
)

fun DomainAlbum.toEntity() = AlbumEntity(
	albumId = this.id,
	name = this.name,
	artistId = this.artistId,
	artistName = this.artistName,
	coverArtId = this.coverArtId,
	songCount = this.songCount,
	duration = this.duration,
	year = this.year,
	genre = this.genre,
	starredAt = this.starredAt,
	userRating = this.userRating,
	musicBrainzId = this.musicBrainzId,
	createdAt = this.createdAt,
	lastPlayedAt = this.lastPlayedAt,
	playCount = this.playCount,
	genres = this.genres,
	version = this.version,
	isExternal = this.isExternal
)

fun ApiAlbumInfo.toDomainModel() = DomainAlbumInfo(
	musicBrainzId = musicBrainzId,
	largeImageUrl = largeImageUrl,
	mediumImageUrl = mediumImageUrl,
	smallImageUrl = smallImageUrl,
	lastFmUrl = lastFmUrl,
	notes = notes
)

fun AlbumEntity.toDomainModel() = AlbumWithSongs(this, emptyList()).toDomainModel()
fun com.nickshortnct.ondo.data.database.relations.AlbumSummary.toDomainModel() =
	album.toDomainModel().copy(cachedSongIds = songIds.orEmpty())
