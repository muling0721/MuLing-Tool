package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 短视频播放器。
 *
 * 抖音/快手 CDN 对请求头（尤其 Referer）敏感，且可能对 Range 请求返回 302。
 * 策略：先用 ExoPlayer 带头流式播放；失败则用带头 HTTP 下载到本地再播放，
 * 并显示具体错误码便于排查。
 */
@UnstableApi
class ShortVideoPlayerActivity : BaseActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoadingHint: TextView
    private var player: ExoPlayer? = null
    private var videoUrl = ""
    private var referer = ""
    private var title = ""
    private var triedLocalFallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_short_player)

        videoUrl = intent.getStringExtra("video_url") ?: ""
        referer = intent.getStringExtra("referer") ?: ""
        title = intent.getStringExtra("video_title") ?: ""

        playerView = findViewById(R.id.playerView)
        progressBar = findViewById(R.id.progressBar)
        tvLoadingHint = findViewById(R.id.tvLoadingHint)

        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "无效的视频地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        playUri(Uri.parse(videoUrl))
    }

    private fun httpDataSourceFactory(): DefaultHttpDataSource.Factory {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = ShortVideoApi.USER_AGENT
        if (referer.isNotEmpty()) headers["Referer"] = referer
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
    }

    private fun playUri(uri: Uri) {
        releasePlayer()
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory())
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
                    if (!triedLocalFallback) {
                        triedLocalFallback = true
                        Toast.makeText(
                            this@ShortVideoPlayerActivity,
                            "在线播放失败（${error.errorCodeName}），正在下载后播放…",
                            Toast.LENGTH_LONG
                        ).show()
                        downloadThenPlay()
                    } else {
                        hideLoading()
                        Toast.makeText(
                            this@ShortVideoPlayerActivity,
                            "播放失败：${error.errorCodeName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) hideLoading()
                }
            })
            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    /** 兜底：用带 Referer/UA 的普通 HTTP（手动跟随重定向）把文件下到缓存再播放 */
    private fun downloadThenPlay() {
        releasePlayer()
        showLoading("正在下载视频…")
        thread {
            try {
                val file = File(cacheDir, "shortvideo_${System.currentTimeMillis()}.mp4")
                var current = URL(videoUrl)
                var redirects = 0
                while (true) {
                    val conn = current.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", ShortVideoApi.USER_AGENT)
                    if (referer.isNotEmpty()) conn.setRequestProperty("Referer", referer)
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (loc.isNullOrBlank() || redirects++ > 8) {
                            throw IllegalStateException("重定向异常 HTTP $code")
                        }
                        current = URL(current, loc)
                        continue
                    }
                    if (code != 200 && code != 206) {
                        conn.disconnect()
                        throw IllegalStateException("HTTP $code")
                    }
                    conn.inputStream.use { input ->
                        file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
                    }
                    conn.disconnect()
                    break
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    hideLoading()
                    playUri(Uri.fromFile(file))
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadThenPlay failed", e)
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(
                        this@ShortVideoPlayerActivity,
                        "播放失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(text: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        tvLoadingHint.text = text
        tvLoadingHint.visibility = TextView.VISIBLE
    }

    private fun hideLoading() {
        progressBar.visibility = ProgressBar.GONE
        tvLoadingHint.visibility = TextView.GONE
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerView.player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShortVideoPlayer"
    }
}
