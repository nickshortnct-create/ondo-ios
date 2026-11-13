package com.nickshortnct.ondo.ondo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_pin
import ondo.composeapp.generated.resources.action_unpin
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.domain.models.DomainAlbum
import com.nickshortnct.ondo.domain.models.DomainArtist
import com.nickshortnct.ondo.domain.models.DomainPlaylist
import com.nickshortnct.ondo.domain.models.DomainSong
import com.nickshortnct.ondo.domain.models.DomainSongCollection
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.PushPin
import com.nickshortnct.ondo.icons.outlined.PushPinOff

@Serializable
enum class PinKind { Song, Album, Playlist, Artist }

/** Something pinned to the top of Library. Songs keep their details so they can play straight away. */
@Immutable
@Serializable
data class PinnedItem(
	val kind: PinKind,
	val id: String,
	val title: String,
	val subtitle: String = "",
	val coverArtId: String? = null,
	val song: DomainSong? = null
)

fun pinnedItemOf(collection: DomainSongCollection): PinnedItem? = when (collection) {
	is DomainAlbum -> PinnedItem(PinKind.Album, collection.id, collection.name, collection.artistName, collection.coverArtId)
	is DomainPlaylist -> PinnedItem(PinKind.Playlist, collection.id, collection.name, "", collection.coverArtId)
	else -> null
}

fun pinnedItemOf(artist: DomainArtist) = PinnedItem(PinKind.Artist, artist.id, artist.name, "", artist.coverArtId)

fun pinnedItemOf(song: DomainSong) = PinnedItem(PinKind.Song, song.id, song.title, song.artistName, song.coverArtId, song)

/** Pins are kept on this device, per server and account, newest first. */
class PinManager(private val settings: Settings, private val sessionManager: SessionManager) {
	private val key get() = "ondo.pins." + settings.getString("instanceUrl", "").trimEnd('/') + "|" + sessionManager.username

	val pins: StateFlow<List<PinnedItem>>
		field = MutableStateFlow(load())

	private fun load(): List<PinnedItem> = runCatching {
		ondoJson.decodeFromString<List<PinnedItem>>(settings.getString(key, "[]"))
	}.getOrDefault(emptyList())

	/** Re-reads pins after the account or server changes. */
	fun reload() {
		pins.value = load()
	}

	fun isPinned(kind: PinKind, id: String) = pins.value.any { it.kind == kind && it.id == id }

	fun pin(item: PinnedItem) = save(listOf(item) + pins.value.filterNot { it.kind == item.kind && it.id == item.id })

	fun unpin(kind: PinKind, id: String) = save(pins.value.filterNot { it.kind == kind && it.id == id })

	fun toggle(item: PinnedItem) = if (isPinned(item.kind, item.id)) unpin(item.kind, item.id) else pin(item)

	private fun save(list: List<PinnedItem>) {
		val capped = list.take(MAX_PINS)
		pins.value = capped
		settings.putString(key, ondoJson.encodeToString(capped))
	}

	private companion object {
		const val MAX_PINS = 30
	}
}

/** "Pin to Library" / "Unpin from Library" for the long-press sheets. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PinSheetItem(
	item: PinnedItem?,
	colors: ListItemColors,
	contentPadding: PaddingValues,
	onDismissRequest: () -> Unit
) {
	if (item == null) return
	val pinManager = koinInject<PinManager>()
	val pins by pinManager.pins.collectAsStateWithLifecycle()
	val pinned = pins.any { it.kind == item.kind && it.id == item.id }
	ListItem(
		content = { Text(stringResource(if (pinned) Res.string.action_unpin else Res.string.action_pin)) },
		leadingContent = { Icon(if (pinned) Icons.Outlined.PushPinOff else Icons.Outlined.PushPin, null) },
		onClick = {
			pinManager.toggle(item)
			onDismissRequest()
		},
		colors = colors,
		contentPadding = contentPadding
	)
}
