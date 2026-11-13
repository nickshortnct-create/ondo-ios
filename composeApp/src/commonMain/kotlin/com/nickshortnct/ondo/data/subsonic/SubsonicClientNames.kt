package com.nickshortnct.ondo.data.subsonic

/**
 * Navidrome applies [ND_SUBSONIC_MINIMALCLIENTS] per Subsonic client id.
 *
 * - [MINIMAL] is used only for playlist detail fetches so large playlists
 *   stay within response-size limits. The playlist list uses [METADATA], since
 *   it has no songs and the minimal list drops owner, comment and coverArt.
 * - [METADATA] must stay off that allowlist so album/song/library APIs return
 *   artist, duration, suffix, coverArt, etc.
 */
object SubsonicClientNames {
	const val MINIMAL = "Ondo"
	const val METADATA = "OndoMeta"
}
