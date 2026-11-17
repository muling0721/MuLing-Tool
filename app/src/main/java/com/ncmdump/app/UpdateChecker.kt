package com.ncmdump.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检测：只做网络请求与版本比较，UI 由 Compose 页面负责。
 */
object UpdateChecker {

    const val GITHUB_URL = "https://github.com/muling0721/MuLing-Tool"

    private const val RELEASE_URL = "https://api.github.com/repos/muling0721/MuLing-Tool/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String
    )

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(RELEASE_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "MuLing-Tool")
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                ReleaseInfo(
                    tagName = json.optString("tag_name", ""),
                    name = json.optString("name", ""),
                    body = json.optString("body", ""),
                    htmlUrl = json.optString("html_url", GITHUB_URL + "/releases/latest")
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
