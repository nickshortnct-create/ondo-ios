package com.nickshortnct.ondo.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.SingletonImageLoader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_ok
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.info_confirm_clear_actions
import ondo.composeapp.generated.resources.info_confirm_clear_downloads
import ondo.composeapp.generated.resources.info_confirm_rebuild
import ondo.composeapp.generated.resources.action_cancel_download
import ondo.composeapp.generated.resources.action_clear_downloads
import ondo.composeapp.generated.resources.action_clear_image_cache
import ondo.composeapp.generated.resources.action_clear_pending_actions
import ondo.composeapp.generated.resources.action_rebuild_database
import ondo.composeapp.generated.resources.action_trigger_sync
import ondo.composeapp.generated.resources.count_days_ago
import ondo.composeapp.generated.resources.count_hours_ago
import ondo.composeapp.generated.resources.count_minutes_ago
import ondo.composeapp.generated.resources.count_songs
import ondo.composeapp.generated.resources.info_library_download
import ondo.composeapp.generated.resources.info_library_download_warning
import ondo.composeapp.generated.resources.info_not_available_offline
import ondo.composeapp.generated.resources.info_progress
import ondo.composeapp.generated.resources.info_status_calculating
import ondo.composeapp.generated.resources.info_status_downloading
import ondo.composeapp.generated.resources.info_sync_just_now
import ondo.composeapp.generated.resources.info_sync_never
import ondo.composeapp.generated.resources.option_cover_art_quality
import ondo.composeapp.generated.resources.option_downloaded_songs
import ondo.composeapp.generated.resources.option_image_cache_size
import ondo.composeapp.generated.resources.option_last_sync
import ondo.composeapp.generated.resources.option_live_status
import ondo.composeapp.generated.resources.option_offline_mode
import ondo.composeapp.generated.resources.option_pending_actions
import ondo.composeapp.generated.resources.subtitle_download_quality
import ondo.composeapp.generated.resources.subtitle_offline_mode
import ondo.composeapp.generated.resources.subtitle_pending_actions
import ondo.composeapp.generated.resources.subtitle_rebuild_database
import ondo.composeapp.generated.resources.subtitle_trigger_sync
import ondo.composeapp.generated.resources.title_cache_management
import ondo.composeapp.generated.resources.title_danger_zone
import ondo.composeapp.generated.resources.title_data_storage
import ondo.composeapp.generated.resources.title_download_quality
import ondo.composeapp.generated.resources.title_library_download
import ondo.composeapp.generated.resources.title_network
import ondo.composeapp.generated.resources.title_sync_control
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.CoverArtQuality
import com.nickshortnct.ondo.domain.models.settings.OfflineMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Offline
import com.nickshortnct.ondo.ui.components.common.SegmentedListItem
import com.nickshortnct.ondo.ui.components.common.SegmentedListItemDefaults
import com.nickshortnct.ondo.ui.components.dialogs.BulkDownloadDialog
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsChoiceItem
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroup
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsGroupDefaults
import com.nickshortnct.ondo.ui.screens.settings.components.SettingsNavItem
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsDataStorageScreen() {
	val viewModel = koinViewModel<SettingsDataStorageViewModel>()

	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val scope = rememberCoroutineScope()
	val coilPlatformContext = LocalCoilPlatformContext.current
	val imageLoader = SingletonImageLoader.get(coilPlatformContext)

	val syncState by viewModel.syncState.collectAsStateWithLifecycle()
	val pendingActionCount by viewModel.pendingActionCount.collectAsStateWithLifecycle()
	val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle(0)
	val downloadSize by viewModel.downloadSize.collectAsStateWithLifecycle(0L)

	var pendingStorageAction by remember { mutableStateOf<StorageAction?>(null) }

	var showLibraryDownloadDialog by remember { mutableStateOf(false) }
	val isDownloadingLibrary by viewModel.isDownloadingLibrary.collectAsStateWithLifecycle()
	val libraryDownloadProgress by viewModel.libraryDownloadProgress.collectAsStateWithLifecycle()

	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

	val calculating = stringResource(Res.string.info_status_calculating)
	var imageCacheSizeMb by remember { mutableStateOf(calculating) }

	val downloadsSizeMb = remember(downloadSize) {
		val mb = downloadSize.toDouble() / (1024 * 1024)
		if (mb > 1024) {
			val gb = mb / 1024
			" // ${(gb * 100).toInt() / 100.0} GB"
		} else {
			" // ${mb.toInt()} MB"
		}
	}

	val smoothSyncProgress by animateFloatAsState(
		if (syncState.isSyncing) syncState.progress else 0f,
		animationSpec = tween(
			durationMillis = 250,
			easing = EaseOut
		)
	)

	val smoothLibraryDownloadProgress by animateFloatAsState(
		targetValue = libraryDownloadProgress.coerceIn(0f, 1f),
		animationSpec = tween(durationMillis = 500, easing = EaseOut)
	)

	val offlineIcon = @Composable {
		if (!isOnline) {
			Icon(
				Icons.Outlined.Offline,
				stringResource(Res.string.info_not_available_offline),
				modifier = Modifier.size(20.dp)
			)
		}
	}

	@Composable
	fun timeSinceLastSync(): String {
		val time = preferenceManager.lastFullSyncTime

		if (time == 0L) return stringResource(Res.string.info_sync_never)

		val duration = Clock.System.now() - Instant.fromEpochMilliseconds(time)
		val minutes = duration.inWholeMinutes.toInt()
		val hours = duration.inWholeHours.toInt()
		val days = duration.inWholeDays.toInt()

		return when {
			minutes < 1 -> stringResource(Res.string.info_sync_just_now)
			hours < 1 -> pluralStringResource(Res.plurals.count_minutes_ago, minutes, minutes)
			days < 1 -> pluralStringResource(Res.plurals.count_hours_ago, hours, hours)
			else -> pluralStringResource(Res.plurals.count_days_ago, days, days)
		}
	}

	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			val sizeBytes = imageLoader.diskCache?.size ?: 0L
			imageCacheSizeMb = "${sizeBytes / (1024 * 1024)} MB"
		}
	}

	BulkDownloadDialog(
		title = stringResource(Res.string.title_library_download),
		message = stringResource(Res.string.info_library_download_warning),
		showDialog = showLibraryDownloadDialog,
		onDismissRequest = { showLibraryDownloadDialog = false },
		onConfirm = {
			showLibraryDownloadDialog = false
			viewModel.downloadEntireLibrary()
		}
	)

	pendingStorageAction?.let { action ->
		val title = when (action) {
			StorageAction.Actions -> Res.string.action_clear_pending_actions
			StorageAction.Downloads -> Res.string.action_clear_downloads
			StorageAction.Rebuild -> Res.string.action_rebuild_database
		}
		val message = when (action) {
			StorageAction.Actions -> stringResource(Res.string.info_confirm_clear_actions)
			StorageAction.Downloads -> stringResource(Res.string.info_confirm_clear_downloads, downloadCount, downloadsSizeMb)
			StorageAction.Rebuild -> stringResource(Res.string.info_confirm_rebuild)
		}
		AlertDialog(
			onDismissRequest = { pendingStorageAction = null },
			title = { Text(stringResource(title)) }, text = { Text(message) },
			confirmButton = { TextButton(onClick = {
				pendingStorageAction = null
				when (action) {
					StorageAction.Actions -> viewModel.removeAllActions()
					StorageAction.Downloads -> viewModel.clearAllDownloads()
					StorageAction.Rebuild -> viewModel.rebuildDatabase()
				}
			}) { Text(stringResource(Res.string.action_ok)) } },
			dismissButton = { TextButton(onClick = { pendingStorageAction = null }) { Text(stringResource(Res.string.action_cancel)) } }
		)
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_data_storage)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup(title = { Text(stringResource(Res.string.title_network)) }) {
					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.DownloadQuality) },
						content = { Text(stringResource(Res.string.title_download_quality)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_download_quality)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 3)
					)

					SettingsChoiceItem(
						choices = OfflineMode.entries.toImmutableList(),
						selectedChoice = preferenceManager.offlineMode,
						onChoiceSelected = { preferenceManager.offlineMode = it },
						description = stringResource(Res.string.subtitle_offline_mode),
						content = { Text(stringResource(Res.string.option_offline_mode)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 3)
					)

					SettingsChoiceItem(
						choices = CoverArtQuality.entries.toImmutableList(),
						selectedChoice = preferenceManager.coverArtQuality,
						onChoiceSelected = {
							preferenceManager.coverArtQuality = it
							imageLoader.memoryCache?.clear()
							scope.launch(Dispatchers.IO) {
								imageLoader.diskCache?.clear()
								imageCacheSizeMb = "0 MB"
							}
						},
						content = { Text(stringResource(Res.string.option_cover_art_quality)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 3)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_sync_control)) }) {
					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_live_status)) },
						supportingContent = {
							Column(Modifier.fillMaxWidth()) {
								Text(stringResource(syncState.message))
								AnimatedVisibility(
									syncState.isSyncing,
									enter = fadeIn() + expandVertically(clip = false),
									exit = fadeOut() + shrinkVertically(clip = false)
								) {
									LinearProgressIndicator(
										progress = {
											if (!syncState.isSyncing)
												1f
											else smoothSyncProgress.coerceIn(0f, 1f)
										},
										modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
									)
								}
							}
						},
						trailingContent = offlineIcon,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 3)
					)

					SegmentedListItem(
						onClick = viewModel::triggerManualSync,
						enabled = isOnline,
						content = { Text(stringResource(Res.string.action_trigger_sync)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_trigger_sync)) },
						trailingContent = offlineIcon,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 3)
					)

					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_last_sync)) },
						supportingContent = { Text(timeSinceLastSync()) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 3)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_cache_management)) }) {
					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_pending_actions)) },
						supportingContent = {
							Text(
								text = stringResource(
									Res.string.subtitle_pending_actions,
									pendingActionCount
								)
							)
						},
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4)
					)

					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_downloaded_songs)) },
						supportingContent = {
							Text(
								text = pluralStringResource(
									Res.plurals.count_songs,
									downloadCount,
									downloadCount
								) + downloadsSizeMb
							)
						},
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4)
					)

					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_image_cache_size)) },
						supportingContent = { Text(imageCacheSizeMb) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4)
					)

					SegmentedListItem(
						onClick = {
							if (!isDownloadingLibrary) {
								showLibraryDownloadDialog = true
							}
						},
						enabled = isOnline,
						content = { Text(stringResource(Res.string.title_library_download)) },
						supportingContent = {
							Column(Modifier.fillMaxWidth()) {
								Text(
									text = stringResource(
										if (isDownloadingLibrary)
											Res.string.info_status_downloading
										else Res.string.info_library_download
									)
								)
								AnimatedVisibility(
									visible = isDownloadingLibrary,
									enter = fadeIn() + expandVertically(clip = false),
									exit = fadeOut() + shrinkVertically(clip = false)
								) {
									Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.SpaceBetween,
											verticalAlignment = Alignment.CenterVertically
										) {
											Text(
												text = stringResource(Res.string.info_progress),
												style = MaterialTheme.typography.labelMedium,
												color = MaterialTheme.colorScheme.primary
											)

											Row(verticalAlignment = Alignment.CenterVertically) {
												TextButton(
													onClick = {
														viewModel.cancelLibraryDownload()
													},
													contentPadding = PaddingValues(
														horizontal = 8.dp,
														vertical = 0.dp
													),
													modifier = Modifier.padding(end = 8.dp)
												) {
													Text(
														stringResource(Res.string.action_cancel_download),
														style = MaterialTheme.typography.labelLarge,
														color = MaterialTheme.colorScheme.error
													)
												}

												Text(
													text = "${(smoothLibraryDownloadProgress * 100).toInt()}%",
													style = MaterialTheme.typography.labelMedium,
													color = MaterialTheme.colorScheme.primary
												)
											}
										}

										LinearProgressIndicator(
											progress = { smoothLibraryDownloadProgress },
											modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
										)
									}
								}
							}
						},
						trailingContent = offlineIcon,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_danger_zone)) }) {
					SegmentedListItem(
						onClick = {
							imageLoader.memoryCache?.clear()
							scope.launch(Dispatchers.IO) {
								imageLoader.diskCache?.clear()
								imageCacheSizeMb = "0 MB"
							}
						},
						content = { Text(stringResource(Res.string.action_clear_image_cache)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
					SegmentedListItem(
						onClick = { pendingStorageAction = StorageAction.Actions },
						content = { Text(stringResource(Res.string.action_clear_pending_actions)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
					SegmentedListItem(
						onClick = { pendingStorageAction = StorageAction.Downloads },
						content = { Text(stringResource(Res.string.action_clear_downloads)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
					SegmentedListItem(
						onClick = { pendingStorageAction = StorageAction.Rebuild },
						enabled = isOnline,
						content = { Text(stringResource(Res.string.action_rebuild_database)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_rebuild_database)) },
						trailingContent = offlineIcon,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
				}
			}
		}
	}
}

private enum class StorageAction { Actions, Downloads, Rebuild }
