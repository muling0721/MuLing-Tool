package com.ncmdump.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ncmdump.app.FileUtils
import com.ncmdump.app.LyricFetcher
import com.ncmdump.app.MetadataWriter
import com.ncmdump.app.NcmCrypt
import com.ncmdump.app.OutputDir
import com.ncmdump.app.ui.component.GroupCard
import com.ncmdump.app.ui.component.IconTile
import com.ncmdump.app.ui.component.MuDivider
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.PrimaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.SecondaryButton
import com.ncmdump.app.ui.component.SettingRow
import com.ncmdump.app.ui.component.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 首页：NCM 解密 + 音频工具入口。
 */
@Composable
fun HomeScreen(
    state: com.ncmdump.app.ui.HomeState,
    scope: kotlinx.coroutines.CoroutineScope,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenConvert: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val busy = state.busy
    val status = state.status
    val progress = state.progress
    val failed = state.failed
    val files = state.files

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) state.status = "存储权限被拒绝，无法使用"
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            files.clear()
            files.addAll(uris)
            state.failed = false
            state.status = "已选择 ${uris.size} 个文件，点击开始解密"
        }
    }

    fun pick() {
        if (!hasStoragePermission(context)) {
            hasPermission = false
            return
        }
        runCatching { pickFiles.launch(arrayOf("*/*")) }
    }

    fun startDecrypt() {
        if (busy || files.isEmpty()) return
        state.busy = true
        state.failed = false
        state.progress = 0f
        state.status = "正在解密…"
        val targets = files.toList()
        scope.launch {
            val result = decryptBatch(context, targets) { done, total, name ->
                state.progress = done.toFloat() / total.toFloat()
                state.status = "解密中 $done/$total · $name"
            }
            state.busy = false
            state.failed = result.failed > 0 && result.success == 0
            state.progress = 1f
            state.status = "完成：成功 ${result.success}，失败 ${result.failed}\n输出到 ${OutputDir.display(context)}"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 8.dp,
            bottom = bottomPadding
        )
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MuLing Tool",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NCM 解密 · 音频处理 · 视频解析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconTile(icon = Icons.TwoTone.MusicNote, size = 46.dp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            MuLingCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(
                        icon = if (hasPermission) Icons.TwoTone.Check else Icons.TwoTone.Lock,
                        tint = if (hasPermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasPermission) "所有文件访问权限已授予" else "需要「所有文件访问权限」",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasPermission) {
                                "本地解密，音频不会上传到任何服务器"
                            } else {
                                "读取与保存 ncm / mp3 文件前必须先授权"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!hasPermission) {
                        SecondaryButton(
                            text = "去授权",
                            onClick = { requestAllFilesPermission(context, legacyPermission) }
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "NCM 解密") }

        item {
            GroupCard {
                SettingRow(
                    title = "选择 ncm 文件",
                    subtitle = if (files.isEmpty()) {
                        "支持多选，批量解密"
                    } else {
                        "已选择 ${files.size} 个文件"
                    },
                    icon = Icons.TwoTone.Folder,
                    trailing = {
                        StatusPill(text = if (files.isEmpty()) "未选择" else "${files.size}")
                    },
                    onClick = { pick() }
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "输出目录",
                    subtitle = OutputDir.display(context),
                    icon = Icons.TwoTone.Key,
                    onClick = null
                )
                MuDivider(startIndent = 70.dp)
                Column(modifier = Modifier.padding(16.dp)) {
                    if (busy) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (busy) "正在解密…" else "开始解密",
                        enabled = files.isNotEmpty() && hasPermission,
                        loading = busy,
                        icon = Icons.TwoTone.Key,
                        onClick = { startDecrypt() }
                    )
                }
            }
        }

        if (files.isNotEmpty()) {
            item { SectionHeader(title = "待解密文件") }
            items(files, key = { it.toString() }) { uri ->
                MuLingCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FileUtils.getFileName(context, uri),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { files.remove(uri) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Close,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "音频工具") }

        item {
            GroupCard {
                SettingRow(
                    title = "格式转换",
                    subtitle = "mp3 / flac / wav / aac 等格式互转",
                    icon = Icons.TwoTone.Transform,
                    onClick = onOpenConvert
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "下载记录",
                    subtitle = "查看最近下载的音频 / 视频",
                    icon = Icons.TwoTone.Download,
                    onClick = onOpenDownloads
                )
            }
        }

        item {
            LyricActions()
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/** 版权与提示 */
@Composable
private fun LyricActions() {
    Column(modifier = Modifier.padding(top = 18.dp, start = 8.dp, end = 8.dp)) {
        Text(
            text = "仅供个人已购买 / 下载歌曲的本地备份使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "by.muling · v${com.ncmdump.app.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

data class BatchResult(val success: Int, val failed: Int)

/** 批量解密 + 元数据 / 封面 / 歌词写入 */
private suspend fun decryptBatch(
    context: Context,
    uris: List<Uri>,
    onProgress: (Int, Int, String) -> Unit
): BatchResult = withContext(Dispatchers.IO) {
    val outputDir = OutputDir.get(context).absolutePath
    var success = 0
    var failed = 0
    val total = uris.size

    uris.forEachIndexed { index, uri ->
        val name = FileUtils.getFileName(context, uri)
        val cachePath = copyToCache(context, uri, name)
        if (cachePath == null) {
            failed++
        } else {
            try {
                val metadataJson = NcmCrypt.getMetadata(cachePath)
                val coverBytes = NcmCrypt.getCoverImage(cachePath)
                val coverMime = NcmCrypt.getCoverMime(cachePath)
                val result = NcmCrypt.decrypt(cachePath, outputDir)
                if (result.isNotEmpty()) {
                    runCatching {
                        val json = org.json.JSONObject(metadataJson)
                        val title = json.optString("name", "")
                        val artist = json.optString("artist", "")
                        val album = json.optString("album", "")
                        val safeCover = coverBytes.takeIf { it.size <= 10 * 1024 * 1024 }
                        runCatching {
                            MetadataWriter.write(result, title, artist, album, safeCover, coverMime, null)
                        }
                        runCatching {
                            if (title.isNotEmpty()) {
                                val lyrics = LyricFetcher.fetchLyrics(title, artist)
                                if (!lyrics.isNullOrEmpty()) {
                                    val audioFile = File(result)
                                    val lrc = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
                                    lrc.writeText(lyrics, Charsets.UTF_8)
                                }
                            }
                        }
                    }
                    success++
                } else {
                    failed++
                }
            } catch (e: Throwable) {
                failed++
            } finally {
                runCatching { File(cachePath).delete() }
            }
        }
        onProgress(index + 1, total, name)
    }
    BatchResult(success, failed)
}

private fun copyToCache(context: Context, uri: Uri, name: String): String? = try {
    val cacheFile = File(context.cacheDir, "ncm_${System.currentTimeMillis()}_$name")
    context.contentResolver.openInputStream(uri)?.use { input ->
        cacheFile.outputStream().use { output -> input.copyTo(output) }
    }
    if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
} catch (_: Exception) {
    null
}

fun hasStoragePermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

fun requestAllFilesPermission(
    context: Context,
    legacyPermission: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    } else {
        legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/** 供其他页面显示文件名 */
fun fileNameOf(context: Context, uri: Uri): String {
    return runCatching {
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index)
        }
        name.ifEmpty { uri.lastPathSegment ?: "unknown" }
    }.getOrDefault(uri.lastPathSegment ?: "unknown")
}
