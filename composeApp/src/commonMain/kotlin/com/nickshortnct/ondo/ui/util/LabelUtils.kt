package com.nickshortnct.ondo.ui.util

import androidx.compose.runtime.Composable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.count_hours
import ondo.composeapp.generated.resources.count_minutes
import ondo.composeapp.generated.resources.option_sort_alphabetical_by_artist
import ondo.composeapp.generated.resources.option_sort_alphabetical_by_name
import ondo.composeapp.generated.resources.option_sort_by_genre
import ondo.composeapp.generated.resources.option_sort_by_year
import ondo.composeapp.generated.resources.option_sort_frequent
import ondo.composeapp.generated.resources.option_sort_newest
import ondo.composeapp.generated.resources.option_sort_random
import ondo.composeapp.generated.resources.option_sort_rating
import ondo.composeapp.generated.resources.option_sort_recent
import ondo.composeapp.generated.resources.option_sort_year
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.domain.models.DomainAlbumListType
import com.nickshortnct.ondo.domain.models.DomainSongListType
import com.nickshortnct.ondo.domain.models.lyrics.LyricsProvider
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Composable
fun Duration.label(): String {
	val hours = inWholeHours.toInt()
	val minutes = (this - hours.hours).inWholeMinutes.toInt()

	return when {
		hours > 0 && minutes > 0 ->
			"${pluralStringResource(Res.plurals.count_hours, hours, hours)} ${
				pluralStringResource(
					Res.plurals.count_minutes,
					minutes,
					minutes
				)
			}"

		hours > 0 ->
			pluralStringResource(Res.plurals.count_hours, hours, hours)

		else ->
			pluralStringResource(Res.plurals.count_minutes, max(1, minutes), max(1, minutes))
	}
}

@Composable
fun DomainAlbumListType.label() = when (this) {
	DomainAlbumListType.Random -> stringResource(Res.string.option_sort_random)
	DomainAlbumListType.Newest -> stringResource(Res.string.option_sort_newest)
	DomainAlbumListType.Frequent -> stringResource(Res.string.option_sort_frequent)
	DomainAlbumListType.Recent -> stringResource(Res.string.option_sort_recent)
	DomainAlbumListType.AlphabeticalByName -> stringResource(Res.string.option_sort_alphabetical_by_name)
	DomainAlbumListType.AlphabeticalByArtist -> stringResource(Res.string.option_sort_alphabetical_by_artist)
	DomainAlbumListType.Highest -> stringResource(Res.string.option_sort_rating)
	DomainAlbumListType.Year -> stringResource(Res.string.option_sort_year)
	is DomainAlbumListType.ByGenre -> stringResource(Res.string.option_sort_by_genre)
	is DomainAlbumListType.ByYear -> stringResource(Res.string.option_sort_by_year)
}

@Composable
fun DomainSongListType.label() = when (this) {
	is DomainSongListType.ByArtist -> stringResource(Res.string.option_sort_by_genre)
	is DomainSongListType.ByGenre -> stringResource(Res.string.option_sort_by_genre)
	DomainSongListType.FrequentlyPlayed -> stringResource(Res.string.option_sort_frequent)
	DomainSongListType.Newest -> stringResource(Res.string.option_sort_newest)
	DomainSongListType.Random -> stringResource(Res.string.option_sort_random)
	DomainSongListType.Rating -> stringResource(Res.string.option_sort_rating)
	DomainSongListType.Year -> stringResource(Res.string.option_sort_by_year)
}

fun PaletteStyle.label(): String = when (this) {
	PaletteStyle.TonalSpot -> "Tonal Spot"
	PaletteStyle.Neutral -> "Neutral"
	PaletteStyle.Vibrant -> "Vibrant"
	PaletteStyle.Expressive -> "Expressive"
	PaletteStyle.Rainbow -> "Rainbow"
	PaletteStyle.FruitSalad -> "Fruit Salad"
	PaletteStyle.Monochrome -> "Monochrome"
	PaletteStyle.Fidelity -> "Fidelity"
	PaletteStyle.Content -> "Content"
}

fun ColorSpec.SpecVersion.label() = when (this) {
	ColorSpec.SpecVersion.SPEC_2021 -> "Material 3 (2021)"
	ColorSpec.SpecVersion.SPEC_2025 -> "Expressive (2025)"
}

fun LyricsProvider.Id.label() = when (this) {
	LyricsProvider.Id.SUBSONIC -> "Subsonic"
	LyricsProvider.Id.LYRICS_PLUS -> "LyricsPlus"
	LyricsProvider.Id.LRCLIB -> "LRCLIB"
}
