package com.ncmdump.app

import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * 短视频播放器：ExoPlayer + 自定义 Referer/UA 请求头
 * （抖音 CDN 不带 Referer 会返回 403）
 */
@UnstableApi
class ShortVideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var videoUrl = ""
    private var referer = ""
    private var title = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_short_player)

        videoUrl = intent.getStringExtra("video_url") ?: ""
        referer = intent.getStringExtra("referer") ?: ""
        title = intent.getStringExtra("video_title") ?: ""

        val playerView = findViewById<PlayerView>(R.id.playerView)
        findViewById<Button>(R.id.btnShortSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnShortBack).setOnClickListener { finish() }

        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "无效的视频地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val headers = HashMap<String, String>()
        headers["User-Agent"] = ShortVideoApi.USER_AGENT
        if (referer.isNotEmpty()) headers["Referer"] = referer

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaSource(mediaSource)
            it.prepare()
            it.playWhenReady = true
        }
    }

    @Suppress("DEPRECATION")
    private fun save() {
        try {
            val safeTitle = title.ifBlank { "shortvideo_${System.currentTimeMillis()}" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
            val request = DownloadManager.Request(Uri.parse(videoUrl))
                .setTitle(safeTitle)
                .setMimeType("video/mp4")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "$safeTitle.mp4")
                .addRequestHeader("User-Agent", ShortVideoApi.USER_AGENT)
            if (referer.isNotEmpty()) request.addRequestHeader("Referer", referer)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "已开始下载：$safeTitle.mp4", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
