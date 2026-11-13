package com.nickshortnct.ondo.shared

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Nick's own apps that may browse and play Ondo's library, like Android Auto does. An app counts
 * only when its package name AND its signing certificate match, so another app cannot claim the
 * name. Publix Maps shows Ondo as a mini player on the map.
 */
object PartnerApps {
	const val PUBLIX_MAPS = "com.nicknas.publixmaps"

	/** SHA-256 of the Publix Maps release certificate. */
	private val PUBLIX_MAPS_CERT = hex("ea5742876fd2ec1cb05d2c9d214b09d562744c27cb5e21c5a7064ca988e41219")

	fun isTrusted(context: Context, packageName: String): Boolean {
		if (packageName != PUBLIX_MAPS || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
		return runCatching {
			context.packageManager.hasSigningCertificate(packageName, PUBLIX_MAPS_CERT, PackageManager.CERT_INPUT_SHA256)
		}.getOrDefault(false)
	}

	private fun hex(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
