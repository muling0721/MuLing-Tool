package com.ncmdump.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ncmdump.app.BiliParser
import com.ncmdump.app.DownloadHelper
import com.ncmdump.app.OutputDir
import com.ncmdump.app.ShortVideoApi
import com.ncmdump.app.ui.component.CoverImage
import com.ncmdump.app.ui.component.DropdownField
import com.ncmdump.app.ui.component.GroupCard
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.MuLingTextField
import com.ncmdump.app.ui.component.PillTabs
import com.ncmdump.app.ui.component.PrimaryButton
import com.ncmdump.app.ui.component.SecondaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.StatusPill
import com.ncmdump.app.ui.util.copyText
import com.ncmdump.app.ui.util.safeFileName
import com.ncmdump.app.ui.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 视频页：哔哩哔哩解析 + 抖音/快手短视频解析。
 */
@Composable
fun VideoScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    onPlayBili: (bvid: String, cid: Long, title: String) -> Unit,
    onPlayShort: (url: String, referer: String, title: String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

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
                text = "视频解析",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "复制分享链接即可解析无水印资源",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            PillTabs(items = listOf("哔哩哔哩", "短视频"), selectedIndex = tab, onSelect = { tab = it })
        }

        if (tab == 0) {
            biliSection(onPlayBili)
        } else {
            shortVideoSection(onPlayShort)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.biliSection(
    onPlayBili: (String, Long, String) -> Unit
) = item { BiliPane(onPlayBili) }

private fun androidx.compose.foundation.lazy.LazyListScope.shortVideoSection(
    onPlayShort: (String, String, String) -> Unit
) = item { ShortVideoPane(onPlayShort) }

@Composable
private fun BiliPane(onPlayBili: (String, Long, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<BiliParser.VideoInfo?>(null) }
    var status by remember { mutableStateOf("支持 BV 号、av 号、b23.tv 短链") }
    var failed by remember { mutableStateOf(false) }
    var playUrl by remember { mutableStateOf<BiliParser.PlayUrl?>(null) }
    var fetchingUrl by remember { mutableStateOf(false) }

    fun parse() {
        if (input.isBlank() || loading) return
        loading = true
        failed = false
        status = "正在解析…"
        playUrl = null
        val keyword = input.trim()
        scope.launch {
            val result = withContext(Dispatchers.IO) { BiliParser.getVideoInfo(keyword) }
            loading = false
            if (result != null) {
                info = result
                status = "解析成功"
            } else {
                failed = true
                status = BiliParser.lastError.ifBlank { "解析失败，请检查链接或网络" }
            }
        }
    }

    Column(modifier = Modifier.padding(top = 14.dp)) {
        MuLingCard {
            Column(modifier = Modifier.padding(16.dp)) {
                MuLingTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "粘贴 BV 号或视频链接",
                    leadingIcon = Icons.TwoTone.Link,
                    onImeAction = { parse() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (loading) "正在解析…" else "解析视频",
                    loading = loading,
                    icon = Icons.TwoTone.Search,
                    onClick = { parse() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val data = info
        if (data != null) {
            SectionHeader(title = "视频信息")
            MuLingCard {
                Column {
                    CoverImage(
                        url = data.cover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(text = "UP主 ${data.owner.ifBlank { "未知" }}")
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusPill(text = BiliParser.formatDuration(data.duration))
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusPill(text = data.bvid)
                        }
                        if (data.desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = data.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (data.pages.size > 1) {
                SectionHeader(title = "分P列表")
                MuLingCard {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        data.pages.forEach { page ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayBili(data.bvid, page.cid, "${data.title} - ${page.part}")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "P${page.page}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = page.part,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = BiliParser.formatDuration(page.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SectionHeader(title = "操作")
            GroupCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "播放",
                            icon = Icons.TwoTone.PlayArrow,
                            modifier = Modifier.weight(1f),
                            onClick = { onPlayBili(data.bvid, data.cid, data.title) }
                        )
                        SecondaryButton(
                            text = "下载",
                            icon = Icons.TwoTone.Download,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    status = "正在获取下载地址…"
                                    val url = withContext(Dispatchers.IO) {
                                        BiliParser.getDirectPlayUrl(data.bvid, data.cid, qn = 80)
                                    }
                                    if (url == null || url.videoUrl.isEmpty()) {
                                        failed = true
                                        status = "下载失败：无法获取视频地址（可能仅支持 DASH）"
                                    } else {
                                        val name = safeFileName(
                                            data.title.ifBlank { data.bvid },
                                            data.bvid
                                        ) + ".mp4"
                                        DownloadHelper.download(
                                            context,
                                            url.videoUrl,
                                            OutputDir.file(context, name),
                                            "https://www.bilibili.com/",
                                            data.title
                                        )
                                        status = "已开始下载：$name"
                                    }
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "获取直链",
                            icon = Icons.TwoTone.ContentCopy,
                            modifier = Modifier.weight(1f),
                            enabled = !fetchingUrl,
                            onClick = {
                                scope.launch {
                                    fetchingUrl = true
                                    status = "正在获取播放地址…"
                                    val url = withContext(Dispatchers.IO) {
                                        BiliParser.getPlayUrl(data.bvid, data.cid)
                                    }
                                    fetchingUrl = false
                                    if (url == null) {
                                        failed = true
                                        status = "获取播放地址失败"
                                    } else {
                                        playUrl = url
                                        status = "播放地址获取成功（${url.qualityDesc.ifBlank { "默认画质" }}）"
                                    }
                                }
                            }
                        )
                        SecondaryButton(
                            text = "复制链接",
                            icon = Icons.TwoTone.ContentCopy,
                            modifier = Modifier.weight(1f),
                            enabled = playUrl != null,
                            onClick = {
                                val url = playUrl ?: return@SecondaryButton
                                copyText(context, "视频流地址", url.videoUrl)
                            }
                        )
                    }
                    if (playUrl?.audioUrl?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SecondaryButton(
                            text = "复制音频流地址",
                            icon = Icons.TwoTone.ContentCopy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                playUrl?.audioUrl?.let { copyText(context, "音频流地址", it) }
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            MuLingCard {
                com.ncmdump.app.ui.component.EmptyState(
                    icon = Icons.TwoTone.Videocam,
                    title = "还没有解析结果",
                    subtitle = "粘贴视频链接后点击「解析视频」"
                )
            }
        }
    }
}

@Composable
private fun ShortVideoPane(onPlayShort: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShortVideoApi.Result?>(null) }
    var status by remember { mutableStateOf("粘贴抖音 / 快手分享链接") }
    var failed by remember { mutableStateOf(false) }
    var formatIndex by remember { mutableIntStateOf(0) }

    fun parse() {
        if (input.isBlank() || loading) return
        loading = true
        failed = false
        status = "正在解析…"
        val text = input.trim()
        scope.launch {
            val res = withContext(Dispatchers.IO) { ShortVideoApi.parse(text) }
            loading = false
            if (res != null) {
                result = res
                status = if (res.isGallery) "解析成功（图集 ${res.images.size} 张）" else "解析成功"
            } else {
                failed = true
                status = ShortVideoApi.lastError.ifBlank { "解析失败" }
            }
        }
    }

    Column(modifier = Modifier.padding(top = 14.dp)) {
        MuLingCard {
            Column(modifier = Modifier.padding(16.dp)) {
                MuLingTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "粘贴分享链接或识别出的文本",
                    leadingIcon = Icons.TwoTone.Link,
                    singleLine = false,
                    onImeAction = { parse() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = if (loading) "正在解析…" else "解析短视频",
                    loading = loading,
                    icon = Icons.TwoTone.Search,
                    onClick = { parse() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val data = result
        if (data != null) {
            SectionHeader(title = "解析结果")
            MuLingCard {
                Column {
                    CoverImage(
                        url = data.images.firstOrNull() ?: data.cover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = data.title.ifBlank { "无标题" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(text = data.platform.label)
                            if (data.author.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(text = data.author)
                            }
                            if (data.isGallery) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(text = "图集 ${data.images.size} 张")
                            }
                        }
                    }
                }
            }

            SectionHeader(title = "操作")
            GroupCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!data.isGallery) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = "播放",
                                icon = Icons.TwoTone.PlayArrow,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val url = data.videoUrl ?: return@SecondaryButton
                                    onPlayShort(
                                        url,
                                        ShortVideoApi.refererFor(data.platform),
                                        data.title
                                    )
                                }
                            )
                            SecondaryButton(
                                text = "下载",
                                icon = Icons.TwoTone.Download,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val url = data.videoUrl ?: return@SecondaryButton
                                    val name = ShortVideoApi.fileName(data)
                                    DownloadHelper.download(
                                        context,
                                        url,
                                        OutputDir.file(context, name),
                                        ShortVideoApi.refererFor(data.platform),
                                        data.title.ifBlank { name }
                                    )
                                    status = "已开始下载：$name"
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (data.isGallery) {
                            SecondaryButton(
                                text = "下载全部图片",
                                icon = Icons.TwoTone.Download,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val stamp = System.currentTimeMillis()
                                    data.images.forEachIndexed { index, image ->
                                        val name = "${data.platform.label}_${stamp}_$index.jpg"
                                        DownloadHelper.download(
                                            context,
                                            image,
                                            OutputDir.file(context, name),
                                            title = name
                                        )
                                    }
                                    status = "已开始下载 ${data.images.size} 张图片"
                                }
                            )
                        } else {
                            SecondaryButton(
                                text = "复制直链",
                                icon = Icons.TwoTone.ContentCopy,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val link = data.videoUrl ?: data.images.firstOrNull()
                                    if (link != null) copyText(context, "视频直链", link) else {
                                        toast(context, "暂无可复制的链接")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            MuLingCard {
                com.ncmdump.app.ui.component.EmptyState(
                    icon = Icons.TwoTone.Videocam,
                    title = "还没有解析结果",
                    subtitle = "支持抖音、快手分享链接与图集"
                )
            }
        }
    }
}
