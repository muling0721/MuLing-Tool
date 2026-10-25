package com.ncmdump.app

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class PlayerActivity : BaseActivity() {

    private lateinit var webView: WebView
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private var bvid: String = ""
    private var cid: Long = 0
    private var videoTitle: String = ""
    private var directUrl: String = ""
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        bvid = intent.getStringExtra("bvid") ?: ""
        cid = intent.getLongExtra("cid", 0)
        videoTitle = intent.getStringExtra("video_title") ?: ""
        directUrl = intent.getStringExtra("video_url") ?: ""

        supportActionBar?.title = videoTitle.ifEmpty { "播放" }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        webView = findViewById(R.id.webView)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        btnSave.setOnClickListener {
            if (!isSaving) saveVideo()
        }
        btnBack.setOnClickListener { finish() }

        setupWebView()
        loadVideo()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadVideo() {
        if (directUrl.isNotEmpty()) {
            val html = """
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000}video{width:100%;height:100%}</style>
                </head><body><video src="$directUrl" controls autoplay playsinline></video></body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            return
        }
        if (bvid.isEmpty() || cid == 0L) {
            Toast.makeText(this, "无效的视频参数", Toast.LENGTH_SHORT).show()
            return
        }
        // B站官方嵌入式播放器
        val url = "https://player.bilibili.com/player.html?bvid=$bvid&cid=$cid&page=1&high_quality=1&danmaku=0&autoplay=1"
        webView.loadUrl(url)
    }

    private fun saveVideo() {
        if (directUrl.isNotEmpty()) {
            saveDirectVideo()
            return
        }
        if (bvid.isEmpty() || cid == 0L) {
            Toast.makeText(this, "无效的视频参数", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        btnSave.isEnabled = false
        btnSave.text = "解析地址..."

        thread {
            try {
                // 只用合并格式（fnval=0），1080P
                val playUrl = BiliParser.getDirectPlayUrl(bvid, cid, qn = 80)

                if (playUrl == null || playUrl.videoUrl.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "获取下载地址失败，该视频可能仅支持DASH格式", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                    isSaving = false
                    return@thread
                }

                runOnUiThread {
                    btnSave.text = "下载中 0%"
                }

                val url = URL(playUrl.videoUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Referer", "https://www.bilibili.com/")
                conn.setRequestProperty("Origin", "https://www.bilibili.com")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                if (conn.responseCode != 200) {
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "下载失败: HTTP ${conn.responseCode}", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                    conn.disconnect()
                    isSaving = false
                    return@thread
                }

                val contentLength = conn.contentLength.toLong()
                val safeTitle = videoTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
                val fileName = "$safeTitle.mp4"

                val outFile = OutputDir.file(this, fileName)
                outFile.parentFile?.mkdirs()
                val outputStream = FileOutputStream(outFile)

                val input = conn.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var total = 0L
                var lastPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (contentLength > 0) {
                        val percent = (total * 100 / contentLength).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            runOnUiThread {
                                btnSave.text = "下载中 $percent%"
                            }
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                input.close()
                conn.disconnect()

                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "下载完成: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
                    resetSaveButton()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                    resetSaveButton()
                }
            }
            isSaving = false
        }
    }

    private fun saveDirectVideo() {
        val safeTitle = videoTitle.ifBlank { "shortvideo_${System.currentTimeMillis()}" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
        val name = "$safeTitle.mp4"
        DownloadHelper.download(this, directUrl, OutputDir.file(this, name), "https://www.bilibili.com/")
        Toast.makeText(this, "已开始下载：$name", Toast.LENGTH_SHORT).show()
    }

    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = "保存视频"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
