package com.nickshortnct.ondo.ui.components.layouts

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_account
import ondo.composeapp.generated.resources.title_search
import ondo.composeapp.generated.resources.title_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.domain.models.settings.NavbarConfig
import com.nickshortnct.ondo.domain.models.settings.NavbarTab
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.filled.Settings
import com.nickshortnct.ondo.icons.outlined.AccountCircle
import com.nickshortnct.ondo.icons.outlined.Search
import com.nickshortnct.ondo.ui.components.common.TooltipBox
import com.nickshortnct.ondo.ui.components.sheets.AccountSheet
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.NavtabsViewModel

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun RootTopBar(
	title: @Composable () -> Unit,
	scrollBehavior: TopAppBarScrollBehavior,
	actions: @Composable RowScope.() -> Unit = {},
) {
	val navViewModel = koinViewModel<NavtabsViewModel>()
	val navState by navViewModel.state.collectAsState()
	val navConfig = (navState as? UiState.Success)?.data

	MediumFlexibleTopAppBar(
		title = {
			CompositionLocalProvider(
				LocalTextStyle provides when (LocalTextStyle.current) {
					MaterialTheme.typography.headlineMedium -> MaterialTheme.typography.headlineSmall
					else -> MaterialTheme.typography.titleLarge
				}
			) {
				title()
			}
		},
		actions = {
			actions()
			Actions(navConfig = navConfig)
		},
		scrollBehavior = scrollBehavior,
		colors = TopAppBarDefaults.topAppBarColors(
			scrolledContainerColor = MaterialTheme.colorScheme.surface
		),
	)
}

@Composable
private fun Actions(
	navConfig: NavbarConfig?,
) {
	val backStack = LocalNavStack.current

	val isSearchEnabled = navConfig?.tabs?.any {
		it.id == NavbarTab.Id.SEARCH && it.visible
	} == true

	var accountSheetOpen by rememberSaveable { mutableStateOf(false) }

	if (!isSearchEnabled) {
		TooltipBox(stringResource(Res.string.title_search)) {
			IconButton(
				onClick = dropUnlessResumed {
					backStack.add(Screen.Search(nested = true))
				}
			) {
				Icon(
					imageVector = Icons.Outlined.Search,
					contentDescription = stringResource(Res.string.title_search)
				)
			}
		}
	}

	TooltipBox(stringResource(Res.string.title_settings)) {
		IconButton(onClick = dropUnlessResumed {
			backStack.add(Screen.Settings.Root)
		}) {
			Icon(
				imageVector = Icons.Filled.Settings,
				contentDescription = stringResource(Res.string.title_settings)
			)
		}
	}

	TooltipBox(stringResource(Res.string.title_account)) {
		IconButton(onClick = {
			accountSheetOpen = true
		}) {
			Icon(
				imageVector = Icons.Outlined.AccountCircle,
				contentDescription = stringResource(Res.string.title_account)
			)
		}
	}

	if (accountSheetOpen) {
		AccountSheet(onDismissRequest = { accountSheetOpen = false })
	}
}
