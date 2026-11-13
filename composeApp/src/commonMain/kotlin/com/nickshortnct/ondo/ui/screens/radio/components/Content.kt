package com.nickshortnct.ondo.ui.screens.radio.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_no_radios
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.domain.models.DomainRadio
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Radio
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.core.UiState

fun LazyGridScope.radioListScreenContent(
	state: UiState<List<DomainRadio>>,
	onRadioClick: (DomainRadio) -> Unit
) {
	val data = state.data.orEmpty()

	if (data.isNotEmpty()) {
		items(data, key = { it.id }) { radio ->
			RadioListScreenCard(
				modifier = Modifier.animateItem(),
				radio = radio,
				onPlayClick = { onRadioClick(radio) }
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				items(10) {
					RadioListScreenCardPlaceholder()
				}
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.Radio,
						label = stringResource(Res.string.info_no_radios)
					)
				}
			}
		}
	}
}
