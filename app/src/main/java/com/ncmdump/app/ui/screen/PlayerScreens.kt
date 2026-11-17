package com.ncmdump.app.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ncmdump.app.ShortVideoApi
import com.ncmdump.app.ui.component.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** B 站视频播放（官方内嵌播放器 / 直链） */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BiliPlayerScreen(
    bvid: String,
    cid: Long,
    directUrl: String,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    @Suppress("DEPRECATION")
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    if (directUrl.isNotEmpty()) {
                        val html = """
                            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                            <style>html,body{margin:0;height:100%;background:#000}
                            video{width:100%;height:100%;object-fit:contain}</style>
                            </head><body>
                            <video src="$directUrl" controls autoplay playsinline></video>
                            </body></html>
                        """.trimIndent()
                        loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    } else {
                        loadUrl(
                            "https://player.bilibili.com/player.html?bvid=$bvid&cid=$cid" +
                                "&page=1&high_quality=1&danmaku=0&autoplay=1"
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** 短视频播放：ExoPlayer 在线播放，失败时下载到缓存后播放 */
@OptIn(UnstableApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ShortVideoPlayerScreen(
    videoUrl: String,
    referer: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("正在加载视频…") }
    var failed by remember { mutableStateOf(false) }
    var attemptedFallback by remember { mutableStateOf(false) }
    var localFile by remember { mutableStateOf<File?>(null) }

    val player = remember {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = ShortVideoApi.USER_AGENT
        if (referer.isNotEmpty()) headers["Referer"] = referer
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
            )
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    loading = false
                    failed = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!attemptedFallback) {
                    attemptedFallback = true
                    message = "在线播放失败（${error.errorCodeName}），正在下载后播放…"
                    loading = true
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { downloadToCache(context, videoUrl, referer) }
                        if (file != null) {
                            localFile = file
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                            player.prepare()
                            player.playWhenReady = true
                        } else {
                            loading = false
                            failed = true
                            message = "播放失败：${error.errorCodeName}"
                        }
                    }
                } else {
                    loading = false
                    failed = true
                    message = "播放失败：${error.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            }
        )

        if (loading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                SecondaryButton(text = "返回", onClick = onBack)
            }
        }

        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 62.dp, end = 24.dp, top = 20.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun downloadToCache(context: android.content.Context, url: String, referer: String): File? {
    return try {
        val file = File(context.cacheDir, "shortvideo_${System.currentTimeMillis()}.mp4")
        var current = URL(url)
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
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank() || redirects++ > 8) return null
                current = URL(current, location)
                continue
            }
            if (code != 200 && code != 206) {
                conn.disconnect()
                return null
            }
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            }
            conn.disconnect()
            break
        }
        if (file.length() > 0) file else null
    } catch (_: Exception) {
        null
    }
}
