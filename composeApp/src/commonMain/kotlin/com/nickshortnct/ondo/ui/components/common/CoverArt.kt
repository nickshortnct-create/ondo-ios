package com.nickshortnct.ondo.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.koinInject
import com.nickshortnct.ondo.domain.manager.PreferenceManager
import com.nickshortnct.ondo.domain.manager.SessionManager
import com.nickshortnct.ondo.icons.Icons
import com.nickshortnct.ondo.icons.outlined.Album
import com.nickshortnct.ondo.util.Logger
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

private val failedCoverIds = LinkedHashSet<String>()

@Composable
fun CoverArt(
	modifier: Modifier = Modifier,
	coverArtId: String?,
	localImageUri: String? = null,
	contentScale: ContentScale = if (!localImageUri.isNullOrBlank() || coverArtId?.startsWith("ar-") == true) ContentScale.Crop else ContentScale.Fit,
	contentDescription: String? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	square: Boolean = true,
	crossfadeMs: Int = 500,
	shadowElevation: Dp = 0.dp,
	interactionSource: MutableInteractionSource? = null,
	shape: Shape? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val shape = shape ?: if (!coverArtId.orEmpty().startsWith("ar-")) {
		preferenceManager.coverArtShape.shape
	} else {
		preferenceManager.artistImageShape.shape
	}
	val coilPlatformContext = LocalCoilPlatformContext.current
	val customHeaders = preferenceManager.customHeaders
	val sessionManager = koinInject<SessionManager>()
	val model = remember(coverArtId, localImageUri, customHeaders) {
		val networkHeaders = NetworkHeaders.Builder().apply {
			preferenceManager.customHeadersMap().forEach { (key, value) -> add(key, value) }
		}.build()
		when {
			!localImageUri.isNullOrBlank() -> ImageRequest.Builder(coilPlatformContext)
				.data(localImageUri)
				.memoryCacheKey(localImageUri)
				.diskCacheKey(localImageUri)
				.diskCachePolicy(CachePolicy.ENABLED)
				.memoryCachePolicy(CachePolicy.ENABLED)
				.crossfade(crossfadeMs)
				.build()

			!coverArtId.isNullOrBlank() -> ImageRequest.Builder(coilPlatformContext)
				.data(sessionManager.getCoverArtUrl(coverArtId))
				.memoryCacheKey(coverArtId)
				.diskCacheKey(coverArtId)
				.diskCachePolicy(CachePolicy.ENABLED)
				.memoryCachePolicy(CachePolicy.ENABLED)
				.crossfade(crossfadeMs)
				.httpHeaders(networkHeaders)
				.build()

			else -> null
		}
	}

	val commonModifier = modifier
		.then(if (square) Modifier.aspectRatio(1f) else Modifier)
		.shadow(shadowElevation, shape)
		.clip(shape)
		.background(MaterialTheme.colorScheme.surfaceContainer)
		.then(
			if (onClick != null)
				Modifier.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
					interactionSource = interactionSource
				)
			else Modifier
		)
		.then(
			if (interactionSource != null)
				Modifier.indication(interactionSource, ripple())
			else Modifier
		)

	val placeholder: @Composable () -> Unit = {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Icon(
				Icons.Outlined.Album,
				contentDescription = null,
				modifier = Modifier.sizeIn(maxWidth = 48.dp, maxHeight = 48.dp).fillMaxSize(0.5f),
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
	if (model == null) return Box(commonModifier) { placeholder() }
	SubcomposeAsyncImage(
		model = model,
		contentDescription = contentDescription,
		modifier = commonModifier,
		contentScale = contentScale,
		error = {
			LaunchedEffect(it.result.throwable) {
				val key = coverArtId ?: localImageUri.orEmpty()
				if (!failedCoverIds.add(key)) return@LaunchedEffect
				if (failedCoverIds.size > 256) failedCoverIds.remove(failedCoverIds.first())
				Logger.w(
					"CoverArt",
					"Failed to load cover art, falling back to placeholder",
					it.result.throwable
				)
			}
			placeholder()
		}
	)
}
