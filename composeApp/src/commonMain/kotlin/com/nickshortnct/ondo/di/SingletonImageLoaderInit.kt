package com.nickshortnct.ondo.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import org.koin.mp.KoinPlatform.getKoin
import coil3.PlatformContext as CoilPlatformContext

/**
 * App-private directory for cached cover art. Unlike the temporary/cache
 * directory, the OS doesn't clear it on its own, so covers (including those of
 * downloaded songs) stay available offline.
 */
internal expect fun coverArtCacheDirectory(context: CoilPlatformContext): Path

internal val imageCacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private var sharedMemoryCache: MemoryCache? = null
private var sharedDiskCache: DiskCache? = null

private fun getMemoryCache(context: CoilPlatformContext): MemoryCache {
	return sharedMemoryCache ?: MemoryCache.Builder()
		.maxSizePercent(context, 0.15)
		.build().also { sharedMemoryCache = it }
}

private fun getDiskCache(context: CoilPlatformContext): DiskCache {
	return sharedDiskCache ?: DiskCache.Builder()
		.directory(coverArtCacheDirectory(context) / "images")
		.maxSizeBytes(2L shl 30)
		.build().also {
			sharedDiskCache = it
			// The cache used to live in the temporary directory; drop it.
			imageCacheScope.launch {
				runCatching { FileSystem.SYSTEM.deleteRecursively(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache") }
			}
		}
}

private fun coverArtInterceptor() = with(getKoin()) {
	StableCoverArtInterceptor(get<CoverArtVersions>(), get<DownloadedCoverArt>())
}

fun initializeSingletonImageLoader(context: CoilPlatformContext): ImageLoader {
	return ImageLoader.Builder(context)
		.components {
			add(coverArtInterceptor())
			add(KtorNetworkFetcherFactory())
		}
		.memoryCache { getMemoryCache(context) }
		.diskCache { getDiskCache(context) }
		.crossfade(true)
		.build()
}

fun getStaticImageLoader(context: CoilPlatformContext): ImageLoader {
	return ImageLoader.Builder(context)
		.serviceLoaderEnabled(false)
		.components {
			add(coverArtInterceptor())
			add(KtorNetworkFetcherFactory())
		}
		.memoryCache { getMemoryCache(context) }
		.diskCache { getDiskCache(context) }
		.crossfade(true)
		.build()
}
