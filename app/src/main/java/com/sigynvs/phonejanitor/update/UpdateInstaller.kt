package com.sigynvs.phonejanitor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads an update APK to cache and hands it to the system package installer. */
class UpdateInstaller(context: Context) {

    private val appContext = context.applicationContext

    suspend fun download(apkUrl: String, sizeHint: Long, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "phone-janitor-update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "PhoneJanitor-Updater")
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            try {
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: sizeHint
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readSoFar = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            readSoFar += n
                            if (total > 0) {
                                onProgress((readSoFar.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            out
        }

    fun launchInstall(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun canInstallPackages(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", appContext.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
