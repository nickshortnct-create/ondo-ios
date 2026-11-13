package com.nickshortnct.ondo.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import com.nickshortnct.ondo.wear.OndoWearApp
import com.nickshortnct.ondo.wear.R

val ondoApp: OndoWearApp
	@Composable get() = LocalContext.current.applicationContext as OndoWearApp

/** A scrolling screen with the Wear scroll indicator and time text. */
@Composable
fun ScreenList(
	title: String? = null,
	content: ScalingLazyListScope.() -> Unit,
) {
	val state = rememberScalingLazyListState()
	ScreenScaffold(scrollState = state) { padding ->
		ScalingLazyColumn(
			state = state,
			contentPadding = padding,
			modifier = Modifier.fillMaxWidth(),
		) {
			if (title != null) item { ListHeader { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) } }
			content()
		}
	}
}

sealed interface Load<out T> {
	data object Loading : Load<Nothing>
	data class Ready<T>(val value: T) : Load<T>
	data class Failed(val message: String) : Load<Nothing>
}

/** Runs [block] when [key] changes; `retry()` runs it again. */
class Loader<T>(val state: Load<T>, val retry: () -> Unit)

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Loader<T> {
	var attempt by remember { mutableIntStateOf(0) }
	var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
	LaunchedEffect(*keys, attempt) {
		state = Load.Loading
		state = try {
			Load.Ready(block())
		} catch (e: kotlinx.coroutines.CancellationException) {
			throw e
		} catch (e: Exception) {
			Load.Failed(e.message ?: "Something went wrong")
		}
	}
	return Loader(state) { attempt++ }
}

fun <T> ScalingLazyListScope.loadItems(loader: Loader<T>, content: ScalingLazyListScope.(T) -> Unit) {
	when (val s = loader.state) {
		Load.Loading -> item {
			Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
		}
		is Load.Failed -> {
			item { Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
			item {
				FilledTonalButton(
					onClick = loader.retry,
					modifier = Modifier.fillMaxWidth(),
					icon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
					label = { Text("Retry") },
				)
			}
		}
		is Load.Ready -> content(s.value)
	}
}

@Composable
fun Cover(model: Any?, size: Dp = ButtonDefaults.IconSize * 1.6f, round: Boolean = false) {
	val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
	if (model == null) {
		Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, modifier = Modifier.size(size))
	} else {
		AsyncImage(
			model = model,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier.size(size).clip(shape),
		)
	}
}

@Composable
fun NavButton(label: String, icon: Int, secondary: String? = null, onClick: () -> Unit) {
	FilledTonalButton(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		icon = { Icon(painterResource(icon), contentDescription = null) },
		secondaryLabel = secondary?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
		label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
	)
}

@Composable
fun MediaRow(title: String, subtitle: String?, cover: Any?, round: Boolean = false, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		colors = ButtonDefaults.filledTonalButtonColors(),
		icon = { Cover(cover, round = round) },
		secondaryLabel = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
		label = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
	)
}

fun formatDuration(ms: Long): String {
	val total = (ms / 1000).coerceAtLeast(0)
	return "%d:%02d".format(total / 60, total % 60)
}

