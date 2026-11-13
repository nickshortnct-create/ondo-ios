package com.nickshortnct.ondo.domain.models

import androidx.compose.runtime.Immutable
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_sort_playlist_by_name
import ondo.composeapp.generated.resources.option_sort_playlist_date_added
import ondo.composeapp.generated.resources.option_sort_playlist_duration
import ondo.composeapp.generated.resources.option_sort_playlist_release_year
import ondo.composeapp.generated.resources.option_sort_random
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainPlaylistListType(val displayName: StringResource) {
	Name(Res.string.option_sort_playlist_by_name),
	DateAdded(Res.string.option_sort_playlist_date_added),
	ReleaseYear(Res.string.option_sort_playlist_release_year),
	Duration(Res.string.option_sort_playlist_duration),
	Random(Res.string.option_sort_random)
}
