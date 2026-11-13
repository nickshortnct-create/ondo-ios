package com.nickshortnct.ondo.ondo

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "ondo_secret_store_v1"
private const val PREFIX = "enc:v1:"
private const val GCM_TAG_BITS = 128
private const val IV_BYTES = 12

actual object SecretStore {
	private fun key(): SecretKey {
		val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		(keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
		generator.init(
			KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build()
		)
		return generator.generateKey()
	}

	actual fun encrypt(plain: String): String {
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, key())
		val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
		return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
	}

	actual fun decrypt(token: String): String? = try {
		val payload = Base64.decode(token.removePrefix(PREFIX), Base64.NO_WRAP)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_BYTES))
		String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
	} catch (e: Exception) {
		null
	}
}
