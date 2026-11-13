package com.nickshortnct.ondo.ui.components.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.action_download
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Download
import com.nickshortnct.ondo.ui.components.common.SegmentedListButton
import com.nickshortnct.ondo.ui.components.common.SegmentedListButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkDownloadDialog(
	title: String,
	message: String,
	showDialog: Boolean,
	onDismissRequest: () -> Unit,
	onConfirm: () -> Unit
) {
	if (showDialog) {
		FormDialog(
			onDismissRequest = onDismissRequest,
			icon = { Icon(Icons.Outlined.Download, null) },
			title = { Text(title) },
			content = { Text(message) },
			buttons = {
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						onConfirm()
						onDismissRequest()
					},
					shapes = SegmentedListButtonDefaults.shapes(index = 0, count = 2),
					colors = SegmentedListButtonDefaults.primaryColors()
				) {
					Text(stringResource(Res.string.action_download))
				}
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = onDismissRequest,
					shapes = SegmentedListButtonDefaults.shapes(index = 1, count = 2)
				) {
					Text(stringResource(Res.string.action_cancel))
				}
			}
		)
	}
}
