package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云歌曲解析（在线搜索 + 获取播放直链）
 *
 * 接口参考 reference/netease-music/：
 *  - 搜索：https://music.kxlove.top/api/WYY/wyy_search.php?word=关键词 → JSON
 *  - 播放：https://music.kxlove.top/api/WYY/wyy.php?id=歌曲ID       → 302 到音频直链
 */
object NeteaseApi {

    private const val SEARCH_API = "https://music.kxlove.top/api/WYY/wyy_search.php?word="
    private const val PLAY_API = "https://music.kxlove.top/api/WYY/wyy.php?id="
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    data class Song(
        val id: String,
        val name: String,
        val artist: String,
        val album: String,
        val cover: String,
        val durationMs: Long
    )

    var lastError: String = ""
        private set

    /** 关键词搜索 */
    fun search(keyword: String): List<Song> {
        lastError = ""
        return try {
            val url = SEARCH_API + URLEncoder.encode(keyword, "UTF-8")
            val json = JSONObject(httpGet(url))
            if (json.optInt("code", -1) != 200) {
                lastError = json.optString("message", "code=${json.optInt("code")}")
                return emptyList()
            }
            val arr = json.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<Song>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    Song(
                        id = o.optString("id", ""),
                        name = o.optString("song", ""),
                        artist = o.optString("singer", ""),
                        album = o.optString("album", ""),
                        cover = normalizeCover(o.optString("picUrl", "")),
                        durationMs = o.optLong("duration", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            lastError = e.message ?: "未知错误"
            emptyList()
        }
    }

    /** 可直接交给播放器的流地址（内部会 302 到真实音频） */
    fun streamUrl(id: String): String = PLAY_API + id

    /** 解析 302 后的真实音频直链（用于复制 / 下载） */
    fun resolveDirectUrl(id: String): String? {
        return try {
            val conn = URL(PLAY_API + id).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", UA)
            conn.connect()
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && !loc.isNullOrBlank()) loc else null
        } catch (e: Exception) {
            null
        }
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** 网易云 CDN 支持 ?param=WxH 取缩略图，减小列表内存占用 */
    private fun normalizeCover(raw: String): String {
        if (raw.isEmpty()) return ""
        var url = if (raw.startsWith("http://")) "https://" + raw.substring(7) else raw
        if (url.contains("music.126.net") && !url.contains("param=")) {
            url += if (url.contains("?")) "&param=120y120" else "?param=120y120"
        }
        return url
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
