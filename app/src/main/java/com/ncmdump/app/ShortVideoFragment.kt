package com.ncmdump.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.net.URL
import kotlin.concurrent.thread

/**
 * 短视频解析：抖音 / 快手 / TikTok
 * 解析逻辑见 ShortVideoApi，参考 reference/短视频解析/
 */
class ShortVideoFragment : Fragment() {

    private lateinit var etShortUrl: EditText
    private lateinit var btnShortParse: Button
    private lateinit var tvShortStatus: TextView
    private lateinit var cardShortResult: View
    private lateinit var ivShortCover: ImageView
    private lateinit var tvShortTitle: TextView
    private lateinit var tvShortAuthor: TextView
    private lateinit var btnPlayVideo: Button
    private lateinit var btnDownloadVideo: Button
    private lateinit var btnDownloadAll: Button
    private lateinit var btnCopyVideoUrl: Button

    private var current: ShortVideoApi.Result? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_short_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etShortUrl = view.findViewById(R.id.etShortUrl)
        btnShortParse = view.findViewById(R.id.btnShortParse)
        tvShortStatus = view.findViewById(R.id.tvShortStatus)
        cardShortResult = view.findViewById(R.id.cardShortResult)
        ivShortCover = view.findViewById(R.id.ivShortCover)
        tvShortTitle = view.findViewById(R.id.tvShortTitle)
        tvShortAuthor = view.findViewById(R.id.tvShortAuthor)
        btnPlayVideo = view.findViewById(R.id.btnPlayVideo)
        btnDownloadVideo = view.findViewById(R.id.btnDownloadVideo)
        btnDownloadAll = view.findViewById(R.id.btnDownloadAll)
        btnCopyVideoUrl = view.findViewById(R.id.btnCopyVideoUrl)

        btnShortParse.setOnClickListener { doParse() }
        etShortUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                doParse()
                true
            } else false
        }

        btnPlayVideo.setOnClickListener { current?.videoUrl?.let { play(it) } }
        btnDownloadVideo.setOnClickListener { current?.let { downloadVideo(it) } }
        btnDownloadAll.setOnClickListener { current?.let { downloadAllImages(it) } }
        btnCopyVideoUrl.setOnClickListener {
            val link = current?.videoUrl ?: current?.images?.firstOrNull()
            if (link != null) copy("视频直链", link) else toast("暂无可复制的链接")
        }
    }

    private fun doParse() {
        val input = etShortUrl.text.toString().trim()
        if (input.isEmpty()) {
            toast("请粘贴分享链接")
            return
        }
        val url = ShortVideoApi.extractUrl(input) ?: input
        when (ShortVideoApi.detectPlatform(url)) {
            ShortVideoApi.Platform.DOUYIN,
            ShortVideoApi.Platform.KUAISHOU,
            ShortVideoApi.Platform.TIKTOK -> Unit
            ShortVideoApi.Platform.UNKNOWN -> {
                tvShortStatus.text = "暂不支持该链接（仅支持抖音 / 快手 / TikTok；B站请用「视频处理」）"
                return
            }
        }

        tvShortStatus.text = "正在解析…"
        cardShortResult.visibility = View.GONE
        btnShortParse.isEnabled = false
        val token = ++parseToken
        thread {
            val result = ShortVideoApi.parse(url)
            activity?.runOnUiThread {
                if (token != parseToken) return@runOnUiThread
                btnShortParse.isEnabled = true
                if (result == null) {
                    tvShortStatus.text = ShortVideoApi.lastError.ifEmpty { "解析失败" }
                } else {
                    current = result
                    showResult(result)
                }
            }
        }
    }

    private var parseToken = 0

    private fun showResult(result: ShortVideoApi.Result) {
        cardShortResult.visibility = View.VISIBLE
        tvShortTitle.text = result.title.ifBlank { "无标题" }
        tvShortAuthor.text = buildString {
            append(result.platform.label)
            if (result.author.isNotBlank()) append(" · ").append(result.author)
            if (result.isGallery) append(" · 图集 ").append(result.images.size).append(" 张")
        }

        val isGallery = result.isGallery
        btnPlayVideo.visibility = if (isGallery) View.GONE else View.VISIBLE
        btnDownloadVideo.visibility = if (isGallery) View.GONE else View.VISIBLE
        btnDownloadAll.visibility = if (isGallery) View.VISIBLE else View.GONE

        loadCover(result.cover ?: result.images.firstOrNull())
        tvShortStatus.text = if (isGallery) "解析成功（图集）" else "解析成功"
    }

    private fun play(videoUrl: String) {
        try {
            startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("video_url", videoUrl)
                putExtra("video_title", current?.title.orEmpty())
            })
        } catch (e: Exception) {
            toast("播放失败：${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadVideo(result: ShortVideoApi.Result) {
        val url = result.videoUrl ?: return
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(result.title.ifBlank { result.platform.label + "视频" })
                .setMimeType("video/mp4")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, ShortVideoApi.fileName(result))
            enqueue(request)
            toast("已加入下载")
        } catch (e: Exception) {
            toast("下载失败：${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadAllImages(result: ShortVideoApi.Result) {
        if (result.images.isEmpty()) return
        try {
            result.images.forEachIndexed { index, img ->
                val name = "${result.platform.label}_${System.currentTimeMillis()}_$index.jpg"
                val request = DownloadManager.Request(Uri.parse(img))
                    .setTitle(name)
                    .setMimeType("image/jpeg")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, name)
                enqueue(request)
            }
            toast("已加入 ${result.images.size} 张图片下载")
        } catch (e: Exception) {
            toast("下载失败：${e.message}")
        }
    }

    private fun enqueue(request: DownloadManager.Request) {
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }

    private fun copy(label: String, text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("已复制$label")
    }

    private fun loadCover(url: String?) {
        ivShortCover.tag = url
        ivShortCover.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        thread {
            val bmp = try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                activity?.runOnUiThread {
                    if (ivShortCover.tag == url) ivShortCover.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun toast(msg: String) {
        activity?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }
}
