package com.nickshortnct.ondo.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousCapsule
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.notice_logout_cleanup
import ondo.composeapp.generated.resources.action_delete_offline_files
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_log_out
import ondo.composeapp.generated.resources.action_sleep_timer
import ondo.composeapp.generated.resources.action_sleep_timer_enabled
import ondo.composeapp.generated.resources.action_sleep_timer_queue_enabled
import ondo.composeapp.generated.resources.action_sleep_timer_songs_enabled
import ondo.composeapp.generated.resources.action_view_shares
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.manager.LoginManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SleepTimerManager
import com.nickshortnct.ondo.domain.manager.SleepTimerMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Bedtime
import com.nickshortnct.ondo.icons.outlined.Logout
import com.nickshortnct.ondo.icons.outlined.Share
import com.nickshortnct.ondo.ui.components.common.Monogram
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem
import com.nickshortnct.ondo.ui.components.common.SegmentedListItemDefaults
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.theme.positive
import com.nickshortnct.ondo.ui.util.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSheet(
	onDismissRequest: () -> Unit
) {
	val backStack = LocalNavStack.current
	val loginManager = koinInject<LoginManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val settings = koinInject<Settings>()

	var showLogout by remember { mutableStateOf(false) }
	var deleteOfflineFiles by remember { mutableStateOf(false) }
	var loggingOut by remember { mutableStateOf(false) }
	var sleepTimerSheetOpen by rememberSaveable { mutableStateOf(false) }
	val sleepTimerManager = koinInject<SleepTimerManager>()
	val sleepTimerMode by sleepTimerManager.mode.collectAsStateWithLifecycle()

	val sheetState = rememberBottomSheetState(
		initialValue = SheetValue.Hidden,
		enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
	)

	val scope = rememberCoroutineScope()
	val animateToDismiss = {
		scope
			.launch { sheetState.hide() }
			.invokeOnCompletion {
				if (!sheetState.isVisible) {
					onDismissRequest()
				}
			}
	}

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = sheetState,
		contentWindowInsets = {
			BottomSheetDefaults.modalWindowInsets.add(
				WindowInsets(
					left = 12.dp,
					right = 12.dp
				)
			)
		},
		dragHandle = {
			Surface(
				modifier = Modifier.padding(vertical = 6.dp),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				shape = ContinuousCapsule,
			) {
				Box(Modifier.size(width = 32.dp, height = 4.dp))
			}
		}
	) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.SegmentedGap)
		) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceContainer,
				shape = MaterialTheme.shapes.large
			) {
				Row(
					modifier = Modifier.padding(12.dp).fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Monogram(text = settings.getString("username", ""))

					Column(
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = settings.getString("username", ""),
							style = MaterialTheme.typography.headlineSmall
						)

						Text(
							text = settings.getString("instanceUrl", ""),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			Spacer(Modifier.height(9.dp))

			val enableSharing = preferenceManager.enableSharing
			val count = if (enableSharing) 3 else 2

			if (enableSharing) {
				SegmentedListItem(
					shapes = SegmentedListItemDefaults.segmentedShapes(
						index = 0,
						count = count
					),
					onClick = {
						animateToDismiss()
						backStack.add(Screen.ShareList)
					},
					leadingContent = { Icon(Icons.Outlined.Share, null) },
					content = { Text(stringResource(Res.string.action_view_shares)) }
				)
			}

			SegmentedListItem(
				shapes = SegmentedListItemDefaults.segmentedShapes(
					index = count - 2,
					count = count
				),
				onClick = { sleepTimerSheetOpen = true },
				leadingContent = {
					val tint = when (sleepTimerMode) {
						!is SleepTimerMode.Disabled -> MaterialTheme.colorScheme.positive
						else -> LocalContentColor.current
					}
					Icon(
						imageVector = Icons.Outlined.Bedtime,
						contentDescription = null,
						tint = tint
					)
				},
				content = {
					val label = when (val mode = sleepTimerMode) {
						is SleepTimerMode.Time -> stringResource(
							Res.string.action_sleep_timer_enabled,
							sleepTimerManager.timeLeft?.label() ?: ""
						)

						is SleepTimerMode.Songs -> stringResource(
							Res.string.action_sleep_timer_songs_enabled,
							mode.remaining
						)

						is SleepTimerMode.EndOfQueue -> stringResource(
							Res.string.action_sleep_timer_queue_enabled
						)

						else -> stringResource(Res.string.action_sleep_timer)
					}
					val color = when (sleepTimerMode) {
						!is SleepTimerMode.Disabled -> MaterialTheme.colorScheme.positive
						else -> LocalContentColor.current
					}
					Text(
						text = label,
						color = color
					)
				}
			)

			SegmentedListItem(
				shapes = SegmentedListItemDefaults.segmentedShapes(
					index = count - 1,
					count = count
				),
				onClick = {
					showLogout = true
				},
				leadingContent = { Icon(Icons.Outlined.Logout, null) },
				content = { Text(stringResource(Res.string.action_log_out)) }
			)
		}
	}

	if (showLogout) {
		AlertDialog(
			onDismissRequest = { if (!loggingOut) showLogout = false },
			title = { Text(stringResource(Res.string.action_log_out)) },
			text = { Column {
				Text(stringResource(Res.string.notice_logout_cleanup))
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(deleteOfflineFiles, { deleteOfflineFiles = it }, enabled = !loggingOut)
					Text(stringResource(Res.string.action_delete_offline_files))
				}
			} },
			confirmButton = { TextButton(enabled = !loggingOut, onClick = {
				loggingOut = true
				scope.launch {
					loginManager.logout(deleteOfflineFiles).join()
					loggingOut = false
					if (loginManager.loginState.value == com.nickshortnct.ondo.ui.core.LoginUiState.Idle) {
						showLogout = false
						backStack.clear(); backStack.add(Screen.Login)
						animateToDismiss()
					}
				}
			}) { Text(stringResource(Res.string.action_log_out)) } },
			dismissButton = { TextButton(enabled = !loggingOut, onClick = { showLogout = false }) { Text(stringResource(Res.string.action_cancel)) } }
		)
	}

	if (sleepTimerSheetOpen) {
		SleepTimerSheet(onDismissRequest = { sleepTimerSheetOpen = false })
	}
}
