package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 短视频解析（抖音 / 快手 / TikTok）
 *
 * 参考 reference/短视频解析/，多解析源依次回退：
 *  1) video-crack:  http://47.99.158.118/video-crack/v2/parse?content=<url>
 *  2) guiguiya 聚合: http://api.guiguiya.com/api/video_qsy/juhe?url=<url>
 *  3) tangdouz:      https://api.tangdouz.com/dy.php?lj=<url>
 *  4) douyin.wtf:    https://douyin.wtf/api/hybrid/video_data?url=<url>&minimal=true
 *
 * 注意：以上均为第三方公共服务，可能失效或变更。
 */
object ShortVideoApi {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0.4472.120 Mobile Safari/537.36"

    enum class Platform(val label: String) {
        DOUYIN("抖音"),
        KUAISHOU("快手"),
        TIKTOK("TikTok"),
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
        url.contains("tiktok.com") -> Platform.TIKTOK
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
        val enc = URLEncoder.encode(url, "UTF-8")

        // 源 1
        runCatching {
            val body = httpGet("http://47.99.158.118/video-crack/v2/parse?content=$enc")
            if (body != null) {
                val o = JSONObject(body)
                if (o.optString("code") == "0") {
                    val d = o.optJSONObject("data")
                    if (d != null) {
                        val v = d.optString("url", "")
                        if (v.isNotBlank() && !v.contains(".mp3")) {
                            return Result(platform, d.optString("title"), d.optString("author"),
                                d.optString("cover").ifBlank { null }, v, emptyList())
                        }
                        val imgs = stringArray(d, "imageUrl")
                        if (imgs.isNotEmpty()) {
                            return Result(platform, d.optString("title"), d.optString("author"),
                                imgs.first(), null, imgs)
                        }
                    }
                }
            }
        }

        // 源 2
        runCatching {
            val body = httpGet("http://api.guiguiya.com/api/video_qsy/juhe?url=$enc")
            if (body != null) {
                val o = JSONObject(body)
                if (o.optString("code") == "200") {
                    val d = o.optJSONObject("data") ?: o
                    val v = d.optString("url", "")
                    if (v.isNotBlank() && !v.contains(".mp3")) {
                        return Result(platform, d.optString("title"), d.optString("author"),
                            d.optString("cover").ifBlank { null }, v, emptyList())
                    }
                }
            }
        }

        // 源 3
        runCatching {
            val raw = httpGet("https://api.tangdouz.com/dy.php?lj=$enc")?.trim().orEmpty()
            if (raw.startsWith("http") && raw.length < 500 && !raw.contains("±")) {
                return Result(platform, "", "", null, raw, emptyList())
            }
            if (raw.contains("±img=")) {
                val imgs = raw.split("±").mapNotNull { part ->
                    part.trim().removePrefix("img=").trim().takeIf { it.startsWith("http") }
                }
                if (imgs.isNotEmpty()) {
                    return Result(platform, "", "", imgs.first(), null, imgs)
                }
            }
        }

        // 源 4（TikTok / 部分抖音，需公共实例可用）
        runCatching {
            val body = httpGet("https://douyin.wtf/api/hybrid/video_data?url=$enc&minimal=true")
            if (body != null) {
                val o = JSONObject(body)
                if (o.optString("code") == "200") {
                    val d = o.optJSONObject("data")
                    if (d != null) {
                        val v = sequenceOf(
                            "nwm_video_url_HQ", "nwm_video_url", "wm_video_url_HQ", "wm_video_url"
                        ).map { d.optString(it, "") }.firstOrNull { it.isNotBlank() }
                        if (v != null) {
                            return Result(platform, d.optString("desc"), d.optString("author"),
                                d.optString("cover_data").ifBlank { null }, v, emptyList())
                        }
                    }
                }
            }
        }

        lastError = "解析失败：接口可能已失效，或该视频无版权/不支持"
        return null
    }

    /** 下载文件名 */
    fun fileName(result: Result): String {
        val base = result.title.ifBlank {
            result.videoUrl?.substringAfterLast('/')?.substringBefore('?').orEmpty()
        }
        val safe = base.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60)
            .ifBlank { "shortvideo_${System.currentTimeMillis()}" }
        return "${result.platform.label}_$safe.mp4"
    }

    private fun stringArray(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        val list = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            a.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
        }
        return list
    }

    private fun httpGet(u: String): String? {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA)
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
