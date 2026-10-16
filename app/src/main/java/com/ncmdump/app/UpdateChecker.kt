package com.ncmdump.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {

    private const val RELEASE_URL = "https://api.github.com/repos/muling0721/MuLing-Tool/releases/latest"
    private const val DOWNLOAD_URL = "https://github.com/muling0721/MuLing-Tool/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String
    )

    fun checkUpdate(context: Context, showNoUpdate: Boolean = false) {
        val activity = context as? Activity
        thread {
            try {
                val release = fetchLatestRelease()
                if (release != null) {
                    val latestVersion = release.tagName.removePrefix("v")
                    val currentVersion = getCurrentVersion(context)
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        activity?.runOnUiThread {
                            showUpdateDialog(activity, currentVersion, release)
                        }
                    } else if (showNoUpdate) {
                        activity?.runOnUiThread {
                            showInfoDialog(
                                activity,
                                "已是最新版本",
                                "v$currentVersion",
                                "当前已是最新版本，无需更新。"
                            )
                        }
                    }
                } else if (showNoUpdate) {
                    activity?.runOnUiThread {
                        showInfoDialog(
                            activity,
                            "检查更新失败",
                            null,
                            "无法获取更新信息，请检查网络连接后重试。"
                        )
                    }
                }
            } catch (e: Exception) {
                if (showNoUpdate) {
                    activity?.runOnUiThread {
                        showInfoDialog(activity, "检查更新失败", null, e.message ?: "未知错误")
                    }
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val url = URL(RELEASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Analysis-Tool-App")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                ReleaseInfo(
                    tagName = json.optString("tag_name", ""),
                    name = json.optString("name", ""),
                    body = json.optString("body", ""),
                    htmlUrl = json.optString("html_url", DOWNLOAD_URL)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun showUpdateDialog(activity: Activity, currentVersion: String, release: ReleaseInfo) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvDialogTitle).text = "发现新版本"
        view.findViewById<TextView>(R.id.tvDialogSubtitle).text =
            "v$currentVersion  →  ${release.tagName}"
        view.findViewById<TextView>(R.id.tvDialogBody).text =
            release.body.ifBlank { "有新版本可用，建议更新以获得最新修复与改进。" }

        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton("立即更新") { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun showInfoDialog(activity: Activity, title: String, subtitle: String?, message: String) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvDialogTitle).text = title
        view.findViewById<TextView>(R.id.tvDialogSubtitle).apply {
            if (subtitle.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                text = subtitle
            }
        }
        view.findViewById<TextView>(R.id.tvDialogBody).text = message

        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton("好的", null)
            .show()
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".", "-").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".", "-").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            latest != current
        }
    }
}
