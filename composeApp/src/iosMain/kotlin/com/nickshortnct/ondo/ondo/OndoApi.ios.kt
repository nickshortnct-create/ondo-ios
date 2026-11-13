package com.nickshortnct.ondo.ondo

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// iOS has no decknas-specific TLS trust problem to work around -- just forward to the
// normal top-level HttpClient(block), unchanged from before the Android actual existed.
actual fun ondoHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(block)
