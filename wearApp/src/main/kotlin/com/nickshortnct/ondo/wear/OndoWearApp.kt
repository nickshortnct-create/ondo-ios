package com.nickshortnct.ondo.wear

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.nickshortnct.ondo.wear.data.AccountStore
import com.nickshortnct.ondo.wear.data.PhoneLink
import com.nickshortnct.ondo.wear.data.SubsonicApi
import com.nickshortnct.ondo.wear.data.WatchSettings
import com.nickshortnct.ondo.wear.downloads.DownloadStore
import com.nickshortnct.ondo.wear.playback.WatchMediaItems
import com.nickshortnct.ondo.wear.playback.WatchPlayer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class OndoWearApp : Application(), SingletonImageLoader.Factory {
	val scope = MainScope()
	val accounts by lazy { AccountStore(this) }
	val settings by lazy { WatchSettings(this) }
	val api by lazy { SubsonicApi(accounts) }
	val downloads by lazy { DownloadStore(this) }
	val mediaItems by lazy { WatchMediaItems(api, downloads, settings) }
	val player by lazy { WatchPlayer(this, mediaItems, scope) }
	val phoneLink by lazy { PhoneLink(this, accounts, scope) }

	fun scopeLaunch(block: suspend () -> Unit) {
		scope.launch { block() }
	}

	override fun onCreate() {
		super.onCreate()
		phoneLink.start()
	}

	override fun newImageLoader(context: PlatformContext): ImageLoader {
		// Gateways in front of the server may need the same custom headers as the API.
		val client = OkHttpClient.Builder().addInterceptor { chain ->
			val request = chain.request().newBuilder().apply {
				api.headers().forEach { (k, v) -> header(k, v) }
			}.build()
			chain.proceed(request)
		}.build()
		return ImageLoader.Builder(context)
			.components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
			.build()
	}
}
