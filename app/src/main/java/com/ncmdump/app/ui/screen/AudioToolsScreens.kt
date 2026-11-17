package com.ncmdump.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ncmdump.app.FfmpegHelper
import com.ncmdump.app.ui.component.ChoiceChips
import com.ncmdump.app.ui.component.GroupCard
import com.ncmdump.app.ui.component.IconTile
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.PrimaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.SettingRow
import java.io.File

@Composable
fun SubPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 格式转换 */
@Composable
fun ConvertScreen(topPadding: Dp, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Uri?>(null) }
    var formatIndex by remember { mutableStateOf(0) }
    var bitrateIndex by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("请选择要转换的音频文件") }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = uri
            failed = false
            status = "已选择：${fileNameOf(context, uri)}"
        }
    }

    val formats = FfmpegHelper.audioFormats
    val bitrates = listOf("128k", "192k", "256k", "320k")
    val lossless = setOf("wav", "flac")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 4.dp,
            bottom = bottomPadding
        )
    ) {
        item { SubPageHeader("格式转换", "FFmpeg 转码，输出到统一目录", onBack) }

        item { SectionHeader(title = "源文件") }
        item {
            GroupCard {
                SettingRow(
                    title = if (selected == null) "选择音频文件" else fileNameOf(context, selected!!),
                    subtitle = if (selected == null) "支持 mp3 / flac / wav / m4a 等" else "点击重新选择",
                    icon = Icons.TwoTone.Folder,
                    onClick = { picker.launch(arrayOf("*/*")) }
                )
            }
        }

        item { SectionHeader(title = "目标格式") }
        item {
            MuLingCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    ChoiceChips(
                        options = formats,
                        selectedIndex = formatIndex,
                        onSelect = { formatIndex = it }
                    )
                    if (formats.getOrElse(formatIndex) { "" } !in lossless) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "码率",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ChoiceChips(
                            options = bitrates,
                            selectedIndex = bitrateIndex,
                            onSelect = { bitrateIndex = it }
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "执行") }
        item {
            MuLingCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.TwoTone.Transform, size = 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    PrimaryButton(
                        text = if (busy) "正在转换…" else "开始转换",
                        enabled = selected != null,
                        loading = busy,
                        icon = Icons.TwoTone.Transform
                    ) {
                        val uri = selected ?: return@PrimaryButton
                        busy = true
                        failed = false
                        status = "正在转换…"
                        val format = formats[formatIndex]
                        val bitrate = bitrates[bitrateIndex]
                        FfmpegHelper.convertAudio(context, uri, format, bitrate) { ok, result ->
                            busy = false
                            failed = !ok
                            status = if (ok) {
                                "转换完成：${result?.let { File(it).name } ?: ""}"
                            } else {
                                "转换失败：${result?.take(120) ?: "未知错误"}"
                            }
                        }
                    }
                }
            }
        }
    }
}
