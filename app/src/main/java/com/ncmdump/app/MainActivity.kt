package com.ncmdump.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ncmdump.app.ui.MuLingApp

/**
 * 单 Activity + Compose 架构：所有页面由 [MuLingApp] 内部导航管理。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MuLingApp() }
    }
}
