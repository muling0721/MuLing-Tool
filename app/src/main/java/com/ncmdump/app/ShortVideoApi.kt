package com.ncmdump.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// 短视频解析（抖音 / 快手）
//
// 使用「羽棠小屋」聚合解析接口：
// GET https://api.yutangxiaowu.cn/api/v1/mix/video?url=分享链接
//
// 抖音返回 title / author / image / video(file_url) 结构；
// 快手返回 code / msg / data(video.url) 结构，App 均已兼容。
object ShortVideoApi {

    private const val API = "https://api.yutangxiaowu.cn/api/v1/mix/video?url="
    /** 播放/下载时需携带的 User-Agent */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"

    /** 各平台 CDN 要求的 Referer */
    fun refererFor(platform: Platform): String = when (platform) {
        Platform.DOUYIN -> "https://www.douyin.com/"
        Platform.KUAISHOU -> "https://www.kuaishou.com/"
        Platform.UNKNOWN -> ""
    }

    enum class Platform(val label: String) {
        DOUYIN("抖音"),
        KUAISHOU("快手"),
        UNKNOWN("短视频")
    }

    data class Result(
        val platform: Platform,
        val title: String,
        val author: String,
        val cover: String?,
        val videoUrl: String?,
        val images: List<String>
    ) {
        val isGallery: Boolean get() = videoUrl.isNullOrBlank() && images.isNotEmpty()
    }

    var lastError: String = ""
        private set

    private val urlRegex = Regex("""https?://[\w\-./?=&%~:#@!$'()*+,;]+""")

    fun extractUrl(text: String): String? = urlRegex.find(text)?.value

    fun detectPlatform(url: String): Platform = when {
        url.contains("douyin.com") || url.contains("iesdouyin.com") -> Platform.DOUYIN
        url.contains("kuaishou.com") || url.contains("gifshow.com") ||
            url.contains("chenzhongtech.com") -> Platform.KUAISHOU
        else -> Platform.UNKNOWN
    }

    fun isSupported(url: String): Boolean = detectPlatform(url) != Platform.UNKNOWN

    fun parse(input: String): Result? {
        lastError = ""
        val url = extractUrl(input) ?: input.trim()
        if (url.isEmpty()) {
            lastError = "未识别到链接"
            return null
        }
        val platform = detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            lastError = "仅支持抖音 / 快手分享链接"
            return null
        }

        val body = httpGet(API + URLEncoder.encode(url, "UTF-8"))
        if (body.isNullOrBlank()) {
            lastError = "网络请求失败"
            return null
        }
        val o = try {
            JSONObject(body)
        } catch (e: Exception) {
            lastError = "返回数据异常"
            return null
        }

        // 抖音结构
        if (o.has("video") || o.has("author")) {
            val title = o.optString("title")
            val author = o.optJSONObject("author")?.optString("name").orEmpty()
            val images = jsonArrayToStrings(o.optJSONArray("image"))
            val bestUrl = bestDouyinVideo(o.optJSONArray("video"))
            val cover = images.firstOrNull()
            if (!bestUrl.isNullOrBlank()) {
                return Result(platform, title, author, cover, bestUrl, emptyList())
            }
            if (images.isNotEmpty()) {
                return Result(platform, title, author, cover, null, images)
            }
            lastError = "未解析到视频"
            return null
        }

        // 快手 / 统一结构
        if (o.optInt("code", -1) == 0) {
            val d = o.optJSONObject("data")
            if (d == null) {
                lastError = "返回数据异常"
                return null
            }
            val video = d.optJSONObject("video")
            val title = video?.optString("title").orEmpty().ifBlank { d.optString("songName") }
            val author = d.optJSONObject("owner")?.optString("name").orEmpty()
            val videoUrl = video?.optString("url").orEmpty()
            val cover = video?.optString("fm").orEmpty().ifBlank { null }
            if (videoUrl.isNotBlank()) {
                return Result(platform, title, author, cover, videoUrl, emptyList())
            }
            val imgs = ArrayList<String>()
            jsonArrayToStrings(d.optJSONArray("image")).let { imgs.addAll(it) }
            jsonArrayToStrings(d.optJSONArray("images")).let { imgs.addAll(it) }
            if (imgs.isNotEmpty()) {
                return Result(platform, title, author, imgs.first(), null, imgs)
            }
            lastError = "未解析到视频"
            return null
        }

        lastError = o.optString("msg").ifBlank { "解析失败" }
        return null
    }

    /** 抖音会返回多档清晰度，取文件最大的一档 */
    private fun bestDouyinVideo(videos: JSONArray?): String? {
        if (videos == null) return null
        var best: String? = null
        var bestSize = -1L
        for (i in 0 until videos.length()) {
            val v = videos.optJSONObject(i) ?: continue
            val size = v.optLong("file_size", 0L)
            val u = v.optJSONArray("file_url")?.optString(0).orEmpty()
            if (u.isNotBlank() && size >= bestSize) {
                bestSize = size
                best = u
            }
        }
        return best
    }

    private fun jsonArrayToStrings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val list = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            a.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
        }
        return list
    }

    fun fileName(result: Result): String {
        val base = result.title.ifBlank {
            result.videoUrl?.substringAfterLast('/')?.substringBefore('?').orEmpty()
        }
        val safe = base.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60)
            .ifBlank { "shortvideo_${System.currentTimeMillis()}" }
        return "${result.platform.label}_$safe.mp4"
    }

    private fun httpGet(u: String): String? {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
