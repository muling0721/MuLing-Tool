package com.ncmdump.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ncmdump.app.NeteaseApi
import com.ncmdump.app.ui.LocalAppState
import com.ncmdump.app.ui.MediaSessionState
import com.ncmdump.app.ui.component.CoverImage
import com.ncmdump.app.ui.component.DropdownField
import com.ncmdump.app.ui.component.MuLingCard
import com.ncmdump.app.ui.component.MuLingTextField
import com.ncmdump.app.ui.component.PrimaryButton
import com.ncmdump.app.ui.component.SectionHeader
import com.ncmdump.app.ui.component.SettingRow
import com.ncmdump.app.ui.util.copyText
import com.ncmdump.app.ui.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val qualityLabels = listOf("标准", "无损", "Hi-Res", "沉浸环绕", "高清环绕", "超清母带")
private val qualityValues = listOf("standard", "lossless", "hires", "sky", "jyeffect", "jymaster")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    media: MediaSessionState,
    state: com.ncmdump.app.ui.MusicState
) {
    val context = LocalContext.current
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    val prefs = appState.prefs

    val keyword = state.keyword
    val searching = state.searching
    val results = state.results
    val status = state.status
    val failed = state.failed
    val qualityIndex = state.qualityIndex
    LaunchedEffect(Unit) {
        val saved = prefs.getString("netease_quality", "standard") ?: "standard"
        val savedIndex = qualityValues.indexOf(saved).coerceAtLeast(0)
        state.qualityIndex = savedIndex
        runCatching { NeteaseApi.level = qualityValues[savedIndex] }
    }
    var sheetSong by remember { mutableStateOf<NeteaseApi.Song?>(null) }
    var lyricTitle by remember { mutableStateOf<String?>(null) }
    var lyricText by remember { mutableStateOf("") }
    var lyricLoading by remember { mutableStateOf(false) }

    fun search() = state.search(scope)

    fun loadLyric(song: NeteaseApi.Song) {
        lyricTitle = song.name
        lyricText = ""
        lyricLoading = true
        scope.launch {
            val lyric = withContext(Dispatchers.IO) { NeteaseApi.fetchLyric(song.id) }
            lyricLoading = false
            if (lyric == null || lyric.isEmpty) {
                lyricText = "未找到该歌曲的歌词"
            } else {
                val content = lyric.merged()
                lyricText = content
                withContext(Dispatchers.IO) { NeteaseApi.saveLyric(context, song, content) }
            }
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
            Text(
                text = "音乐",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "网易云在线搜索 · 试听 · 歌词 · 下载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            MuLingCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    MuLingTextField(
                        value = keyword,
                        onValueChange = { state.keyword = it },
                        placeholder = "搜索歌曲 / 歌手",
                        leadingIcon = Icons.TwoTone.Search,
                        onImeAction = { search() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (searching) "搜索中…" else "搜索",
                        loading = searching,
                        icon = Icons.TwoTone.Search,
                        onClick = { search() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DropdownField(
                        options = qualityLabels,
                        selectedIndex = qualityIndex,
                        label = "音质",
                        onSelect = { index ->
                            state.qualityIndex = index
                            NeteaseApi.level = qualityValues[index]
                            prefs.edit().putString("netease_quality", qualityValues[index]).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (media.status.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = media.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item { SectionHeader(title = "搜索结果") }
            items(results, key = { it.id }) { song ->
                MuLingCard(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .combinedClickable(
                            onClick = { media.play(scope, song) },
                            onLongClick = { sheetSong = song }
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverImage(
                            url = song.cover,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            fallback = Icons.TwoTone.MusicNote
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = listOf(song.artist, song.album)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (song.durationMs > 0) {
                            Text(
                                text = NeteaseApi.formatDuration(song.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.TwoTone.PlayArrow,
                            contentDescription = "播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    val song = sheetSong
    if (song != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetSong = null },
            sheetState = sheetState
        ) {
            SongActions(
                song = song,
                onPlay = {
                    sheetSong = null
                    media.play(scope, song)
                },
                onShowLyric = {
                    sheetSong = null
                    loadLyric(song)
                },
                onCopyUrl = {
                    sheetSong = null
                    scope.launch {
                        val direct = withContext(Dispatchers.IO) { NeteaseApi.resolveDirectUrl(song.id) }
                        if (direct.isNullOrBlank()) {
                            toast(context, "解析失败：${NeteaseApi.lastError}")
                        } else {
                            copyText(context, "音乐直链", direct)
                        }
                    }
                },
                onDownload = {
                    sheetSong = null
                    scope.launch {
                        val direct = withContext(Dispatchers.IO) { NeteaseApi.resolveDirectUrl(song.id) }
                        if (direct.isNullOrBlank()) {
                            toast(context, "解析失败：${NeteaseApi.lastError}")
                            return@launch
                        }
                        val name = NeteaseApi.fileNameFor(song, ".mp3")
                        com.ncmdump.app.DownloadHelper.download(
                            context,
                            direct,
                            com.ncmdump.app.OutputDir.file(context, name),
                            "https://music.163.com/",
                            song.name
                        )
                        val lyric = withContext(Dispatchers.IO) { NeteaseApi.fetchLyric(song.id) }
                        if (lyric != null && !lyric.isEmpty) {
                            NeteaseApi.saveLyric(context, song, lyric.merged())
                        }
                        toast(context, "已开始下载：$name")
                    }
                }
            )
        }
    }

    val lyricName = lyricTitle
    if (lyricName != null) {
        ModalBottomSheet(onDismissRequest = { lyricTitle = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = lyricName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "歌词已保存到输出目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (lyricLoading) {
                    Text(
                        text = "正在获取歌词…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = lyricText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { lyricTitle = null }) {
                    Text(text = "关闭")
                }
            }
        }
    }
}

@Composable
private fun SongActions(
    song: NeteaseApi.Song,
    onPlay: () -> Unit,
    onShowLyric: () -> Unit,
    onCopyUrl: () -> Unit,
    onDownload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = song.cover,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                fallback = Icons.TwoTone.MusicNote
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SettingRow(
            title = "播放",
            icon = Icons.TwoTone.PlayArrow,
            onClick = onPlay
        )
        SettingRow(
            title = "查看 / 下载歌词",
            subtitle = "原文与翻译合并，保存为 .lrc",
            icon = Icons.TwoTone.Subtitles,
            onClick = onShowLyric
        )
        SettingRow(
            title = "复制音频直链",
            icon = Icons.TwoTone.Search,
            onClick = onCopyUrl
        )
        SettingRow(
            title = "下载音频",
            subtitle = "同时保存同名歌词",
            icon = Icons.TwoTone.Download,
            onClick = onDownload
        )
    }
}
