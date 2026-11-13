package com.nickshortnct.ondo.ondo

private const val ENCRYPTED_PREFIX = "enc:v1:"

/**
 * Encrypts small secrets (service API keys, tokens) before they are written to preferences.
 * The key lives in the platform key store and never leaves the device, so a copied
 * preferences file or backup does not expose the secret.
 */
expect object SecretStore {
	/** Returns an opaque string starting with [ENCRYPTED_PREFIX], or throws if encryption is unavailable. */
	fun encrypt(plain: String): String

	/** Reverses [encrypt]; returns null if the key was invalidated or the data is corrupt. */
	fun decrypt(token: String): String?
}

/** Stored form of [secret]: blank stays blank; encryption failure leaves the previous preference intact. */
fun protectSecret(secret: String): String {
	if (secret.isBlank() || secret.startsWith(ENCRYPTED_PREFIX)) return secret
	return runCatching { SecretStore.encrypt(secret) }.getOrElse {
		throw IllegalStateException("Could not securely save the API key. Your previous settings were kept. Try saving again.", it)
	}
}

/** Plain form of a stored value. Legacy plaintext values pass through and are encrypted on the next save. */
fun revealSecret(stored: String): String =
	if (stored.startsWith(ENCRYPTED_PREFIX)) SecretStore.decrypt(stored).orEmpty() else stored
