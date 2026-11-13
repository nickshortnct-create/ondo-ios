package com.nickshortnct.ondo.domain.manager

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nickshortnct.ondo.domain.manager.base.BasePreferenceManager
import com.nickshortnct.ondo.domain.models.settings.AnimationStyle
import com.nickshortnct.ondo.domain.models.settings.AppIconVariant
import com.nickshortnct.ondo.domain.models.settings.BottomBarCollapseMode
import com.nickshortnct.ondo.domain.models.settings.BottomBarVisibilityMode
import com.nickshortnct.ondo.domain.models.settings.CoverArtQuality
import com.nickshortnct.ondo.domain.models.settings.CoverArtShape
import com.nickshortnct.ondo.domain.models.settings.CoverArtTapAction
import com.nickshortnct.ondo.domain.models.settings.ExplicitContentPlayback
import com.nickshortnct.ondo.domain.models.settings.FontOption
import com.nickshortnct.ondo.domain.models.settings.GridSize
import com.nickshortnct.ondo.domain.models.settings.ListViewMode
import com.nickshortnct.ondo.domain.models.settings.MarqueeSpeed
import com.nickshortnct.ondo.domain.models.settings.MiniPlayerProgressStyle
import com.nickshortnct.ondo.domain.models.settings.MiniPlayerStyle
import com.nickshortnct.ondo.domain.models.settings.NavigationBarLabelVisibility
import com.nickshortnct.ondo.domain.models.settings.NavigationBarStyle
import com.nickshortnct.ondo.domain.models.settings.NowPlayingBackgroundStyle
import com.nickshortnct.ondo.domain.models.settings.NowPlayingSliderStyle
import com.nickshortnct.ondo.domain.models.settings.OfflineMode
import com.nickshortnct.ondo.domain.models.settings.ReplayGainMode
import com.nickshortnct.ondo.domain.models.settings.StreamingQuality
import com.nickshortnct.ondo.domain.models.settings.Theme
import com.nickshortnct.ondo.domain.models.settings.ThemeMode
import com.nickshortnct.ondo.domain.models.settings.ToolbarPosition
import com.russhwolf.settings.Settings as KmpSettings
import kotlinx.serialization.json.Json

class PreferenceManager(
	settings: KmpSettings
) : BasePreferenceManager(settings) {
	var appIconVariant by preference(AppIconVariant.Default)
	var font by preference(FontOption.GoogleSans)
	var fontPath by preference("")
	var animationStyle by preference(AnimationStyle.Expressive)
	var nowPlayingBackgroundStyle by preference(NowPlayingBackgroundStyle.Dynamic)
	var swipeToSkip by preference(true)
	var hideIfIdle by preference(false)
	var gridSize by preference(GridSize.TwoByTwo)
	var coverArtShape by preference(CoverArtShape.Soft)
	var artistImageShape by preference(CoverArtShape.Soft)
	var coverArtQuality by preference(CoverArtQuality.Medium)

	/** Ondo Connect is opt-in; installs that were already signed in keep it on (see [migrateConnectEnabled]). */
	var connectEnabled by preference(false)

	fun migrateConnectEnabled(wasSignedIn: Boolean) {
		if (!settings.hasKey("connectEnabled") && wasSignedIn) connectEnabled = true
	}
	var artGridItemSize by preference(150f)
	var marqueeSpeed by preference(MarqueeSpeed.Slow)
	var alphabeticalScroll by preference(false)
	var enableRatings by preference(true)
	var enableSharing by preference(true)
	var lyricsAutoscroll by preference(true)
	var lyricsBeatByBeat by preference(true)
	// Positive values advance the displayed lyric to compensate for late timing data.
	var lyricsTimingOffsetMs by preference(0)
	// Bluetooth devices (by name, one per line) that hide the lyric preview on Now Playing,
	// e.g. a car whose audio delay throws synced lyrics out of time.
	var lyricsHiddenOutputs by preference("")

	fun lyricsHiddenOutputNames(): Set<String> =
		lyricsHiddenOutputs.lines().filter { it.isNotBlank() }.toSet()

	fun setLyricsHiddenOn(deviceName: String, hidden: Boolean) {
		val names = lyricsHiddenOutputNames()
		lyricsHiddenOutputs = (if (hidden) names + deviceName else names - deviceName).joinToString("\n")
	}
	var lyricsKeepAlive by preference(true)
	var lyricsBlur by preference(false)
	var lyricsBrightInactive by preference(false)
	var enableScrobbling by preference(true)
	var scrobblePercentage by preference(.5f)
	var minDurationToScrobble by preference(30f)
	var replayGainMode by preference(ReplayGainMode.Off)
	var rgAmpGain by preference(0f)
	var ampGain by preference(0f)
	var gaplessPlayback by preference(true)
	var audioOffload by preference(false)

	// TODO: better names and strings for these transcoding settings
	var streamingQualityWifi by preference(StreamingQuality.Lossless)
	var streamingQualityCellular by preference(StreamingQuality.Lossless)
	var isAdvancedTranscodingActive by preference(false)
	var customMaxBitrateWifi by preference(0)
	var customMaxBitrateCellular by preference(0)
	var customFormatWifi by preference("")
	var customFormatCellular by preference("")

	var downloadQualityWifi by preference(StreamingQuality.Lossless)
	var downloadQualityCellular by preference(StreamingQuality.Lossless)
	var isAdvancedDownloadTranscodingActive by preference(false)
	var customDownloadMaxBitrateWifi by preference(0)
	var customDownloadMaxBitrateCellular by preference(0)
	var customDownloadFormatWifi by preference("")
	var customDownloadFormatCellular by preference("")

	var nowPlayingToolbarPosition by preference(ToolbarPosition.Bottom)
	var nowPlayingSongInfo by preference(true)
	var nowPlayingSliderStyle by preference(NowPlayingSliderStyle.Squiggly)
	var nowPlayingCoverArtAction by preference(CoverArtTapAction.ShowLyrics)
	var customHeaders by preference("")
	var explicitContentPlayback by preference(ExplicitContentPlayback.Allowed)
	var autoFillQueue by preference(false)
	var shushQueueDuplicateDialog by preference(false)

	// navigation bar settings
	var bottomBarCollapseMode by preference(BottomBarCollapseMode.OnScroll)
	var bottomBarVisibilityMode by preference(BottomBarVisibilityMode.AllScreens)
	var navigationBarStyle by preference(NavigationBarStyle.Normal)
	var navigationBarLabelVisibility by preference(
		NavigationBarLabelVisibility.Always
	)
	var miniPlayerStyle by preference(MiniPlayerStyle.Detached)
	var miniPlayerProgressStyle by preference(MiniPlayerProgressStyle.Seekable)

	// theme related settings
	var theme by preference(Theme.Dynamic)
	var themeMode by preference(ThemeMode.System)
	var dynamicTheming by preference(false)
	var paletteStyle by preference(PaletteStyle.TonalSpot)
	var paletteSpec by preference(ColorSpec.SpecVersion.SPEC_2025)
	var paletteAccentH by preference(0f)

	// sync related settings
	var lastFullSyncTime by preference(0L)

	// sorting/view mode preferences
	var albumListViewMode by preference(ListViewMode.Grid)
	var playlistListViewMode by preference(ListViewMode.List)
	var artistListViewMode by preference(ListViewMode.List)

	fun getPlaylistTrackSortName(playlistId: String): String =
		settings.getString("${playlistTrackSortPreferenceKey(playlistId)}.sort", "DateAdded")

	fun setPlaylistTrackSortName(playlistId: String, sortName: String) {
		settings.putString("${playlistTrackSortPreferenceKey(playlistId)}.sort", sortName)
	}

	fun getPlaylistTrackSortDescending(playlistId: String): Boolean =
		settings.getBoolean("${playlistTrackSortPreferenceKey(playlistId)}.descending", false)

	fun setPlaylistTrackSortDescending(playlistId: String, descending: Boolean) {
		settings.putBoolean("${playlistTrackSortPreferenceKey(playlistId)}.descending", descending)
	}

	private fun playlistTrackSortPreferenceKey(playlistId: String): String {
		val server = settings.getString("instanceUrl", "").trimEnd('/')
		val username = settings.getString("username", "")
		return "playlistTrackSort.$server|$username|$playlistId"
	}

	fun getSearchHistory(): List<String> = runCatching {
		Json.decodeFromString<List<String>>(settings.getString(searchHistoryPreferenceKey(), "[]"))
	}.getOrDefault(emptyList()).filter { it.isNotBlank() }.take(10)

	fun setSearchHistory(history: List<String>) {
		settings.putString(searchHistoryPreferenceKey(), Json.encodeToString(history.take(10)))
	}

	private fun searchHistoryPreferenceKey(): String {
		val server = settings.getString("instanceUrl", "").trimEnd('/')
		val username = settings.getString("username", "")
		return "searchHistory.$server|$username"
	}

	// these values are bitmasks of `DomainFilter`
	var albumFilters by preference(0)
	var songFilters by preference(0)
	var artistFilters by preference(0)
	var playlistFilters by preference(0)

	fun customHeadersMap(): Map<String, String> = buildMap {
		for (line in customHeaders.lines()) {
			val parts = line.split(":", limit = 2)
			if (parts.size < 2) continue

			val rawKey = parts[0]
			val rawValue = parts[1]

			val key = rawKey.trim()
			val value = rawValue.trim()
			if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
		}
	}

	var offlineMode by preference(OfflineMode.Auto)
}
