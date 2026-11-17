package com.ncmdump.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 下载记录：保存最近下载过的文件（标题、路径、时间、类型）。
 * 使用 SharedPreferences 存储 JSON，最多保留 200 条。
 */
object DownloadHistory {

    private const val PREF = "download_history"
    private const val MAX_ITEMS = 200

    data class Entry(
        val title: String,
        val path: String,
        val time: Long,
        val kind: String
    ) {
        val fileName: String get() = path.substringAfterLast('/')
    }

    fun add(context: Context, title: String, path: String) {
        if (path.isBlank()) return
        val list = load(context).toMutableList()
        list.removeAll { it.path == path }
        list.add(0, Entry(title.ifBlank { path.substringAfterLast('/') }, path, System.currentTimeMillis(), kindOf(path)))
        save(context, list.take(MAX_ITEMS))
    }

    fun load(context: Context): List<Entry> {
        val raw = prefs(context).getString(PREF, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                Entry(
                    title = o.optString("title", ""),
                    path = o.optString("path", ""),
                    time = o.optLong("time", 0L),
                    kind = o.optString("kind", "")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun remove(context: Context, path: String) {
        save(context, load(context).filterNot { it.path == path })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(PREF).apply()
    }

    private fun save(context: Context, list: List<Entry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("title", entry.title)
                    .put("path", entry.path)
                    .put("time", entry.time)
                    .put("kind", entry.kind)
            )
        }
        prefs(context).edit().putString(PREF, array.toString()).apply()
    }

    private fun kindOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "webm", "mkv", "mov", "flv", "avi" -> "视频"
        "mp3", "flac", "aac", "m4a", "ogg", "wav", "opus", "lrc" -> "音频"
        else -> "文件"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
}
