package com.nickshortnct.ondo.ondo

import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val ONDO_YOUTUBE_REFERER = "https://com.nickshortnct.ondo"

@Composable
actual fun YouTubeWebView(url: String, modifier: Modifier) {
	val holder = remember { arrayOfNulls<WebView>(1) }
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	DisposableEffect(lifecycle) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
				Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
				else -> Unit
			}
		}
		lifecycle.addObserver(observer)
		onDispose { lifecycle.removeObserver(observer) }
	}
	AndroidView(
		modifier = modifier,
		factory = { context ->
			WebView(context).apply {
				holder[0] = this
				settings.javaScriptEnabled = true
				settings.domStorageEnabled = true
				settings.mediaPlaybackRequiresUserGesture = true
				settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
				webViewClient = object : WebViewClient() {
					override fun shouldOverrideUrlLoading(
						view: WebView?,
						request: WebResourceRequest?
					): Boolean {
						val uri = request?.url ?: return false
						if (!request.isForMainFrame) return false
						val host = uri.host?.removePrefix("www.")
						if (uri.scheme == "https" && host in setOf("youtube.com", "youtube-nocookie.com") && uri.path.orEmpty().startsWith("/embed/")) return false
						if (uri.scheme in setOf("http", "https")) {
							try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
							catch (_: ActivityNotFoundException) { }
						}
						return true
					}
				}
				webChromeClient = WebChromeClient()
				tag = url
				// YouTube embeds reject clients that don't identify their embedding
				// app (Error 153). Android WebView does not provide a Referer for
				// this top-level navigation unless we set it explicitly.
				loadUrl(url, mapOf("Referer" to ONDO_YOUTUBE_REFERER))
			}
		},
		update = { view ->
			if (view.tag != url) {
				view.tag = url
				view.loadUrl(url, mapOf("Referer" to ONDO_YOUTUBE_REFERER))
			}
		},
		onRelease = { view ->
			if (holder[0] === view) holder[0] = null
			view.stopLoading()
			view.loadUrl("about:blank")
			view.onPause()
			view.removeAllViews()
			view.destroy()
		}
	)
}
