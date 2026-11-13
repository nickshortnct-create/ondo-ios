package com.nickshortnct.ondo.ondo
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
actual fun decryptSaavnUrl(encrypted: String): String {
    val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("38346591".toByteArray(), "DES"))
    val url = String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)), Charsets.UTF_8)
    require(url.startsWith("https://") || url.startsWith("http://")) { "Invalid media URL" }
    return url.replace(Regex("_\\d+\\.mp4(\\?.*)?$"), "_320.mp4")
}
