package com.ncmdump.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlin.concurrent.thread
import java.net.URL

class BiliActivity : BaseActivity() {

    private lateinit var etBvid: EditText
    private lateinit var btnParse: Button
    private lateinit var btnGetPlayUrl: Button
    private lateinit var btnPlayInline: Button
    private lateinit var btnCopyVideoUrl: Button
    private lateinit var btnCopyAudioUrl: Button
    private lateinit var tvBiliStatus: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvOwner: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvPages: TextView
    private lateinit var ivCover: ImageView
    private lateinit var cardVideoInfo: CardView
    private lateinit var cardPages: CardView

    private var currentVideoInfo: BiliParser.VideoInfo? = null
    private var currentPlayUrl: BiliParser.PlayUrl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bili)

        setSupportActionBar(findViewById(R.id.topAppBar))
        supportActionBar?.title = "B站视频解析"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etBvid = findViewById(R.id.etBvid)
        btnParse = findViewById(R.id.btnParse)
        btnGetPlayUrl = findViewById(R.id.btnGetPlayUrl)
        btnPlayInline = findViewById(R.id.btnPlayInline)
        btnCopyVideoUrl = findViewById(R.id.btnCopyVideoUrl)
        btnCopyAudioUrl = findViewById(R.id.btnCopyAudioUrl)
        tvBiliStatus = findViewById(R.id.tvBiliStatus)
        tvTitle = findViewById(R.id.tvTitle)
        tvOwner = findViewById(R.id.tvOwner)
        tvDuration = findViewById(R.id.tvDuration)
        tvDesc = findViewById(R.id.tvDesc)
        tvPages = findViewById(R.id.tvPages)
        ivCover = findViewById(R.id.ivCover)
        cardVideoInfo = findViewById(R.id.cardVideoInfo)
        cardPages = findViewById(R.id.cardPages)

        btnParse.setOnClickListener {
            val input = etBvid.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入BV号或链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseVideo(input)
        }

        btnGetPlayUrl.setOnClickListener {
            currentVideoInfo?.let { info ->
                fetchPlayUrl(info.bvid, info.cid)
            }
        }

        btnPlayInline.setOnClickListener {
            currentVideoInfo?.let { info ->
                playInline(info.bvid, info.cid, info.title)
            }
        }

        btnCopyVideoUrl.setOnClickListener {
            currentPlayUrl?.let {
                copyToClipboard("视频流地址", it.videoUrl)
            }
        }

        btnCopyAudioUrl.setOnClickListener {
            currentPlayUrl?.let {
                if (it.audioUrl.isNotEmpty()) {
                    copyToClipboard("音频流地址", it.audioUrl)
                } else {
                    Toast.makeText(this, "该视频无独立音频流", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun parseVideo(input: String) {
        tvBiliStatus.text = "正在解析..."
        btnParse.isEnabled = false
        cardVideoInfo.visibility = CardView.GONE
        cardPages.visibility = CardView.GONE

        thread {
            val info = BiliParser.getVideoInfo(input)
            runOnUiThread {
                btnParse.isEnabled = true
                if (info != null) {
                    currentVideoInfo = info
                    showVideoInfo(info)
                    tvBiliStatus.text = "解析成功"
                } else {
                    val err = BiliParser.lastError
                    tvBiliStatus.text = "解析失败: ${err.ifEmpty { "请检查BV号或网络" }}"
                }
            }
        }
    }

    private fun showVideoInfo(info: BiliParser.VideoInfo) {
        cardVideoInfo.visibility = CardView.VISIBLE
        tvTitle.text = info.title
        tvOwner.text = "UP主: ${info.owner}"
        tvDuration.text = "时长: ${BiliParser.formatDuration(info.duration)}  |  BV: ${info.bvid}"
        tvDesc.text = info.desc.ifEmpty { "暂无简介" }

        // 加载封面
        thread {
            try {
                val url = URL(info.cover)
                val bitmap = BitmapFactory.decodeStream(url.openStream())
                runOnUiThread {
                    if (bitmap != null) {
                        ivCover.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {}
        }

        // 分P列表
        if (info.pages.size > 1) {
            cardPages.visibility = CardView.VISIBLE
            val sb = StringBuilder()
            info.pages.forEach { page ->
                sb.append("P${page.page}: ${page.part} (${BiliParser.formatDuration(page.duration)})\n")
            }
            tvPages.text = sb.toString().trimEnd()
        }

        // 重置播放地址按钮
        btnCopyVideoUrl.visibility = Button.GONE
        btnCopyAudioUrl.visibility = Button.GONE
        currentPlayUrl = null
    }

    private fun fetchPlayUrl(bvid: String, cid: Long) {
        tvBiliStatus.text = "正在获取播放地址..."
        btnGetPlayUrl.isEnabled = false

        thread {
            val playUrl = BiliParser.getPlayUrl(bvid, cid)
            runOnUiThread {
                btnGetPlayUrl.isEnabled = true
                if (playUrl != null) {
                    currentPlayUrl = playUrl
                    btnCopyVideoUrl.visibility = Button.VISIBLE
                    if (playUrl.audioUrl.isNotEmpty()) {
                        btnCopyAudioUrl.visibility = Button.VISIBLE
                    }
                    tvBiliStatus.text = "播放地址获取成功 (${playUrl.qualityDesc})"
                } else {
                    tvBiliStatus.text = "获取播放地址失败（可能需要登录Cookie）"
                }
            }
        }
    }

    private fun playInline(bvid: String, cid: Long, title: String) {
        // 直接跳转到播放器，用B站官方嵌入式WebView播放
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("bvid", bvid)
            putExtra("cid", cid)
            putExtra("video_title", title)
        }
        startActivity(intent)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label 已复制", Toast.LENGTH_SHORT).show()
    }
}
