package com.ncmdump.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 首页 NCM 解密状态。放在 App 层持有，
 * 切换底部标签后已选文件与解密进度不会丢失。
 */
class HomeState {

    val files = mutableStateListOf<Uri>()

    var busy by mutableStateOf(false)
    var status by mutableStateOf("权限已授予，请选择 ncm 文件")
    var progress by mutableFloatStateOf(0f)
    var failed by mutableStateOf(false)
}
