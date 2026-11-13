package com.nickshortnct.ondo.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_genres
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.isLandscape
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.ui.components.layouts.ArtGrid
import com.nickshortnct.ondo.ui.components.layouts.NestedTopBar
import com.nickshortnct.ondo.ui.components.layouts.PullToRefreshBox
import com.nickshortnct.ondo.ui.components.layouts.RootBottomBar
import com.nickshortnct.ondo.ui.components.layouts.RootTopBar
import com.nickshortnct.ondo.ui.components.snackbars.ErrorSnackBar
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.PersistentViewModelStoreOwner
import com.nickshortnct.ondo.ui.screens.genre.components.genreListScreenContent
import com.nickshortnct.ondo.ui.screens.genre.viewmodels.GenreListViewModel
import com.nickshortnct.ondo.ui.util.withoutTop
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreListScreen(
	nested: Boolean
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<GenreListViewModel>(
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val genresState by viewModel.genresState.collectAsState()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val rootViewModel = koinViewModel<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				viewModel.gridState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					{ Text(stringResource(Res.string.title_genres)) },
					scrollBehavior
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_genres)) })
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = genresState !is UiState.Loading,
			onRefresh = { viewModel.refreshGenres(true) },
			key = genresState
		) {
			ArtGrid(
				modifier = if (!nested)
					Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
				else Modifier,
				contentPadding = innerPadding.withoutTop(),
				state = viewModel.gridState,
				verticalArrangement = if ((genresState as? UiState.Success)?.data?.isEmpty() == true)
					Arrangement.Center
				else Arrangement.spacedBy(12.dp),
				columns = GridCells.Fixed(2)
			) {
				genreListScreenContent(state = genresState)
			}
		}
	}

	ErrorSnackBar(
		error = (genresState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}
