package com.nickshortnct.ondo.domain.models

import androidx.compose.runtime.Immutable
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.option_sort_alphabetical_by_name
import ondo.composeapp.generated.resources.option_sort_random
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainArtistListType(val displayName: StringResource) {
	AlphabeticalByName(Res.string.option_sort_alphabetical_by_name),
	Random(Res.string.option_sort_random)
}
