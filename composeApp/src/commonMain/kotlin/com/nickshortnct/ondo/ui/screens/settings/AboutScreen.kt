package com.nickshortnct.ondo.ui.screens.settings

import ondo.composeapp.generated.resources.action_checking
import ondo.composeapp.generated.resources.notice_update_checking
import ondo.composeapp.generated.resources.notice_update_current
import ondo.composeapp.generated.resources.notice_update_available_explicit
import ondo.composeapp.generated.resources.notice_update_check_failed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_app_version
import ondo.composeapp.generated.resources.option_check_for_updates
import ondo.composeapp.generated.resources.title_about
import ondo.composeapp.generated.resources.title_codeberg
import ondo.composeapp.generated.resources.title_discord_server
import ondo.composeapp.generated.resources.title_github
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.PlatformType
import com.nickshortnct.ondo.ui.components.sheets.ChangelogSheet
import com.nickshortnct.ondo.ui.components.sheets.ChangelogViewModel
import com.nickshortnct.ondo.ui.components.sheets.UpdateCheckState
import com.nickshortnct.ondo.generated.BuildInfo
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem
import com.nickshortnct.ondo.ui.components.common.SegmentedListItemDefaults
import com.nickshortnct.ondo.ui.components.dialogs.LinkConfirmationDialog
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroup
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroupDefaults
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsNavItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsAboutScreen() {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_about)) },
				hideBack = hideBack
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
		) {
			SettingsGroup {
				val text = buildString {
					append(platformContext.name + "\n")
					append(
						stringResource(
							Res.string.info_app_version,
							platformContext.appVersion
						)
					)
				}
				SegmentedListItem(
					onClick = { clipboard.setText(AnnotatedString(text)) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
					content = { Text(text) }
				)
			}

			SettingsGroup {
				SettingsNavItem(
					onClick = { linkToOpen = "https://github.com/nickshortnct-create/ondo" },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
					content = { Text(stringResource(Res.string.title_github)) }
				)
			}

			if (platformContext.platformType == PlatformType.Android && !BuildInfo.FDROID) {
				val updater = koinViewModel<ChangelogViewModel>()
				val checkState by updater.checkState.collectAsStateWithLifecycle()
				val checkError by updater.checkError.collectAsStateWithLifecycle()
				SettingsGroup {
					Button(
						onClick = { updater.checkForUpdates(platformContext.appVersion) },
						enabled = checkState != UpdateCheckState.CHECKING,
						modifier = Modifier.fillMaxWidth()
					) {
						Text(if (checkState == UpdateCheckState.CHECKING) stringResource(Res.string.action_checking) else stringResource(Res.string.option_check_for_updates))
					}
					when (checkState) {
						UpdateCheckState.CHECKING -> Text(stringResource(Res.string.notice_update_checking))
						UpdateCheckState.UP_TO_DATE -> Text(stringResource(Res.string.notice_update_current))
						UpdateCheckState.UPDATE_AVAILABLE -> Text(stringResource(Res.string.notice_update_available_explicit))
						UpdateCheckState.FAILED -> Text(checkError ?: stringResource(Res.string.notice_update_check_failed))
						UpdateCheckState.IDLE -> Unit
					}
				}
				ChangelogSheet(updater)
			}
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
