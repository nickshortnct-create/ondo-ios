package com.nickshortnct.ondo.ondo

// Keychain-backed storage is not wired up for iOS (the iOS targets do not build yet, see
// BLD-1). Secrets are therefore kept unencrypted there.
actual object SecretStore {
	actual fun encrypt(plain: String): String = throw UnsupportedOperationException("No secure store on this platform")
	actual fun decrypt(token: String): String? = null
}
