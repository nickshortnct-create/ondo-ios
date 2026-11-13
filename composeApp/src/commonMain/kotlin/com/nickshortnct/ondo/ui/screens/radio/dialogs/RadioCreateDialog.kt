package com.nickshortnct.ondo.ui.screens.radio.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.action_ok
import ondo.composeapp.generated.resources.option_radio_homepage_url
import ondo.composeapp.generated.resources.option_radio_name
import ondo.composeapp.generated.resources.option_radio_stream_url
import ondo.composeapp.generated.resources.title_create_radio
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Radio
import com.nickshortnct.ondo.ui.components.common.SegmentedListButton
import com.nickshortnct.ondo.ui.components.common.SegmentedListButtonDefaults
import com.nickshortnct.ondo.ui.components.dialogs.FormDialog
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.screens.radio.viewmodels.RadioCreateDialogViewModel

@Composable
fun RadioCreateDialog(
	onDismissRequest: () -> Unit,
	onRefresh: () -> Unit
) {
	val viewModel = koinViewModel<RadioCreateDialogViewModel>()
	val state by viewModel.creationState.collectAsState()

	LaunchedEffect(Unit) {
		viewModel.events.collect { event ->
			when (event) {
				is RadioCreateDialogViewModel.Event.Dismiss -> {
					onDismissRequest()
					onRefresh()
				}
			}
		}
	}

	FormDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Radio, null) },
		title = { Text(stringResource(Res.string.title_create_radio)) },
		buttons = {
			SegmentedListButton(
				modifier = Modifier.fillMaxWidth(),
				onClick = viewModel::create,
				enabled = state !is UiState.Loading
					&& viewModel.name.text.isNotBlank()
					&& viewModel.streamUrl.text.isNotBlank(),
				shapes = SegmentedListButtonDefaults.shapes(index = 0, count = 2),
				colors = SegmentedListButtonDefaults.primaryColors()
			) {
				if (state is UiState.Loading) {
					CircularProgressIndicator(Modifier.size(20.dp))
				}
				Text(stringResource(Res.string.action_ok))
			}
			SegmentedListButton(
				modifier = Modifier.fillMaxWidth(),
				onClick = onDismissRequest,
				enabled = state !is UiState.Loading,
				shapes = SegmentedListButtonDefaults.shapes(index = 1, count = 2)
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			(state as? UiState.Error)?.error?.let {
				SelectionContainer {
					Text("$it")
				}
			}
			val fieldModifier = Modifier.height(60.dp)
			val fieldLineLimits = TextFieldLineLimits.SingleLine
			Column(
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				TextField(
					modifier = fieldModifier,
					state = viewModel.name,
					label = { Text("${stringResource(Res.string.option_radio_name)}*") },
					lineLimits = fieldLineLimits
				)
				TextField(
					modifier = fieldModifier,
					state = viewModel.streamUrl,
					label = { Text("${stringResource(Res.string.option_radio_stream_url)}*") },
					lineLimits = fieldLineLimits
				)
				TextField(
					modifier = fieldModifier,
					state = viewModel.homepageUrl,
					label = { Text(stringResource(Res.string.option_radio_homepage_url)) },
					lineLimits = fieldLineLimits
				)
			}
		}
	)
}
