package com.nickshortnct.ondo.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DomainSongArtist(
	val id: String,
	val name: String
)
