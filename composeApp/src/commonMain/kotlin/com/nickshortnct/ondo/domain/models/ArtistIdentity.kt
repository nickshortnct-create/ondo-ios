package com.nickshortnct.ondo.domain.models

// Known IDs disambiguate artists who share a name. Name matching remains useful
// when a server has not supplied MusicBrainz metadata yet.
fun artistIdentityMatches(
	name: String,
	musicBrainzId: String?,
	otherName: String,
	otherMusicBrainzId: String?
): Boolean {
	val id = musicBrainzId.orEmpty().trim()
	val otherId = otherMusicBrainzId.orEmpty().trim()
	if (id.isNotEmpty() && otherId.isNotEmpty()) return id.equals(otherId, ignoreCase = true)
	val normalizedName = normalizeForMatch(name)
	return normalizedName.isNotEmpty() && normalizedName == normalizeForMatch(otherName)
}
