package com.nickshortnct.ondo.di

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal class AndroidApkUpdater(context: Context) : AppUpdater {
    private val context = context.applicationContext
    private val downloadMutex = Mutex()
    private val file get() = File(context.cacheDir, "updates/ondo-latest.apk")
    private val part get() = File(context.cacheDir, "updates/ondo-latest.apk.part")
    @Suppress("DEPRECATION")
    private val flags get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    override val installedVersionCode get() = context.packageManager.getPackageInfo(context.packageName, 0).code()

    init {
        val cached = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (cached != null && cached.code() <= installedVersionCode) { file.delete(); part.delete() }
    }

    override suspend fun download(url: String, expectedVersionCode: Long): Boolean = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            require(URL(url).let { it.protocol == "https" && it.host == "github.com" }) { "Update URL must be hosted by GitHub" }
            if (isValidApk(file, expectedVersionCode)) return@withContext true
            file.delete(); part.delete()
            file.parentFile?.mkdirs()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 120_000; instanceFollowRedirects = true
            }
            try {
                connection.connect()
                check(connection.responseCode in 200..299) { "APK download failed (HTTP ${connection.responseCode})" }
                connection.inputStream.use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                } }
                check(isValidApk(part, expectedVersionCode)) { "Downloaded APK does not match the selected Ondo release" }
                check(part.renameTo(file)) { "Could not save the downloaded update" }
                true
            } finally { connection.disconnect(); part.delete() }
        }
    }

    override fun install(expectedVersionCode: Long): UpdateInstallResult {
        if (!isValidApk(file, expectedVersionCode)) return UpdateInstallResult.Unavailable
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return UpdateInstallResult.PermissionRequired
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return UpdateInstallResult.Launched
    }

    private fun isValidApk(candidateFile: File, expected: Long): Boolean {
        if (!candidateFile.isFile || candidateFile.length() == 0L) return false
        val candidate = context.packageManager.getPackageArchiveInfo(candidateFile.absolutePath, flags) ?: return false
        if (candidate.packageName != context.packageName || !isExpectedUpdateVersion(candidate.code(), installedVersionCode, expected)) return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        check(signers(candidate).isNotEmpty() && signers(candidate) == signers(installed)) { "This update was signed with a different key" }
        return true
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().map { it.toCharsString() }.toSet()

    @Suppress("DEPRECATION")
    private fun PackageInfo.code() = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
}
