package com.ncmdump.app

import org.json.JSONObject
import android.content.Context
import android.os.Environment
import java.io.File
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
    private const val LYRIC_API = "https://music.163.com/api/song/lyric?id="
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

    /** 保存 .lrc：优先公共音乐目录，失败则退回应用专属目录 */
    fun saveLyric(context: Context, song: Song, content: String): String? {
        val fileName = fileNameFor(song, ".lrc")
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName)
            f.writeText(content, Charsets.UTF_8)
            return f.absolutePath
        } catch (_: Exception) {
        }
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val f = File(dir, fileName)
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
