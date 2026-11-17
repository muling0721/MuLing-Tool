package com.ncmdump.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.ncmdump.app.ui.screen.BiliPlayerScreen
import com.ncmdump.app.ui.theme.MuLingTheme
import com.ncmdump.app.ui.theme.SeedColor

/** B 站视频播放（内嵌官方播放器 / 直链） */
class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bvid = intent.getStringExtra("bvid").orEmpty()
        val cid = intent.getLongExtra("cid", 0L)
        val directUrl = intent.getStringExtra("video_url").orEmpty()

        setContent {
            PlayerTheme {
                BiliPlayerScreen(
                    bvid = bvid,
                    cid = cid,
                    directUrl = directUrl,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
internal fun PlayerTheme(content: @Composable () -> Unit) {
    MuLingTheme(dark = true, seed = SeedColor.Blue, dynamicColor = false, cardAlpha = 1f) {
        content()
    }
}
