package com.ncmdump.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ncmdump.app.ui.screen.ShortVideoPlayerScreen

/** 短视频播放（ExoPlayer，失败时下载后播放） */
class ShortVideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val videoUrl = intent.getStringExtra("video_url").orEmpty()
        val referer = intent.getStringExtra("referer").orEmpty()
        val title = intent.getStringExtra("video_title").orEmpty()

        if (videoUrl.isEmpty()) {
            finish()
            return
        }

        setContent {
            PlayerTheme {
                ShortVideoPlayerScreen(
                    videoUrl = videoUrl,
                    referer = referer,
                    title = title,
                    onBack = { finish() }
                )
            }
        }
    }
}
