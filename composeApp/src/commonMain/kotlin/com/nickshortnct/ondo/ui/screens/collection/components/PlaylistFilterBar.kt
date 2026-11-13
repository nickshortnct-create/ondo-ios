package com.nickshortnct.ondo.ui.screens.collection.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_clear_search
import ondo.composeapp.generated.resources.action_save_mix_copy
import ondo.composeapp.generated.resources.hint_search_playlist
import ondo.composeapp.generated.resources.info_filtered_count
import ondo.composeapp.generated.resources.info_mix_badge
import ondo.composeapp.generated.resources.info_mix_replaced_weekly
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Close
import com.nickshortnct.ondo.icons.outlined.Search
import com.nickshortnct.ondo.ondo.MixTag
import com.nickshortnct.ondo.ondo.SongFilterChip
import com.nickshortnct.ondo.ondo.mixWeekLabel

/**
 * The strip under a playlist's play buttons, modelled on Spotify's Liked Songs: an optional search
 * field, then a scrolling row of mood and genre chips. One chip at a time; tapping it again clears it.
 */
@Composable
fun PlaylistFilterBar(
	searchActive: Boolean,
	query: String,
	onQueryChange: (String) -> Unit,
	onCloseSearch: () -> Unit,
	chips: List<SongFilterChip>,
	selectedChip: String?,
	onSelectChip: (String?) -> Unit,
	shownCount: Int,
	totalCount: Int,
	mixTag: MixTag?,
	onSaveMixCopy: (() -> Unit)?,
) {
	Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
		if (mixTag != null) {
			MixBanner(mixTag, onSaveMixCopy)
		}

		AnimatedVisibility(searchActive) {
			val focusRequester = remember { FocusRequester() }
			val focusManager = LocalFocusManager.current
			LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
			OutlinedTextField(
				value = query,
				onValueChange = onQueryChange,
				singleLine = true,
				placeholder = { Text(stringResource(Res.string.hint_search_playlist)) },
				leadingIcon = { Icon(Icons.Outlined.Search, null) },
				trailingIcon = {
					IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onCloseSearch() }) {
						Icon(Icons.Outlined.Close, stringResource(Res.string.action_clear_search))
					}
				},
				shape = RoundedCornerShape(28.dp),
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp)
					.focusRequester(focusRequester)
			)
		}

		if (chips.isNotEmpty()) {
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(horizontal = 16.dp),
				modifier = Modifier.padding(vertical = 4.dp)
			) {
				items(chips, key = { it.key }) { chip ->
					val selected = chip.key == selectedChip
					FilterChip(
						selected = selected,
						onClick = { onSelectChip(if (selected) null else chip.key) },
						label = { Text(if (chip.emoji.isNotBlank()) "${chip.emoji}  ${chip.label}" else chip.label) },
						shape = RoundedCornerShape(50),
						colors = FilterChipDefaults.filterChipColors(
							selectedContainerColor = MaterialTheme.colorScheme.primary,
							selectedLabelColor = MaterialTheme.colorScheme.onPrimary
						),
						modifier = Modifier.animateItem()
					)
				}
			}
		}

		if (shownCount != totalCount) {
			Text(
				stringResource(Res.string.info_filtered_count, shownCount, totalCount),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
			)
		}
	}
}

@Composable
private fun MixBanner(tag: MixTag, onSaveCopy: (() -> Unit)?) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Column(Modifier.weight(1f)) {
			Text(
				"${tag.emoji}  " + stringResource(Res.string.info_mix_badge, mixWeekLabel(tag.week)),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.primary
			)
			Text(
				stringResource(Res.string.info_mix_replaced_weekly),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		if (onSaveCopy != null) {
			FilledTonalButton(onClick = onSaveCopy) {
				Text(stringResource(Res.string.action_save_mix_copy))
			}
		}
	}
}
