package com.nickshortnct.ondo.ui.screens.share

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.info_error
import ondo.composeapp.generated.resources.info_no_shares
import ondo.composeapp.generated.resources.title_shares
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalSnackBarState
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.ShareManager
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.filled.ShareOff
import com.nickshortnct.ondo.ui.components.common.ContentUnavailable
import com.nickshortnct.ondo.ui.components.dialogs.DeletionDialog
import com.nickshortnct.ondo.ui.components.dialogs.DeletionEndpoint
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.artGridError
import com.nickshortnct.ondo.ui.components.sheets.ShareSheet
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.screens.share.components.ShareListScreenItem
import com.nickshortnct.ondo.ui.screens.share.viewmodels.ShareListViewModel
import com.nickshortnct.ondo.ui.util.withoutTop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareListScreen() {
	val viewModel = koinViewModel<ShareListViewModel>()

	val sharesState by viewModel.sharesState.collectAsStateWithLifecycle()
	val selectedShare by viewModel.selectedShare.collectAsStateWithLifecycle()
	val isRefreshingFlow by viewModel.isRefreshing.collectAsStateWithLifecycle()

	val shareManager = koinInject<ShareManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val snackBarState = LocalSnackBarState.current
	val scope = rememberCoroutineScope()

	var deletionId by remember { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.title_shares)) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = contentPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = sharesState !is UiState.Loading && !isRefreshingFlow,
			onRefresh = { viewModel.refreshShares() },
			key = listOf(sharesState, isRefreshingFlow)
		) {
			Crossfade(sharesState) { stateValue ->
				LazyVerticalGrid(
					modifier = Modifier.fillMaxSize(),
					columns = GridCells.Fixed(1),
					contentPadding = contentPadding.withoutTop(),
					state = viewModel.gridState,
					verticalArrangement = if ((stateValue as? UiState.Success)?.data?.isEmpty() == true)
						Arrangement.Center
					else Arrangement.Top
				) {
					when (stateValue) {
						is UiState.Loading -> {
							return@LazyVerticalGrid
						}

						is UiState.Error -> artGridError(stateValue)
						is UiState.Success -> {
							items(stateValue.data, { it.id }) { share ->
								ShareListScreenItem(
									modifier = Modifier.animateItem(fadeInSpec = null),
									share = share,
									onClick = { viewModel.updateSelection(share) },
									onSwipeToDelete = { deletionId = share.id }
								)
							}
							if (stateValue.data.isEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ContentUnavailable(
										icon = Icons.Filled.ShareOff,
										label = stringResource(Res.string.info_no_shares)
									)
								}
							}
						}
					}
				}
			}
		}
	}

	selectedShare?.let { selectedShare ->
		ShareSheet(
			onDismissRequest = viewModel::clearSelection,
			onShare = {
				scope.launch {
					try {
						shareManager.shareString(selectedShare.url)
					} catch (ex: Exception) {
						val message = ex.message ?: getString(Res.string.info_error)
						snackBarState.showSnackbar(message)
					}
				}
			},
			onDelete = { deletionId = selectedShare.id }
		)
	}

	DeletionDialog(
		endpoint = DeletionEndpoint.SHARE,
		id = deletionId,
		onIdClear = { deletionId = null },
		onRefresh = viewModel::refreshShares
	)
}
