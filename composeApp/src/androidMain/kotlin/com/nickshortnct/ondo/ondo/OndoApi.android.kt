package com.nickshortnct.ondo.ondo

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

// Let's Encrypt's ISRG Root X2 (self-signed, valid 2020-2040). decknas.duckdns.org's
// ECDSA chain (leaf -> YE1 -> "Root YE", cross-signed up through ISRG Root X1) chains
// up to this root, and some Android trust stores don't ship it yet -- that's what was
// throwing `CertPathValidatorException: Trust anchor for certification path not found`
// for Lidarr/SoulSync/Squid/YouTube on decknas.
//
// 907 tried pinning this same root via a network_security_config.xml domain-config
// instead -- confirmed byte-correct in the built APK, but didn't fix the error on a
// real device, AND once shipped it broke every *other* host (OkHttp refuses to use a
// custom, non-Android-internal trust manager on any connection once a domain-config
// exists at all -- see network_security_config.xml's comment). So this is the only
// copy of the cert now: trusted directly at the OkHttp engine level, unconditionally,
// for every host (falling back to it only when the platform's own trust check fails --
// see CompositeTrustManager below), no domain-config involved.
private const val ISRG_ROOT_X2_PEM = """-----BEGIN CERTIFICATE-----
MIICGzCCAaGgAwIBAgIQQdKd0XLq7qeAwSxs6S+HUjAKBggqhkjOPQQDAzBPMQsw
CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg
R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw00
MDA5MTcxNjAwMDBaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5ldCBT
ZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgyMHYw
EAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW
+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9
ItgKbppbd9/w+kHsOdx1ymgHDB/qo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0T
AQH/BAUwAwEB/zAdBgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwCgYIKoZI
zj0EAwMDaAAwZQIwe3lORlCEwkSHRhtFcP9Ymd70/aTSVaYgLXTWNLxBo1BfASdW
tL4ndQavEi51mI38AjEAi/V3bNTIZargCyzuFJ0nN6T5U6VR5CmD1/iQMVtCnwr1
/q4AaOeMSQ+2b1tbFfLn
-----END CERTIFICATE-----"""

/**
 * Tries [platform]'s normal (system + user CA) trust first so every other host keeps
 * default Android trust behavior; only falls back to [extra] (our pinned root) when the
 * platform check fails, and only surfaces the platform's own failure if both reject it.
 */
private class CompositeTrustManager(
	private val platform: X509TrustManager,
	private val extra: X509TrustManager,
) : X509TrustManager {
	override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
		platform.checkClientTrusted(chain, authType)

	override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
		try {
			platform.checkServerTrusted(chain, authType)
		} catch (platformFailure: Exception) {
			try {
				extra.checkServerTrusted(chain, authType)
			} catch (_: Exception) {
				throw platformFailure
			}
		}
	}

	override fun getAcceptedIssuers(): Array<X509Certificate> =
		platform.acceptedIssuers + extra.acceptedIssuers
}

private fun platformTrustManager(): X509TrustManager =
	TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
		.apply { init(null as KeyStore?) }
		.trustManagers
		.filterIsInstance<X509TrustManager>()
		.first()

private fun trustManagerFor(cert: X509Certificate): X509TrustManager {
	val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
		load(null, null)
		setCertificateEntry("decknas-isrg-root-x2", cert)
	}
	return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
		.apply { init(keyStore) }
		.trustManagers
		.filterIsInstance<X509TrustManager>()
		.first()
}

actual fun ondoHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
	val isrgRootX2 = CertificateFactory.getInstance("X.509")
		.generateCertificate(ByteArrayInputStream(ISRG_ROOT_X2_PEM.toByteArray())) as X509Certificate
	val trustManager = CompositeTrustManager(platformTrustManager(), trustManagerFor(isrgRootX2))
	val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
	return HttpClient(OkHttp) {
		engine { config { sslSocketFactory(sslContext.socketFactory, trustManager) } }
		block()
	}
}
