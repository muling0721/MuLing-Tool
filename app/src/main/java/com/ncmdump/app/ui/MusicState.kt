package com.ncmdump.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ncmdump.app.NeteaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网易云页面的搜索状态。放在 App 层持有，
 * 切换底部标签 / 打开子页面返回后可以保留搜索结果。
 */
class MusicState {

    var keyword by mutableStateOf("")
    var searching by mutableStateOf(false)
    var results by mutableStateOf<List<NeteaseApi.Song>>(emptyList())
    var status by mutableStateOf("输入歌名或歌手，点击试听")
    var failed by mutableStateOf(false)
    var qualityIndex by mutableIntStateOf(0)

    fun search(scope: CoroutineScope) {
        val key = keyword.trim()
        if (key.isEmpty() || searching) return
        searching = true
        failed = false
        status = "搜索中…"
        scope.launch {
            val list = withContext(Dispatchers.IO) { NeteaseApi.search(key) }
            searching = false
            results = list
            if (list.isEmpty()) {
                val error = NeteaseApi.lastError
                failed = error.isNotEmpty()
                status = if (error.isNotEmpty()) "搜索失败：$error" else "没有找到相关歌曲"
            } else {
                failed = false
                status = "共 ${list.size} 首 · 点击试听，长按查看更多"
            }
        }
    }
}
