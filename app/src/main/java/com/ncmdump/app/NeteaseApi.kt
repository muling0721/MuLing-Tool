package com.ncmdump.app

import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云歌曲解析
 *
 * - 搜索：官方搜索接口
 * - 播放/下载：解析接口（按歌曲页面链接获取音频直链）
 * - 歌词：官方歌词接口
 */
object NeteaseApi {

    private const val SEARCH_API = "https://music.163.com/api/cloudsearch/pc?s="
    private const val LYRIC_API = "https://music.163.com/api/song/lyric?id="
    private const val PARSE_API = "https://api.yutangxiaowu.cn/api/music/Song_V1"
    private const val SONG_PAGE = "https://music.163.com/song?id="
    /** 当前音质：standard / lossless / hires / sky / jyeffect / jymaster */
    @Volatile
    var level: String = "standard"
    private const val REFERER = "https://music.163.com/"
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

    private val urlCache = HashMap<String, String>()

    /** 关键词搜索 */
    fun search(keyword: String): List<Song> {
        lastError = ""
        return try {
            val url = SEARCH_API + URLEncoder.encode(keyword, "UTF-8") + "&type=1&limit=30&offset=0"
            val json = JSONObject(httpGet(url))
            val result = json.optJSONObject("result")
            if (result == null) {
                lastError = json.optString("message", "code=${json.optInt("code")}")
                return emptyList()
            }
            val arr = result.optJSONArray("songs") ?: return emptyList()
            val list = ArrayList<Song>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val album = o.optJSONObject("al")
                list.add(
                    Song(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        artist = joinArtists(o.optJSONArray("ar")),
                        album = album?.optString("name", "").orEmpty(),
                        cover = normalizeCover(album?.optString("picUrl", "").orEmpty()),
                        durationMs = o.optLong("dt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            lastError = e.message ?: "未知错误"
            emptyList()
        }
    }

    /**
     * 解析真实音频直链（带内存缓存）。
     * 解析接口按歌曲页面链接工作，成功时返回 url 字段。
     */
    fun resolveDirectUrl(id: String): String? {
        val lv = level
        val key = "$id:$lv"
        synchronized(urlCache) { urlCache[key]?.let { return it } }
        return try {
            val page = SONG_PAGE + id
            val url = PARSE_API + "?url=" + URLEncoder.encode(page, "UTF-8") +
                "&level=" + lv + "&type=json"
            val json = JSONObject(httpGet(url))
            val status = json.optInt("status", -1)
            val playUrl = json.optString("url", "")
            if (status == 200 && playUrl.isNotBlank()) {
                synchronized(urlCache) { urlCache[key] = playUrl }
                playUrl
            } else {
                lastError = json.optString("msg", "解析失败（status=$status）")
                null
            }
        } catch (e: Exception) {
            lastError = e.message ?: "未知错误"
            null
        }
    }

    private fun joinArtists(arr: JSONArray?): String {
        if (arr == null) return ""
        val names = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val name = arr.optJSONObject(i)?.optString("name", "").orEmpty()
            if (name.isNotBlank()) names.add(name)
        }
        return names.joinToString(" / ")
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** 歌词（原文 + 翻译） */
    data class Lyric(val lrc: String, val translation: String) {
        val isEmpty: Boolean get() = lrc.isBlank() && translation.isBlank()

        /** 按时间戳把翻译合并到原文下一行 */
        fun merged(): String {
            val origin = lrc.trim()
            if (translation.isBlank()) return origin
            val timeRegex = Regex("^\\[(\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?)\\](.*)$")
            val transMap = translation.lineSequence()
                .mapNotNull { line ->
                    val m = timeRegex.find(line.trim())
                    if (m != null) m.groupValues[1] to m.groupValues[2].trim() else null
                }
                .toMap()
            if (transMap.isEmpty()) return origin
            return origin.lineSequence().joinToString("\n") { line ->
                val m = timeRegex.find(line.trim())
                if (m == null) {
                    line.trim()
                } else {
                    val tr = transMap[m.groupValues[1]]
                    val text = m.groupValues[2].trim()
                    if (!tr.isNullOrBlank() && text.isNotBlank()) "$text\n$tr" else line.trim()
                }
            }.trim()
        }
    }

    /** 获取歌词（网易云官方接口） */
    fun fetchLyric(id: String): Lyric? {
        return try {
            val json = JSONObject(httpGet("$LYRIC_API$id&lv=-1&tv=-1&rv=-1"))
            val lrc = json.optJSONObject("lrc")?.optString("lyric", "").orEmpty()
            val tl = json.optJSONObject("tlyric")?.optString("lyric", "").orEmpty()
            val lyric = Lyric(lrc, tl)
            if (lyric.isEmpty) null else lyric
        } catch (e: Exception) {
            null
        }
    }

    /** 保存 .lrc 到统一输出目录 */
    fun saveLyric(context: Context, song: Song, content: String): String? {
        return try {
            val f = OutputDir.file(context, fileNameFor(song, ".lrc"))
            f.writeText(content, Charsets.UTF_8)
            f.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** 统一的文件名（歌名 - 歌手 + 扩展名），过滤非法字符 */
    fun fileNameFor(song: Song, ext: String): String {
        val base = listOf(song.name, song.artist).filter { it.isNotBlank() }.joinToString(" - ")
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "netease_${song.id}" }
        return "$safe$ext"
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
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", REFERER)
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }
}
