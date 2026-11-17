package com.ncmdump.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.CloudDownload
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ncmdump.app.BuildConfig
import com.ncmdump.app.OutputDir
import com.ncmdump.app.UpdateChecker
import com.ncmdump.app.ui.LocalAppState
import com.ncmdump.app.ui.component.ChoiceChips
import com.ncmdump.app.ui.component.GroupCard
import com.ncmdump.app.ui.component.LabeledSlider
import com.ncmdump.app.ui.component.MuDivider
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.PrimaryButton
import com.ncmdump.app.ui.component.SecondaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.SettingRow
import com.ncmdump.app.ui.component.SwitchRow
import com.ncmdump.app.ui.theme.SeedColor
import com.ncmdump.app.ui.util.toast
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenTheme: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val context = LocalContext.current
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogSubtitle by remember { mutableStateOf<String?>(null) }
    var dialogBody by remember { mutableStateOf("") }
    var dialogUrl by remember { mutableStateOf<String?>(null) }

    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val path = treeUriToPath(uri)
        if (path.isBlank()) {
            toast(context, "无法识别该目录，请选择内部存储中的目录")
        } else {
            OutputDir.set(context, path)
            appState.refreshOutputDir(context)
            toast(context, "输出目录：$path")
        }
    }

    fun chooseOutputDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            toast(context, "请先授予「所有文件访问权限」")
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }
            return
        }
        runCatching { pickDir.launch(null) }
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
            Text(
                text = "设置",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "偏好设置与关于",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { SectionHeader(title = "存储") }
        item {
            GroupCard {
                SettingRow(
                    title = "输出目录",
                    subtitle = appState.outputDirLabel,
                    icon = Icons.TwoTone.Folder,
                    onClick = { chooseOutputDir() }
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "下载记录",
                    subtitle = "查看 / 定位已下载的音频与视频",
                    icon = Icons.TwoTone.Download,
                    onClick = onOpenDownloads
                )
            }
        }

        item { SectionHeader(title = "外观") }
        item {
            GroupCard {
                SettingRow(
                    title = "主题与外观",
                    subtitle = "深浅色、主题色、背景与不透明度",
                    icon = Icons.TwoTone.Palette,
                    onClick = onOpenTheme
                )
            }
        }

        item { SectionHeader(title = "更新") }
        item {
            GroupCard {
                SwitchRow(
                    title = "自动检测更新",
                    subtitle = if (BuildConfig.DEBUG) "测试版默认关闭" else "启动时检查 GitHub 最新版本",
                    checked = appState.autoUpdate,
                    onCheckedChange = { appState.updateAutoUpdate(it) },
                    icon = Icons.TwoTone.CloudDownload
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "检查更新",
                    subtitle = if (checking) "正在检查…" else "当前版本 v${BuildConfig.VERSION_NAME}",
                    icon = Icons.TwoTone.Refresh,
                    onClick = {
                        if (checking) return@SettingRow
                        checking = true
                        scope.launch {
                            val release = UpdateChecker.fetchLatest()
                            checking = false
                            val current = UpdateChecker.currentVersion(context)
                            if (release == null) {
                                dialogTitle = "检查更新失败"
                                dialogSubtitle = null
                                dialogBody = "无法获取更新信息，请检查网络连接后重试。"
                                dialogUrl = null
                            } else if (UpdateChecker.isNewer(release.tagName, current)) {
                                dialogTitle = "发现新版本"
                                dialogSubtitle = "v$current  →  ${release.tagName}"
                                dialogBody = release.body.ifBlank { "有新版本可用，建议更新以获得最新修复与改进。" }
                                dialogUrl = release.htmlUrl
                            } else {
                                dialogTitle = "已是最新版本"
                                dialogSubtitle = "v$current"
                                dialogBody = "当前已是最新版本，无需更新。"
                                dialogUrl = null
                            }
                        }
                    }
                )
            }
        }

        item { SectionHeader(title = "关于") }
        item {
            GroupCard {
                SettingRow(
                    title = "GitHub 仓库",
                    subtitle = UpdateChecker.GITHUB_URL,
                    icon = Icons.TwoTone.Info,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.GITHUB_URL))
                            )
                        }
                    }
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "版本",
                    subtitle = "MuLing Tool v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    icon = Icons.TwoTone.Check
                )
                MuDivider(startIndent = 70.dp)
                SettingRow(
                    title = "版权说明",
                    subtitle = "仅供个人已购买 / 下载歌曲的本地备份使用",
                    icon = Icons.TwoTone.Info
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 22.dp, start = 8.dp)) {
                Text(
                    text = "by.muling",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    val title = dialogTitle
    if (title != null) {
        val dismiss: (@Composable () -> Unit)? = if (dialogUrl != null) {
            { TextButton(onClick = { dialogTitle = null }) { Text("稍后") } }
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { dialogTitle = null },
            title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val subtitle = dialogSubtitle
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(text = dialogBody, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                val url = dialogUrl
                if (url != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        dialogTitle = null
                    }) { Text("立即更新") }
                } else {
                    TextButton(onClick = { dialogTitle = null }) { Text("好的") }
                }
            },
            dismissButton = dismiss
        )
    }
}

@Composable
fun ThemeScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appState = LocalAppState.current
    val darkModeIndex = when (appState.darkMode) {
        "light" -> 1
        "dark" -> 2
        else -> 0
    }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        appState.updateBackground(uri.toString())
        toast(context, "背景已设置")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 4.dp,
            bottom = bottomPadding
        )
    ) {
        item { SubPageHeader("主题与外观", "深浅色、主题色与背景", onBack) }

        item { SectionHeader(title = "深浅模式") }
        item {
            MuLingCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    ChoiceChips(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = darkModeIndex,
                        onSelect = { index ->
                            appState.updateDarkMode(
                                when (index) {
                                    1 -> "light"
                                    2 -> "dark"
                                    else -> "system"
                                }
                            )
                        }
                    )
                }
            }
        }

        item { SectionHeader(title = "主题色") }
        item {
            MuLingCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    SwitchRow(
                        title = "动态取色",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "使用壁纸提取的颜色（Android 12+）"
                        } else {
                            "需要 Android 12 及以上系统"
                        },
                        icon = Icons.TwoTone.Palette,
                        checked = appState.dynamicColor,
                        onCheckedChange = { appState.updateDynamicColor(it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MuDivider()
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "预设颜色",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SeedColor.entries.forEach { seed ->
                            val selected = !appState.dynamicColor && appState.seed == seed
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(seed.color)
                                        .border(
                                            width = if (selected) 3.dp else 0.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { appState.updateSeed(seed) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.TwoTone.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = seed.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "背景") }
        item {
            MuLingCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (appState.background.isBlank()) "未设置自定义背景" else "已设置自定义背景",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "选择图片",
                            icon = Icons.TwoTone.Wallpaper,
                            modifier = Modifier.weight(1f),
                            onClick = { runCatching { pickBackground.launch(arrayOf("image/*")) } }
                        )
                        SecondaryButton(
                            text = "清除",
                            enabled = appState.background.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            onClick = { appState.updateBackground(null) }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    MuDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    SwitchRow(
                        title = "背景模糊",
                        subtitle = "仅 Android 12+ 生效",
                        icon = Icons.TwoTone.BlurOn,
                        checked = appState.blurEnabled,
                        onCheckedChange = { appState.updateBlurEnabled(it) }
                    )
                    LabeledSlider(
                        label = "模糊强度",
                        value = appState.blurStrength.toFloat(),
                        valueText = "${appState.blurStrength}%",
                        onValueChange = { appState.updateBlurStrength(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = appState.blurEnabled
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MuDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    LabeledSlider(
                        label = "卡片不透明度",
                        value = appState.cardAlpha,
                        valueText = "${(appState.cardAlpha * 100).toInt()}%",
                        onValueChange = { appState.updateCardAlpha(it) },
                        valueRange = 0.3f..1f
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                text = "完成",
                icon = Icons.TwoTone.Check,
                onClick = onBack
            )
        }
    }
}

private fun treeUriToPath(uri: Uri): String = try {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val colon = docId.indexOf(':')
    if (colon < 0) {
        ""
    } else {
        val volume = docId.substring(0, colon)
        val rel = docId.substring(colon + 1)
        val base = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        if (rel.isBlank()) base else "$base/$rel"
    }
} catch (_: Exception) {
    ""
}
