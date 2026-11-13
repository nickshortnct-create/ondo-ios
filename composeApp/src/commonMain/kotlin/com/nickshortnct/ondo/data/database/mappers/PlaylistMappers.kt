package com.nickshortnct.ondo.data.database.mappers

import com.nickshortnct.ondo.data.database.entities.PlaylistEntity
import com.nickshortnct.ondo.data.database.relations.PlaylistWithSongs
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import dev.zt64.subsonic.api.model.Playlist as ApiPlaylist

fun ApiPlaylist.toEntity() = PlaylistEntity(
	playlistId = id,
	name = name,
	owner = owner,
	comment = comment,
	coverArtId = coverArtId,
	songCount = songCount,
	duration = duration,
	createdAt = createdAt,
	modifiedAt = modifiedAt,
	public = public,
	readOnly = readOnly,
	allowedUsers = allowedUsers,
	validUntil = validUntil
)

fun PlaylistWithSongs.toDomainModel() = DomainPlaylist(
	id = playlist.playlistId,
	name = playlist.name,
	owner = playlist.owner,
	comment = playlist.comment,
	coverArtId = playlist.coverArtId,
	customCoverPath = playlist.customCoverPath,
	songCount = playlist.songCount,
	duration = playlist.duration,
	createdAt = playlist.createdAt,
	modifiedAt = playlist.modifiedAt,
	public = playlist.public,
	readOnly = playlist.readOnly,
	allowedUsers = playlist.allowedUsers,
	validUntil = playlist.validUntil,
	songs = songs
		.sortedBy { it.crossRef.position }
		.map { it.song.toDomainModel() }
)

fun DomainPlaylist.toEntity() = PlaylistEntity(
	playlistId = id,
	name = name,
	owner = owner,
	comment = comment,
	coverArtId = coverArtId,
	customCoverPath = customCoverPath,
	songCount = songCount,
	duration = duration,
	createdAt = createdAt,
	modifiedAt = modifiedAt,
	public = public,
	readOnly = readOnly,
	allowedUsers = allowedUsers,
	validUntil = validUntil
)

fun PlaylistEntity.toDomainModel() = PlaylistWithSongs(this, emptyList()).toDomainModel()
fun com.nickshortnct.ondo.data.database.relations.PlaylistSummary.toDomainModel() =
	playlist.toDomainModel().copy(cachedSongIds = songIds.orEmpty())
