package com.sigynvs.phonejanitor.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the latest GitHub Release for the project. Release tags are `v<versionCode>` (v2, v3, …);
 * the release carries the built `*.apk` as an asset. Unauthenticated — 60 requests/hour/IP is
 * plenty for a once-a-day check.
 */
class UpdateChecker(private val currentVersionCode: Int) {

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_LATEST).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PhoneJanitor-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return@withContext null

            val tag = obj.optString("tag_name")
            val latestCode = tag.trimStart('v', 'V').toIntOrNull() ?: return@withContext null
            if (latestCode <= currentVersionCode) return@withContext null

            val assets = obj.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    size = asset.optLong("size")
                    break
                }
            }
            val url = apkUrl ?: return@withContext null

            UpdateInfo(
                versionCode = latestCode,
                versionName = obj.optString("name").ifBlank { tag },
                notes = obj.optString("body").trim(),
                apkUrl = url,
                sizeBytes = size,
            )
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val RELEASES_LATEST =
            "https://api.github.com/repos/SigynVS/phone-janitor/releases/latest"
    }
}
