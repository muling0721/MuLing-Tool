package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * 哔哩哔哩解析：使用羽棠小屋 combinedVideo 接口。
 * 一次请求同时拿到视频信息和可下载的合并流地址。
 */
object BiliParser {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    private const val API = "https://api.yutangxiaowu.cn/api/combinedVideo"

    private val BV_PATTERN = Pattern.compile("(?i)(BV[0-9A-Za-z]{10})")
    private val AV_PATTERN = Pattern.compile("(?i)(?<![0-9A-Za-z])av(\\d+)(?![0-9A-Za-z])")
    private val B23_PATTERN = Pattern.compile("https?://b23\\.tv/[0-9A-Za-z]+", Pattern.CASE_INSENSITIVE)
    private val BILI_URL_PATTERN =
        Pattern.compile("https?://(?:www\\.|m\\.)?bilibili\\.com/video/[^\\s]+", Pattern.CASE_INSENSITIVE)

    var lastError: String = ""
        private set

    data class VideoInfo(
        val bvid: String,
        val aid: Long,
        val title: String,
        val desc: String,
        val owner: String,
        val cover: String,
        val duration: Int,
        val cid: Long,
        val pages: List<PageInfo>
    )

    data class PageInfo(
        val cid: Long,
        val page: Int,
        val part: String,
        val duration: Int
    )

    data class PlayUrl(
        val videoUrl: String,
        val audioUrl: String,
        val quality: Int,
        val qualityDesc: String
    )

    private var cachedBvid: String? = null
    private var cachedPlay: PlayUrl? = null

    fun extractBvid(input: String): String? {
        val bv = BV_PATTERN.matcher(input)
        if (bv.find()) return bv.group(1)

        val av = AV_PATTERN.matcher(input)
        if (av.find()) return "av" + av.group(1)

        val b23 = B23_PATTERN.matcher(input)
        if (b23.find()) {
            getRedirectUrl(b23.group())?.let { redirect ->
                extractBvid(redirect)?.let { return it }
            }
        }

        val bili = BILI_URL_PATTERN.matcher(input)
        if (bili.find()) return extractBvid(bili.group())

        return null
    }

    fun getVideoInfo(input: String): VideoInfo? {
        lastError = ""
        val url = urlFor(input) ?: run {
            lastError = "无法识别BV/AV号"
            return null
        }

        return try {
            val body = httpGet("$API?url=" + URLEncoder.encode(url, "UTF-8")) ?: run {
                lastError = "网络请求失败"
                return null
            }
            val json = JSONObject(body)
            val playInfo = json.optJSONObject("playInfo")
            val data = playInfo?.optJSONObject("data")
            if (data == null) {
                lastError = json.optString(
                    "error",
                    playInfo?.optString("message").orEmpty().ifEmpty { "解析失败" }
                )
                return null
            }

            val bvid = data.optString("bvid", json.optString("bvid", ""))
            val pages = ArrayList<PageInfo>()
            data.optJSONArray("pages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    pages.add(
                        PageInfo(
                            cid = p.optLong("cid", 0L),
                            page = p.optInt("page", i + 1),
                            part = p.optString("part", ""),
                            duration = p.optInt("duration", 0)
                        )
                    )
                }
            }

            val info = VideoInfo(
                bvid = bvid,
                aid = data.optLong("aid", 0L),
                title = data.optString("title", ""),
                desc = data.optString("desc", ""),
                owner = data.optJSONObject("owner")?.optString("name", "").orEmpty(),
                cover = normalizeUrl(data.optString("pic", "")),
                duration = data.optInt("duration", 0),
                cid = data.optLong("cid", 0L),
                pages = pages
            )

            val basic = json.optJSONObject("basicInfo")?.optJSONObject("data")
            val durl = basic?.optJSONArray("durl")
            val videoUrl = if (durl != null && durl.length() > 0) {
                durl.optJSONObject(0)?.optString("url", "").orEmpty()
            } else {
                ""
            }
            val quality = basic?.optInt("quality", 0) ?: 0
            val qualityDesc = basic?.optJSONArray("accept_description")?.optString(0, "").orEmpty()

            cachedBvid = bvid
            cachedPlay = PlayUrl(videoUrl, "", quality, qualityDesc)

            info
        } catch (e: Exception) {
            lastError = e.message ?: "解析失败"
            null
        }
    }

    fun getPlayUrl(bvid: String, cid: Long, qn: Int = 64): PlayUrl? {
        cachedPlay?.let { play ->
            if (cachedBvid == bvid && play.videoUrl.isNotEmpty()) return play
        }
        getVideoInfo("https://www.bilibili.com/video/$bvid") ?: return null
        return cachedPlay?.takeIf { it.videoUrl.isNotEmpty() }
    }

    fun getDirectPlayUrl(bvid: String, cid: Long, qn: Int = 64): PlayUrl? =
        getPlayUrl(bvid, cid, qn)

    private fun urlFor(input: String): String? {
        B23_PATTERN.matcher(input).let { if (it.find()) return it.group() }
        BILI_URL_PATTERN.matcher(input).let { if (it.find()) return it.group() }
        BV_PATTERN.matcher(input).let { if (it.find()) return "https://www.bilibili.com/video/${it.group(1)}" }
        AV_PATTERN.matcher(input).let {
            if (it.find()) return "https://www.bilibili.com/video/av${it.group(1)}"
        }
        return null
    }

    private fun normalizeUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://")) "https://" + raw.substring(7) else raw
    }

    private fun getRedirectUrl(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", UA)
            conn.connect()
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            location
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun formatDuration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
