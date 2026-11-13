package com.nickshortnct.ondo.ui.screens.playlist.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.action_choose_cover
import ondo.composeapp.generated.resources.action_edit_playlist
import ondo.composeapp.generated.resources.action_remove_cover
import ondo.composeapp.generated.resources.action_save
import ondo.composeapp.generated.resources.info_cover_will_be_removed
import ondo.composeapp.generated.resources.option_playlist_description
import ondo.composeapp.generated.resources.option_playlist_name
import ondo.composeapp.generated.resources.option_playlist_public
import ondo.composeapp.generated.resources.subtitle_playlist_public
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Edit
import com.nickshortnct.ondo.ui.components.common.CoverArt
import com.nickshortnct.ondo.ui.components.common.SegmentedListButton
import com.nickshortnct.ondo.ui.components.common.SegmentedListButtonDefaults
import com.nickshortnct.ondo.ui.components.dialogs.FormDialog
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.CoverChange
import com.nickshortnct.ondo.ui.screens.playlist.viewmodels.PlaylistEditDialogViewModel
import com.nickshortnct.ondo.ui.util.rememberPlaylistCoverPicker

/** Rename a playlist, change its description and visibility, and set or remove its cover. */
@Composable
fun PlaylistEditDialog(
	playlist: DomainPlaylist,
	onDismissRequest: () -> Unit,
	onSaved: () -> Unit = {}
) {
	val viewModel = koinViewModel<PlaylistEditDialogViewModel>(
		key = "edit-" + playlist.id,
		parameters = { parametersOf(playlist) }
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val saveState by viewModel.saveState.collectAsStateWithLifecycle()
	val isPublic by viewModel.isPublic.collectAsStateWithLifecycle()
	val coverChange by viewModel.coverChange.collectAsStateWithLifecycle()
	val saving = saveState is UiState.Loading

	val pickCover = rememberPlaylistCoverPicker { bytes -> bytes?.let(viewModel::replaceCover) }

	LaunchedEffect(viewModel) {
		viewModel.saved.collect {
			onSaved()
			onDismissRequest()
		}
	}

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Edit, null) },
		title = { Text(stringResource(Res.string.action_edit_playlist)) },
		buttons = {
			SegmentedListButton(
				modifier = Modifier.fillMaxWidth(),
				onClick = viewModel::save,
				enabled = !saving && viewModel.name.text.isNotBlank(),
				shapes = SegmentedListButtonDefaults.shapes(index = 0, count = 2),
				colors = SegmentedListButtonDefaults.primaryColors()
			) {
				if (saving) CircularProgressIndicator(Modifier.size(20.dp))
				Text(stringResource(Res.string.action_save))
			}
			SegmentedListButton(
				modifier = Modifier.fillMaxWidth(),
				onClick = onDismissRequest,
				enabled = !saving,
				shapes = SegmentedListButtonDefaults.shapes(index = 1, count = 2)
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			(saveState as? UiState.Error)?.error?.let {
				SelectionContainer { Text(it.message ?: "$it", color = MaterialTheme.colorScheme.error) }
			}

			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(16.dp)
			) {
				val shape = preferenceManager.coverArtShape.decreasedShape
				Box(Modifier.size(96.dp).clip(shape)) {
					when (val change = coverChange) {
						is CoverChange.Replace -> AsyncImage(
							model = change.jpeg,
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier.size(96.dp)
						)
						CoverChange.Remove -> CoverArt(coverArtId = null, modifier = Modifier.size(96.dp), shape = shape)
						CoverChange.None -> CoverArt(
							coverArtId = playlist.coverArtId,
							localImageUri = playlist.customCoverPath,
							modifier = Modifier.size(96.dp),
							shape = shape
						)
					}
				}
				Column {
					TextButton(onClick = pickCover, enabled = !saving) {
						Text(stringResource(Res.string.action_choose_cover))
					}
					TextButton(onClick = viewModel::removeCover, enabled = !saving && coverChange != CoverChange.Remove) {
						Text(stringResource(Res.string.action_remove_cover))
					}
				}
			}
			Spacer(Modifier.height(4.dp))
			if (coverChange == CoverChange.Remove) {
				Text(
					stringResource(Res.string.info_cover_will_be_removed),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			TextField(
				state = viewModel.name,
				label = { Text(stringResource(Res.string.option_playlist_name)) },
				lineLimits = TextFieldLineLimits.SingleLine,
				modifier = Modifier.fillMaxWidth()
			)
			TextField(
				state = viewModel.description,
				label = { Text(stringResource(Res.string.option_playlist_description)) },
				lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 5),
				modifier = Modifier.fillMaxWidth()
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(Res.string.option_playlist_public), style = MaterialTheme.typography.bodyLarge)
					Text(
						stringResource(Res.string.subtitle_playlist_public),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Switch(checked = isPublic, onCheckedChange = viewModel::setPublic, enabled = !saving)
			}
		}
	)
}
