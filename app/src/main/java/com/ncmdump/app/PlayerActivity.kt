package com.ncmdump.app

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class PlayerActivity : BaseActivity() {

    private lateinit var webView: WebView

    private var bvid: String = ""
    private var cid: Long = 0
    private var directUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        bvid = intent.getStringExtra("bvid") ?: ""
        cid = intent.getLongExtra("cid", 0)
        directUrl = intent.getStringExtra("video_url") ?: ""

        webView = findViewById(R.id.webView)
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
        val url = "https://player.bilibili.com/player.html?bvid=$bvid&cid=$cid&page=1&high_quality=1&danmaku=0&autoplay=1"
        webView.loadUrl(url)
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
