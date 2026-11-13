package com.nickshortnct.ondo

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.nickshortnct.ondo.domain.manager.AppVisibility
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.SingletonImageLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import com.nickshortnct.ondo.di.LocalBottomBarScrollManager
import com.nickshortnct.ondo.di.LocalNavStack
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.di.LocalSharedTransitionScope
import com.nickshortnct.ondo.di.LocalSnackBarState
import com.nickshortnct.ondo.di.initializeSingletonImageLoader
import com.nickshortnct.ondo.di.rememberPlatformContext
import com.nickshortnct.ondo.domain.manager.BottomBarScrollManager
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.manager.SnackBarManager
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import com.nickshortnct.ondo.shared.MediaPlayerViewModel
import com.nickshortnct.ondo.ui.components.snackbars.NavicSnackBar
import com.nickshortnct.ondo.ui.navigation.BottomSheetSceneStrategy
import com.nickshortnct.ondo.ui.navigation.NowPlayingSceneStrategy
import com.nickshortnct.ondo.ui.navigation.AppRoutes
import com.nickshortnct.ondo.ui.navigation.Screen
import com.nickshortnct.ondo.ui.screens.album.AlbumListScreen
import com.nickshortnct.ondo.ui.screens.artist.ArtistDetailScreen
import com.nickshortnct.ondo.ui.screens.artist.ArtistListScreen
import com.nickshortnct.ondo.ui.screens.artist.DiscographyAlbumScreen
import com.nickshortnct.ondo.ui.screens.artist.DiscoveryArtistScreen
import com.nickshortnct.ondo.ui.screens.collection.CollectionDetailScreen
import com.nickshortnct.ondo.ui.screens.genre.GenreDetailScreen
import com.nickshortnct.ondo.ui.screens.genre.GenreListScreen
import com.nickshortnct.ondo.ui.screens.library.LibraryScreen
import com.nickshortnct.ondo.ui.screens.login.LoginScreen
import com.nickshortnct.ondo.ui.screens.lyrics.LyricsScreen
import com.nickshortnct.ondo.ui.screens.nowPlaying.NowPlayingScreen
import com.nickshortnct.ondo.ui.screens.nowPlaying.PlaybackSpeedScreen
import com.nickshortnct.ondo.ui.screens.playlist.PlaylistListScreen
import com.nickshortnct.ondo.ui.screens.queue.QueueScreen
import com.nickshortnct.ondo.ui.screens.radio.RadioListScreen
import com.nickshortnct.ondo.ui.screens.search.SearchScreen
import com.nickshortnct.ondo.ui.screens.settings.AudioEffectsScreen
import com.nickshortnct.ondo.ui.screens.settings.BottomBarScreen
import com.nickshortnct.ondo.ui.screens.settings.FontsScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsAboutScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsAppIconScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsAppearanceScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsCustomHeadersScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsDataStorageScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsDeveloperScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsDownloadQualityScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsEqualiserScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsLogsScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsNowPlayingScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsPlaybackScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsStreamingQualityScreen
import com.nickshortnct.ondo.ui.screens.settings.SettingsThemesScreen
import com.nickshortnct.ondo.ui.screens.share.ShareListScreen
import com.nickshortnct.ondo.ui.screens.song.SongDetailScreen
import com.nickshortnct.ondo.ui.screens.song.SongDetailSheet
import com.nickshortnct.ondo.ui.screens.song.SongListScreen
import com.nickshortnct.ondo.ui.screens.starred.StarredScreen
import com.nickshortnct.ondo.ui.theme.NavicTheme
import com.nickshortnct.ondo.ui.util.Material3Transitions

@OptIn(ExperimentalSerializationApi::class)
private val config = SavedStateConfiguration {
	serializersModule = SerializersModule {
		polymorphic(NavKey::class) {
			subclassesOfSealed<Screen>()
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
	// TODO: inject image loader and stop using this cursed singleton thing
	runCatching {
		SingletonImageLoader.setSafe { platformContext ->
			initializeSingletonImageLoader(platformContext)
		}
	}

	val platformContext = rememberPlatformContext()
	val sessionManager = koinInject<SessionManager>()
	val ondoConnect = koinInject<com.nickshortnct.ondo.ondo.OndoConnect>()
	val preferenceManager = koinInject<PreferenceManager>()
	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val backStack = rememberNavBackStack(
		config, if (isLoggedIn) {
			Screen.Library()
		} else {
			Screen.Login
		}
	)
	// A notification can ask us to open an Ondo page (for example Downloads).
	LaunchedEffect(backStack) {
		AppRoutes.pendingOndoPage.collect { page ->
			if (page != null && isLoggedIn) {
				backStack.add(Screen.Ondo(com.nickshortnct.ondo.ui.navigation.OndoPage.fromLegacy(page)))
				AppRoutes.pendingOndoPage.value = null
			}
		}
	}
	val snackBarState = remember { SnackbarHostState() }
	val snackBarManager = koinInject<SnackBarManager>()
	val mediaPlayer = koinInject<MediaPlayerViewModel>()
	// Created here so background sync starts as soon as there is a logged-in session.
	koinInject<com.nickshortnct.ondo.domain.manager.SyncManager>()

	// Reopening the app after the service released the controller must not make the first tap wait.
	LaunchedEffect(Unit) { mediaPlayer.ensureConnected() }
	// Wake a Connect socket that was dropped for being idle when the app returns to the foreground.
	LifecycleEventEffect(Lifecycle.Event.ON_START) { ondoConnect.onForeground(); AppVisibility.visible.value = true }
	LifecycleEventEffect(Lifecycle.Event.ON_STOP) { AppVisibility.visible.value = false }

	LaunchedEffect(Unit) {
		snackBarManager.events.collectLatest { event ->
			snackBarState.showSnackbar(getString(event.resource, *event.args.toTypedArray()))
		}
	}

	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val scrollManager = remember {
		BottomBarScrollManager(with(density) { 50.dp.toPx() })
	}

	var appStarted by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		if (!appStarted) {
			appStarted = true
			if (preferenceManager.explicitContentPlayback == ExplicitContentPlayback.SkipForThisSession) {
				preferenceManager.explicitContentPlayback = ExplicitContentPlayback.Allowed
			}
		}
	}

	SharedTransitionLayout {
		CompositionLocalProvider(
			LocalPlatformContext provides platformContext,
			LocalNavStack provides backStack,
			LocalSnackBarState provides snackBarState,
			LocalSharedTransitionScope provides this@SharedTransitionLayout,
			LocalBottomBarScrollManager provides scrollManager
		) {
			NavicTheme {
				// Ondo: the Now Playing glow (album cover blurred into colour orbs) behind the
				// whole app. Static here (no rotation) so it costs nothing while scrolling or in
				// the background; screens draw on a transparent surface so it shows through.
				val glowSong by koinInject<MediaPlayerViewModel>().uiState.collectAsState()
				val baseScheme = MaterialTheme.colorScheme
				Box(Modifier.fillMaxSize()) {
				com.nickshortnct.ondo.ui.components.common.BlendBackground(
					coverArtId = glowSong.currentSong?.coverArtId,
					isPaused = true
				)
				Box(Modifier.fillMaxSize().background(baseScheme.background.copy(alpha = 0.35f)))
				MaterialTheme(
					colorScheme = baseScheme.copy(background = Color.Transparent, surface = Color.Transparent),
					typography = MaterialTheme.typography,
					shapes = MaterialTheme.shapes
				) {
				Scaffold(
					containerColor = Color.Transparent,
					modifier = Modifier.nestedScroll(scrollManager.connection),
					snackbarHost = {
						SnackbarHost(hostState = snackBarState) { snackBarData ->
							NavicSnackBar(snackBarData = snackBarData)
						}
					},
					contentWindowInsets = WindowInsets()
				) { contentPadding ->
					NavDisplay(
						modifier = Modifier
							.padding(
								start = contentPadding
									.calculateStartPadding(layoutDirection),
								end = contentPadding
									.calculateEndPadding(layoutDirection)
							)
							.fillMaxSize()
							.background(MaterialTheme.colorScheme.surface),
						backStack = backStack,
						sceneStrategies = listOf(
							remember { NowPlayingSceneStrategy() },
							remember { BottomSheetSceneStrategy() },
							rememberListDetailSceneStrategy()
						),
						entryDecorators = listOf(
							rememberSaveableStateHolderNavEntryDecorator(),

							// makes it so that ViewModels get destroyed if their
							// associated screen is removed from the back stack
							//
							// this might not always be desirable, so the
							// `PersistentViewModelStoreOwner` class is used for
							// certain ViewModels to work around this
							rememberViewModelStoreNavEntryDecorator()
						),
						onBack = {
							if (backStack.size >= 2) {
								backStack.removeLastOrNull()
							}
						},
						entryProvider = entryProvider(backStack),
						transitionSpec = {
							Material3Transitions.SharedXAxisEnterTransition(
								density
							) togetherWith Material3Transitions.SharedXAxisExitTransition(
								density
							)
						},
						popTransitionSpec = {
							Material3Transitions.SharedXAxisPopEnterTransition(
								density
							) togetherWith Material3Transitions.SharedXAxisPopExitTransition(
								density
							)
						},
						predictivePopTransitionSpec = {
							slideInHorizontally(
								animationSpec = tween(300, easing = EaseOutQuart),
								initialOffsetX = { -it }
							) togetherWith slideOutHorizontally(
								animationSpec = tween(300, easing = EaseOutQuart),
								targetOffsetX = { it }
							)
						}
					)
				}
				}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
private fun entryProvider(
	backStack: NavBackStack<NavKey>
): (NavKey) -> (NavEntry<NavKey>) {
	val navtabMetadata = if (backStack.size == 1)
		listPane("root") + transitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		} + popTransitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		} + predictivePopTransitionSpec {
			ContentTransform(fadeIn(), fadeOut())
		}
	else listPane("root")
	return androidx.navigation3.runtime.entryProvider {
		// tabs
		entry<Screen.Library>(metadata = navtabMetadata) {
			LibraryScreen()
		}
		entry<Screen.Starred>(metadata = navtabMetadata) {
			StarredScreen()
		}
		entry<Screen.AlbumList>(metadata = navtabMetadata) { key ->
			AlbumListScreen(key.nested, key.listType)
		}
		entry<Screen.PlaylistList>(metadata = navtabMetadata) { key ->
			PlaylistListScreen(key.nested)
		}
		entry<Screen.ArtistList>(metadata = navtabMetadata) { key ->
			ArtistListScreen(key.nested, key.listType)
		}
		entry<Screen.GenreList>(metadata = navtabMetadata) { key ->
			GenreListScreen(key.nested)
		}
		entry<Screen.GenreDetail> { key ->
			GenreDetailScreen(key.genreName)
		}
		entry<Screen.SongList>(metadata = navtabMetadata) { key ->
			SongListScreen(key.nested, key.listType)
		}

		entry<Screen.RadioList>(metadata = navtabMetadata) { key ->
			RadioListScreen(key.nested)
		}

		entry<Screen.Ondo> { key -> com.nickshortnct.ondo.ondo.OndoScreen(key.page) }
		// misc
		entry<Screen.Login> {
			LoginScreen()
		}
		entry<Screen.NowPlaying>(
			metadata = NowPlayingSceneStrategy.bottomSheet(maxWidth = Dp.Unspecified)
		) {
			NowPlayingScreen()
		}
		entry<Screen.Lyrics>(metadata = NowPlayingSceneStrategy.bottomSheet(isTransparent = true)) {
			val player = koinInject<MediaPlayerViewModel>()
			val playerState by player.uiState.collectAsState()
			val song = playerState.currentSong
			LyricsScreen(song)
		}
		entry<Screen.Queue>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			QueueScreen()
		}
		entry<Screen.PlaybackSpeed>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			PlaybackSpeedScreen()
		}
		entry<Screen.CollectionDetail>(metadata = detailPane("root")) { key ->
			CollectionDetailScreen(key.collectionId, key.tab)
		}
		entry<Screen.SongDetailScreen> { key ->
			SongDetailScreen(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.SongDetailSheet>(
			metadata = { key ->
				BottomSheetSceneStrategy.bottomSheet(coverArtId = key.coverArtId)
			}
		) { key ->
			SongDetailSheet(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.Search>(metadata = navtabMetadata) { key ->
			SearchScreen(key.nested, initialQuery = key.query)
		}
		entry<Screen.ShareList> {
			ShareListScreen()
		}
		entry<Screen.ArtistDetail> { key ->
			ArtistDetailScreen(key.artist)
		}
		entry<Screen.DiscoveryArtist> { key ->
			DiscoveryArtistScreen(key.name, key.musicBrainzId, key.skipLocalRedirect)
		}
		entry<Screen.DiscographyAlbum> { key ->
			DiscographyAlbumScreen(key.foreignAlbumId, key.title, key.artistName)
		}

		// settings
		entry<Screen.Settings.Root>(metadata = listPane("settings")) {
			SettingsScreen()
		}
		entry<Screen.Settings.Appearance>(metadata = detailPane("settings")) {
			SettingsAppearanceScreen()
		}
		entry<Screen.Settings.BottomAppBar>(metadata = detailPane("settings")) {
			BottomBarScreen()
		}
		entry<Screen.Settings.NowPlaying>(metadata = detailPane("settings")) {
			SettingsNowPlayingScreen()
		}
		entry<Screen.Settings.Playback>(metadata = detailPane("settings")) {
			SettingsPlaybackScreen()
		}
		entry<Screen.Settings.Effects>(metadata = detailPane("settings")) {
			AudioEffectsScreen()
		}
		entry<Screen.Settings.Developer>(metadata = detailPane("settings")) {
			SettingsDeveloperScreen()
		}
		entry<Screen.Settings.About>(metadata = detailPane("settings")) {
			SettingsAboutScreen()
		}
		entry<Screen.Settings.DataStorage>(metadata = detailPane("settings")) {
			SettingsDataStorageScreen()
		}
		entry<Screen.Settings.Fonts> {
			FontsScreen()
		}
		entry<Screen.Settings.Themes> {
			SettingsThemesScreen()
		}
		entry<Screen.Settings.CustomHeaders> {
			SettingsCustomHeadersScreen()
		}
		entry<Screen.Settings.StreamingQuality> {
			SettingsStreamingQualityScreen()
		}
		entry<Screen.Settings.DownloadQuality> {
			SettingsDownloadQualityScreen()
		}
		entry<Screen.Settings.Logs> {
			SettingsLogsScreen()
		}
		entry<Screen.Settings.AppIcon>(metadata = detailPane("settings")) {
			SettingsAppIconScreen()
		}
		entry<Screen.Settings.Equaliser> {
			SettingsEqualiserScreen()
		}
	}
}
