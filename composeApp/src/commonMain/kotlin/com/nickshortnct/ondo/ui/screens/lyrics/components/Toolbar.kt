package com.nickshortnct.ondo.ui.screens.lyrics.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.collections.immutable.ImmutableList
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_navigate_back
import ondo.composeapp.generated.resources.action_share_lyrics
import ondo.composeapp.generated.resources.count_lines
import ondo.composeapp.generated.resources.title_select_lyrics
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.ArrowBack
import com.nickshortnct.ondo.icons.outlined.Check
import com.nickshortnct.ondo.icons.outlined.KeyboardArrowDown
import com.nickshortnct.ondo.icons.outlined.Share
import com.nickshortnct.ondo.ui.components.layouts.TopBarButton
import com.nickshortnct.ondo.ui.components.toolbars.SheetToolbar

@Composable
fun LyricsScreenToolbar(
	onDismissRequest: () -> Unit,
	onShare: () -> Unit,
	isSelecting: Boolean,
	toggleIsSelecting: () -> Unit,
	windowInsets: WindowInsets,
	selectedIndices: ImmutableList<Int>
) {
	SheetToolbar(
		windowInsets = windowInsets,
		navigationIcon = {
			TopBarButton(
				onClick = dropUnlessResumed {
					if (!isSelecting) {
						onDismissRequest()
					} else {
						toggleIsSelecting()
					}
				},
				content = {
					Icon(
						imageVector = if (!isSelecting)
							Icons.Outlined.KeyboardArrowDown
						else Icons.Outlined.ArrowBack,
						contentDescription = stringResource(Res.string.action_navigate_back)
					)
				}
			)
			NavigationBackHandler(
				state = rememberNavigationEventState(NavigationEventInfo.None),
				isBackEnabled = isSelecting,
				onBackCompleted = toggleIsSelecting
			)
		},
		actions = {
			TopBarButton(
				enabled = !isSelecting || selectedIndices.isNotEmpty(),
				onClick = {
					if (isSelecting) {
						onShare()
					} else {
						toggleIsSelecting()
					}
				}
			) {
				Icon(
					imageVector = if (!isSelecting)
						Icons.Outlined.Share
					else Icons.Outlined.Check,
					contentDescription = stringResource(Res.string.action_share_lyrics),
					modifier = Modifier.size(26.dp)
				)
			}
		},
		title = {
			if (isSelecting) {
				Column {
					Text(
						stringResource(Res.string.title_select_lyrics),
						fontWeight = FontWeight.SemiBold
					)
					Text(
						pluralStringResource(
							Res.plurals.count_lines,
							selectedIndices.count(),
							selectedIndices.count()
						)
					)
				}
			}
		}
	)
}
