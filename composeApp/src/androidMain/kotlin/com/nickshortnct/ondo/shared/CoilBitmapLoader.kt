package com.nickshortnct.ondo.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Session artwork loader backed by Coil's caches. Queue items carry only an
 * artwork *URI* (never embedded bytes), so notification and Android Auto
 * artwork resolve here, at a bounded size, and keep working offline once the
 * cover has been viewed in the app (same cache key as `CoverArt`) or its song
 * has been downloaded.
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(
	private val context: Context,
	private val headers: () -> Map<String, String>
) : BitmapLoader {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun supportsMimeType(mimeType: String) = true

	override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
		future { BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Could not decode artwork") }

	override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = future {
		// Cover URLs carry the Subsonic coverArtId as `id`; use it as the cache key like CoverArt does.
		val coverId = uri.getQueryParameter("id")
		val networkHeaders = NetworkHeaders.Builder().apply {
			headers().forEach { (key, value) -> add(key, value) }
		}.build()
		val request = ImageRequest.Builder(context)
			.data(uri.toString())
			.size(ARTWORK_SIZE_PX)
			.apply { if (coverId != null) memoryCacheKey(coverId).diskCacheKey(coverId) }
			.allowHardware(false)
			.httpHeaders(networkHeaders)
			.build()
		val result = context.imageLoader.execute(request) as? SuccessResult ?: error("Artwork unavailable")
		result.image.toBitmap()
	}

	private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
		val future = SettableFuture.create<T>()
		val job = scope.launch {
			try {
				future.set(block())
			} catch (e: CancellationException) {
				future.cancel(false)
				throw e
			} catch (e: Exception) {
				future.setException(e)
			}
		}
		future.addListener({ if (future.isCancelled) job.cancel() }, Runnable::run)
		return future
	}

	private companion object {
		const val ARTWORK_SIZE_PX = 1024
	}
}
