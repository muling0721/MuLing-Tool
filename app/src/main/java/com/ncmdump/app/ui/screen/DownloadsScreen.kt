package com.ncmdump.app.ui.screen

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.FileProvider
import com.ncmdump.app.DownloadHistory
import com.ncmdump.app.ui.component.EmptyState
import com.ncmdump.app.ui.component.MuDivider
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.SecondaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.StatusPill
import com.ncmdump.app.ui.util.copyText
import com.ncmdump.app.ui.util.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下载记录：最近下载过的音频 / 视频，可复制路径或直接打开文件 */
@Composable
fun DownloadsScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(DownloadHistory.load(context)) }
    var selected by remember { mutableStateOf<DownloadHistory.Entry?>(null) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 4.dp,
            bottom = bottomPadding
        )
    ) {
        item { SubPageHeader("下载记录", "最近下载的音频 / 视频（最多 200 条）", onBack) }

        if (entries.isEmpty()) {
            item {
                MuLingCard {
                    EmptyState(
                        icon = Icons.TwoTone.Download,
                        title = "还没有下载记录",
                        subtitle = "下载完成的文件会自动记录在这里"
                    )
                }
            }
        } else {
            item { SectionHeader(title = "全部记录", trailing = {
                Text(
                    text = "${entries.size} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }) }
            items(entries, key = { it.path }) { entry ->
                MuLingCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(text = entry.kind)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.title.ifBlank { entry.fileName },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeFormat.format(Date(entry.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { selected = entry }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.TwoTone.Folder,
                                contentDescription = "定位",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                DownloadHistory.remove(context, entry.path)
                                entries = DownloadHistory.load(context)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = "删除记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                SecondaryButton(
                    text = "清空全部记录",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        DownloadHistory.clear(context)
                        entries = emptyList()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                MuDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "记录只保存在本机，删除记录不会删除已下载的文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val entry = selected
    if (entry != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(text = entry.fileName, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (File(entry.path).exists()) "文件存在" else "文件已被移动或删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (File(entry.path).exists()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val opened = openFile(context, entry.path)
                    if (!opened) {
                        copyText(context, "文件路径", entry.path)
                    }
                    selected = null
                }) { Text("打开 / 复制路径") }
            },
            dismissButton = {
                TextButton(onClick = {
                    copyText(context, "文件路径", entry.path)
                    selected = null
                }) { Text("复制路径") }
            }
        )
    }
}

private fun openFile(context: Context, path: String): Boolean {
    val file = File(path)
    if (!file.exists()) {
        toast(context, "文件不存在")
        return false
    }
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse {
        toast(context, "没有可打开该文件的应用")
        false
    }
}

private fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "opus" -> "audio/opus"
    "lrc", "txt" -> "text/plain"
    else -> "*/*"
}
