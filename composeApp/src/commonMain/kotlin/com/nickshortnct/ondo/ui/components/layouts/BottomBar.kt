package com.nickshortnct.ondo.ui.components.layouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.title_albums
import ondo.composeapp.generated.resources.title_artists
import ondo.composeapp.generated.resources.title_genres
import ondo.composeapp.generated.resources.title_library
import ondo.composeapp.generated.resources.title_playlists
import ondo.composeapp.generated.resources.title_radios
import ondo.composeapp.generated.resources.title_search
import ondo.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.models.settings.NavbarConfig
import com.nickshortnct.ondo.domain.models.settings.NavbarTab
import com.nickshortnct.ondo.domain.models.settings.NavigationBarLabelVisibility
import com.nickshortnct.ondo.domain.models.settings.NavigationBarStyle
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.filled.Album
import com.nickshortnct.ondo.icons.filled.Artist
import com.nickshortnct.ondo.icons.filled.Genre
import com.nickshortnct.ondo.icons.filled.LibraryMusic
import com.nickshortnct.ondo.icons.filled.Radio
import com.nickshortnct.ondo.icons.outlined.Album
import com.nickshortnct.ondo.icons.outlined.Artist
import com.nickshortnct.ondo.icons.outlined.Genre
import com.nickshortnct.ondo.icons.outlined.LibraryMusic
import com.nickshortnct.ondo.icons.outlined.Note
import com.nickshortnct.ondo.icons.outlined.PlaylistPlay
import com.nickshortnct.ondo.icons.outlined.Radio
import com.nickshortnct.ondo.icons.outlined.Search
import com.nickshortnct.ondo.ui.core.UiState
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.settings.viewmodels.NavtabsViewModel
import com.nickshortnct.ondo.ui.util.animatedTabIconPainter
import com.nickshortnct.ondo.ui.viewmodel.RootViewModel

private enum class NavItem(
	val destination: Screen,
	val icon: ImageVector,
	val iconUnselected: ImageVector = icon,
	val label: StringResource
) {
	LIBRARY(
		destination = Screen.Library(),
		icon = Icons.Filled.LibraryMusic,
		iconUnselected = Icons.Outlined.LibraryMusic,
		label = Res.string.title_library
	),
	ALBUMS(
		destination = Screen.AlbumList(),
		icon = Icons.Filled.Album,
		iconUnselected = Icons.Outlined.Album,
		label = Res.string.title_albums
	),
	PLAYLISTS(
		destination = Screen.PlaylistList(),
		icon = Icons.Outlined.PlaylistPlay,
		label = Res.string.title_playlists
	),
	ARTISTS(
		destination = Screen.ArtistList(),
		icon = Icons.Filled.Artist,
		iconUnselected = Icons.Outlined.Artist,
		label = Res.string.title_artists
	),
	SEARCH(
		destination = Screen.Search(),
		icon = Icons.Outlined.Search,
		iconUnselected = Icons.Outlined.Search,
		label = Res.string.title_search
	),
	GENRES(
		destination = Screen.GenreList(),
		icon = Icons.Filled.Genre,
		iconUnselected = Icons.Outlined.Genre,
		label = Res.string.title_genres
	),
	SONGS(
		destination = Screen.SongList(),
		icon = Icons.Outlined.Note,
		iconUnselected = Icons.Outlined.Note,
		label = Res.string.title_songs
	),
	RADIOS(
		destination = Screen.RadioList(),
		icon = Icons.Filled.Radio,
		iconUnselected = Icons.Outlined.Radio,
		label = Res.string.title_radios
	)
}

@Composable
fun BottomBar(
	modifier: Modifier = Modifier,
	containerColor: Color = NavigationBarDefaults.containerColor,
	windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
	enabled: Boolean = true
) {
	val viewModel = koinViewModel<NavtabsViewModel>()
	val rootViewModel = koinViewModel<RootViewModel>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val state by viewModel.state.collectAsState()
	val containerColor by animateColorAsState(containerColor)
	val tabs = ((state as? UiState.Success)?.data ?: NavbarConfig.default)
		.tabs.filter { tab -> tab.visible }
	val preferenceManager = koinInject<PreferenceManager>()

	val onTabSelected = { destination: Screen ->
		if (backStack.lastOrNull() == destination) {
			rootViewModel.requestScrollToTop()
		} else {
			backStack.apply {
				clear()
				add(destination)
			}
		}
	}

	AnimatedContent(
		preferenceManager.navigationBarStyle != NavigationBarStyle.Short
			&& platformContext.sizeClass.widthSizeClass <= WindowWidthSizeClass.Compact
			&& tabs.size > 1
	) {
		if (tabs.size < 2) return@AnimatedContent
		if (it) {
			NavigationBar(
				modifier = modifier,
				containerColor = containerColor,
				windowInsets = windowInsets
			) {
				tabs.forEach { tab ->
					val item = when (tab.id) {
						NavbarTab.Id.LIBRARY -> NavItem.LIBRARY
						NavbarTab.Id.ALBUMS -> NavItem.ALBUMS
						NavbarTab.Id.PLAYLISTS -> NavItem.PLAYLISTS
						NavbarTab.Id.ARTISTS -> NavItem.ARTISTS
						NavbarTab.Id.SEARCH -> NavItem.SEARCH
						NavbarTab.Id.GENRES -> NavItem.GENRES
						NavbarTab.Id.SONGS -> NavItem.SONGS
						NavbarTab.Id.RADIOS -> NavItem.RADIOS
					}
					val selected = backStack.lastOrNull() == item.destination

					NavigationBarItem(
						selected = selected,
						enabled = enabled,
						alwaysShowLabel = preferenceManager.navigationBarLabelVisibility
							== NavigationBarLabelVisibility.Always,
						onClick = dropUnlessResumed {
							onTabSelected(item.destination)
						},
						icon = {
							if (selected) {
								val painter = animatedTabIconPainter(item.destination)
								if (painter != null) {
									Icon(painter = painter, null)
								} else {
									Icon(item.icon, null)
								}
							} else {
								Icon(item.iconUnselected, null)
							}
						},
						label = if (preferenceManager.navigationBarLabelVisibility
							!== NavigationBarLabelVisibility.Never) {
							{
								Text(
									stringResource(item.label),
									maxLines = 1,
									autoSize = TextAutoSize.StepBased(
										minFontSize = 1.sp,
										maxFontSize = MaterialTheme.typography.labelMedium.fontSize
									)
								)
							}
						} else {
							null
						}
					)
				}
			}
		} else {
			ShortNavigationBar(
				modifier = modifier,
				containerColor = containerColor
			) {
				tabs.forEach { tab ->
					val item = when (tab.id) {
						NavbarTab.Id.LIBRARY -> NavItem.LIBRARY
						NavbarTab.Id.ALBUMS -> NavItem.ALBUMS
						NavbarTab.Id.PLAYLISTS -> NavItem.PLAYLISTS
						NavbarTab.Id.ARTISTS -> NavItem.ARTISTS
						NavbarTab.Id.SEARCH -> NavItem.SEARCH
						NavbarTab.Id.GENRES -> NavItem.GENRES
						NavbarTab.Id.SONGS -> NavItem.SONGS
						NavbarTab.Id.RADIOS -> NavItem.RADIOS
					}
					val selected = backStack.last() == item.destination

					ShortNavigationBarItem(
						iconPosition = if (platformContext.sizeClass.widthSizeClass > WindowWidthSizeClass.Compact)
							NavigationItemIconPosition.Start
						else NavigationItemIconPosition.Top,
						selected = backStack.last() == item.destination,
						enabled = enabled,
						onClick = dropUnlessResumed {
							onTabSelected(item.destination)
						},
						icon = {
							if (selected) {
								val painter = animatedTabIconPainter(item.destination)
								if (painter != null) {
									Icon(painter = painter, null)
								} else {
									Icon(item.icon, null)
								}
							} else {
								Icon(item.iconUnselected, null)
							}
						},
						label = if (
							preferenceManager.navigationBarLabelVisibility == NavigationBarLabelVisibility.Always ||
							(preferenceManager.navigationBarLabelVisibility == NavigationBarLabelVisibility.OnlySelected && selected)
						) {
							{ Text(stringResource(item.label)) }
						} else {
							null
						},
					)
				}
			}
		}
	}
}
