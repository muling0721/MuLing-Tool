package com.ncmdump.app

import android.os.Bundle
import android.os.Environment
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

class PlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnSave: Button
    private lateinit var btnBack: Button

    private var bvid: String = ""
    private var cid: Long = 0
    private var videoTitle: String = ""
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        bvid = intent.getStringExtra("bvid") ?: ""
        cid = intent.getLongExtra("cid", 0)
        videoTitle = intent.getStringExtra("video_title") ?: ""

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
        if (bvid.isEmpty() || cid == 0L) {
            Toast.makeText(this, "无效的视频参数", Toast.LENGTH_SHORT).show()
            return
        }
        // B站官方嵌入式播放器
        val url = "https://player.bilibili.com/player.html?bvid=$bvid&cid=$cid&page=1&high_quality=1&danmaku=0&autoplay=1"
        webView.loadUrl(url)
    }

    private fun saveVideo() {
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

                // 用 MediaStore 写入公共 Movies 目录
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }

                val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "创建文件失败", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                    conn.disconnect()
                    isSaving = false
                    return@thread
                }

                val outputStream = resolver.openOutputStream(uri)
                if (outputStream == null) {
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "打开输出流失败", Toast.LENGTH_LONG).show()
                        resetSaveButton()
                    }
                    conn.disconnect()
                    isSaving = false
                    return@thread
                }

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

                // 通知媒体库更新
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Video.Media.SIZE, total)
                resolver.update(uri, contentValues, null, null)

                // 获取真实路径
                val realPath = getRealPathFromUri(uri)

                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "下载完成: $realPath", Toast.LENGTH_LONG).show()
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

    private fun getRealPathFromUri(uri: android.net.Uri): String {
        return try {
            val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val colIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                val path = cursor.getString(colIndex)
                cursor.close()
                path ?: uri.toString()
            } else {
                uri.toString()
            }
        } catch (_: Exception) {
            uri.toString()
        }
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
