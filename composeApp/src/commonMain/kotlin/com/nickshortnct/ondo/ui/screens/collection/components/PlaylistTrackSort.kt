package com.nickshortnct.ondo.ui.screens.collection.components

import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_sort_alphabetical_by_artist
import ondo.composeapp.generated.resources.option_sort_alphabetical_by_name
import ondo.composeapp.generated.resources.option_sort_playlist_date_added
import ondo.composeapp.generated.resources.option_sort_playlist_release_year
import org.jetbrains.compose.resources.StringResource
import com.nickshortnct.ondo.domain.models.DomainSong

enum class PlaylistTrackSort(val label: StringResource) {
	DateAdded(Res.string.option_sort_playlist_date_added),
	ReleaseYear(Res.string.option_sort_playlist_release_year),
	Title(Res.string.option_sort_alphabetical_by_name),
	Artist(Res.string.option_sort_alphabetical_by_artist)
}

internal fun sortPlaylistTracks(
	songs: List<DomainSong>,
	sort: PlaylistTrackSort,
	descending: Boolean
): List<DomainSong> {
	return when (sort) {
		// The Subsonic playlist API does not expose a per-entry added timestamp,
		// so its saved playlist order is the best available Date added order.
		PlaylistTrackSort.DateAdded -> if (descending) songs.reversed() else songs
		PlaylistTrackSort.ReleaseYear -> songs.sortedWith(
			compareBy<DomainSong> { it.year == null }
				.thenBy { if (descending) -(it.year ?: 0) else (it.year ?: Int.MAX_VALUE) }
				.thenBy { it.title.lowercase() }
		)
		PlaylistTrackSort.Title -> if (descending) songs.sortedByDescending { it.title.lowercase() } else songs.sortedBy { it.title.lowercase() }
		PlaylistTrackSort.Artist -> if (descending) {
			songs.sortedWith(compareByDescending<DomainSong> { it.artistName.lowercase() }.thenBy { it.title.lowercase() })
		} else {
			songs.sortedWith(compareBy<DomainSong> { it.artistName.lowercase() }.thenBy { it.title.lowercase() })
		}
	}
}
