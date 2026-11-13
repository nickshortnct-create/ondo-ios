package com.nickshortnct.ondo.androidApp.wear

/**
 * Data Layer contract shared with the watch app (`wearApp`, same package and
 * signing key -- the Data Layer only connects apps with both in common).
 * Keep in sync with `com.nickshortnct.ondo.wear.data.WearContract`.
 */
object WearContract {
	const val PATH_NOW_PLAYING = "/ondo/now-playing"
	const val PATH_ACCOUNT = "/ondo/account"
	const val PATH_COMMAND = "/ondo/command"
	const val PATH_REQUEST_ACCOUNT = "/ondo/request-account"

	// now-playing keys
	const val KEY_HAS_ITEM = "hasItem"
	const val KEY_SONG_ID = "songId"
	const val KEY_TITLE = "title"
	const val KEY_ARTIST = "artist"
	const val KEY_ALBUM = "album"
	const val KEY_PLAYING = "playing"
	const val KEY_BUFFERING = "buffering"
	const val KEY_SHUFFLE = "shuffle"
	const val KEY_REPEAT = "repeat"
	const val KEY_STARRED = "starred"
	const val KEY_CAN_FAVORITE = "canFavorite"
	const val KEY_POSITION = "positionMs"
	const val KEY_DURATION = "durationMs"
	const val KEY_POSITION_AT = "positionAt"
	const val KEY_QUEUE_INDEX = "queueIndex"
	const val KEY_QUEUE_SIZE = "queueSize"
	const val KEY_VOLUME = "volume"
	const val KEY_MAX_VOLUME = "maxVolume"
	const val KEY_ART = "art"
	const val KEY_ART_ID = "artId"

	// account keys
	const val KEY_LOGGED_IN = "loggedIn"
	const val KEY_URL = "url"
	const val KEY_USERNAME = "username"
	const val KEY_TOKEN = "token"
	const val KEY_SALT = "salt"
	const val KEY_HEADERS = "headers"
	const val KEY_UPDATED_AT = "updatedAt"

	// commands: "<name>" or "<name>|<arg>|<arg>"
	const val CMD_TOGGLE = "toggle"
	const val CMD_PLAY = "play"
	const val CMD_PAUSE = "pause"
	const val CMD_NEXT = "next"
	const val CMD_PREVIOUS = "previous"
	const val CMD_SHUFFLE = "shuffle"
	const val CMD_REPEAT = "repeat"
	const val CMD_FAVORITE = "favorite"
	const val CMD_VOLUME_UP = "volume_up"
	const val CMD_VOLUME_DOWN = "volume_down"
	const val CMD_SEEK = "seek"
	const val CMD_PLAY_MEDIA = "play_media"
	const val CMD_PLAY_SONGS = "play_songs"
	const val CMD_REFRESH = "refresh"
}
