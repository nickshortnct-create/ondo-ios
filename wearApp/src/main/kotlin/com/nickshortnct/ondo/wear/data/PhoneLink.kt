package com.nickshortnct.ondo.wear.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What the phone app is playing, as mirrored over the Data Layer. */
data class PhoneNowPlaying(
	val hasItem: Boolean,
	val songId: String,
	val title: String,
	val artist: String,
	val album: String,
	val playing: Boolean,
	val buffering: Boolean,
	val shuffle: Boolean,
	val repeatMode: Int,
	val starred: Boolean,
	val canFavorite: Boolean,
	val positionMs: Long,
	val durationMs: Long,
	val positionAt: Long,
	val queueIndex: Int,
	val queueSize: Int,
	val volume: Int,
	val maxVolume: Int,
	val artId: String,
) {
	/** Position now, extrapolated from the last update while playing. */
	fun currentPositionMs(now: Long = System.currentTimeMillis()): Long {
		val elapsed = if (playing && !buffering) (now - positionAt).coerceAtLeast(0) else 0
		val position = positionMs + elapsed
		return if (durationMs > 0) position.coerceAtMost(durationMs) else position
	}

	companion object {
		fun from(map: DataMap) = PhoneNowPlaying(
			hasItem = map.getBoolean(WearContract.KEY_HAS_ITEM),
			songId = map.getString(WearContract.KEY_SONG_ID).orEmpty(),
			title = map.getString(WearContract.KEY_TITLE).orEmpty(),
			artist = map.getString(WearContract.KEY_ARTIST).orEmpty(),
			album = map.getString(WearContract.KEY_ALBUM).orEmpty(),
			playing = map.getBoolean(WearContract.KEY_PLAYING),
			buffering = map.getBoolean(WearContract.KEY_BUFFERING),
			shuffle = map.getBoolean(WearContract.KEY_SHUFFLE),
			repeatMode = map.getInt(WearContract.KEY_REPEAT),
			starred = map.getBoolean(WearContract.KEY_STARRED),
			canFavorite = map.getBoolean(WearContract.KEY_CAN_FAVORITE),
			positionMs = map.getLong(WearContract.KEY_POSITION),
			durationMs = map.getLong(WearContract.KEY_DURATION),
			positionAt = map.getLong(WearContract.KEY_POSITION_AT),
			queueIndex = map.getInt(WearContract.KEY_QUEUE_INDEX),
			queueSize = map.getInt(WearContract.KEY_QUEUE_SIZE),
			volume = map.getInt(WearContract.KEY_VOLUME),
			maxVolume = map.getInt(WearContract.KEY_MAX_VOLUME, 15),
			artId = map.getString(WearContract.KEY_ART_ID).orEmpty(),
		)
	}
}

/**
 * Talks to the phone app: receives its Now Playing state and login, and sends
 * remote-control commands. Works without a server login on the watch.
 */
class PhoneLink(
	private val context: Context,
	private val accounts: AccountStore,
	private val scope: CoroutineScope,
) : DataClient.OnDataChangedListener {
	private val dataClient = Wearable.getDataClient(context)
	private val messageClient = Wearable.getMessageClient(context)
	private val nodeClient = Wearable.getNodeClient(context)

	private val _nowPlaying = MutableStateFlow<PhoneNowPlaying?>(null)
	val nowPlaying: StateFlow<PhoneNowPlaying?> get() = _nowPlaying

	private val _artwork = MutableStateFlow<Bitmap?>(null)
	val artwork: StateFlow<Bitmap?> get() = _artwork
	private var artworkId: String? = null

	private val _phoneConnected = MutableStateFlow(false)
	val phoneConnected: StateFlow<Boolean> get() = _phoneConnected

	fun start() {
		dataClient.addListener(this)
		scope.launch { refresh() }
	}

	/** Re-reads the latest phone state; also asks for the login if we have none. */
	suspend fun refresh() {
		runCatching {
			_phoneConnected.value = nodeClient.connectedNodes.await().isNotEmpty()
			val items = dataClient.dataItems.await()
			try {
				items.forEach { item -> handle(item.uri.path, DataMapItem.fromDataItem(item).dataMap) }
			} finally {
				items.release()
			}
			if (accounts.account.value == null) requestAccount()
			send(WearContract.CMD_REFRESH)
		}
	}

	override fun onDataChanged(events: DataEventBuffer) {
		events.forEach { event ->
			if (event.type == DataEvent.TYPE_CHANGED) {
				handle(event.dataItem.uri.path, DataMapItem.fromDataItem(event.dataItem).dataMap)
			}
		}
	}

	/** Shared with [PhoneDataListenerService] for updates while the app is closed. */
	fun handle(path: String?, map: DataMap) {
		when (path) {
			WearContract.PATH_NOW_PLAYING -> {
				val state = PhoneNowPlaying.from(map)
				_nowPlaying.value = state
				val asset = map.getAsset(WearContract.KEY_ART)
				if (!state.hasItem) {
					artworkId = null
					_artwork.value = null
				} else if (asset != null && state.artId.isNotBlank() && state.artId != artworkId) {
					artworkId = state.artId
					scope.launch {
						runCatching {
							val stream = dataClient.getFdForAsset(asset).await().inputStream
							stream.use { BitmapFactory.decodeStream(it) }
						}.getOrNull()?.let { _artwork.value = it }
					}
				}
			}
			WearContract.PATH_ACCOUNT -> {
				val loggedIn = map.getBoolean(WearContract.KEY_LOGGED_IN)
				val url = map.getString(WearContract.KEY_URL).orEmpty()
				accounts.save(
					if (!loggedIn || url.isBlank()) null else Account(
						url = url,
						username = map.getString(WearContract.KEY_USERNAME).orEmpty(),
						token = map.getString(WearContract.KEY_TOKEN).orEmpty(),
						salt = map.getString(WearContract.KEY_SALT).orEmpty(),
						headers = AccountStore.parseHeaders(map.getString(WearContract.KEY_HEADERS).orEmpty()),
					)
				)
			}
		}
	}

	suspend fun requestAccount(): Boolean = sendRaw(WearContract.PATH_REQUEST_ACCOUNT, ByteArray(0))

	/** Sends a command to every connected phone; returns false when no phone is reachable. */
	suspend fun send(command: String, vararg args: String): Boolean =
		sendRaw(WearContract.PATH_COMMAND, (listOf(command) + args).joinToString("|").toByteArray(Charsets.UTF_8))

	fun sendAsync(command: String, vararg args: String) {
		scope.launch { send(command, *args) }
	}

	private suspend fun sendRaw(path: String, payload: ByteArray): Boolean = runCatching {
		val nodes = nodeClient.connectedNodes.await()
		_phoneConnected.value = nodes.isNotEmpty()
		nodes.forEach { messageClient.sendMessage(it.id, path, payload).await() }
		nodes.isNotEmpty()
	}.getOrDefault(false)

	/** Optimistic local update so the watch UI reacts before the phone confirms. */
	fun updateLocally(transform: (PhoneNowPlaying) -> PhoneNowPlaying) {
		_nowPlaying.value?.let { _nowPlaying.value = transform(it) }
	}
}
