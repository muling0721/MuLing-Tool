package com.ncmdump.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ncmdump.app.DownloadHelper
import com.ncmdump.app.NeteaseApi
import com.ncmdump.app.OutputDir
import com.ncmdump.app.PlaybackService
import com.ncmdump.app.ui.component.NowPlayingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局媒体会话状态：连接 PlaybackService，驱动迷你播放器与网易云页面。
 */
class MediaSessionState {

    var info by mutableStateOf<NowPlayingInfo?>(null)
        private set

    var status by mutableStateOf("")
        private set

    var ready by mutableStateOf(false)
        private set

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var closed = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) closed = false
            refresh()
        }
    }

    fun attach(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            ready = true
            c.addListener(listener)
            refresh()
        }, MoreExecutors.directExecutor())
    }

    fun detach() {
        controller?.removeListener(listener)
        controller = null
        ready = false
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            info = null
            return
        }
        val item = c.currentMediaItem
        if (item == null || closed) {
            info = null
            return
        }
        val md = item.mediaMetadata
        info = NowPlayingInfo(
            id = item.mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            cover = md.artworkUri?.toString().orEmpty(),
            isPlaying = c.isPlaying
        )
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        refresh()
    }

    fun close() {
        controller?.apply {
            stop()
            clearMediaItems()
        }
        closed = true
        refresh()
    }

    fun play(scope: CoroutineScope, song: NeteaseApi.Song) {
        val c = controller
        if (c == null) {
            status = "播放器初始化中，请稍候…"
            return
        }
        closed = false
        status = "正在解析：${song.name}"
        scope.launch {
            val url = withContext(Dispatchers.IO) { NeteaseApi.resolveDirectUrl(song.id) }
            if (url.isNullOrBlank()) {
                status = "解析失败：${NeteaseApi.lastError.ifBlank { "该歌曲暂不可播放" }}"
                return@launch
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.artist)
                .apply { if (song.cover.isNotEmpty()) setArtworkUri(Uri.parse(song.cover)) }
                .build()
            val item = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
            status = "正在加载：${song.name}"
        }
    }

    /** 下载当前播放的歌曲（含同名歌词） */
    fun downloadCurrent(context: Context, scope: CoroutineScope) {
        val data = info ?: return
        val song = NeteaseApi.Song(
            id = data.id,
            name = data.title,
            artist = data.artist,
            album = "",
            cover = data.cover,
            durationMs = 0L
        )
        scope.launch {
            status = "正在解析下载地址…"
            val direct = withContext(Dispatchers.IO) { NeteaseApi.resolveDirectUrl(song.id) }
            if (direct.isNullOrBlank()) {
                status = "下载失败：${NeteaseApi.lastError.ifBlank { "无法解析地址" }}"
                return@launch
            }
            val name = NeteaseApi.fileNameFor(song, ".mp3")
            DownloadHelper.download(
                context,
                direct,
                OutputDir.file(context, name),
                "https://music.163.com/",
                song.name
            )
            val lyric = withContext(Dispatchers.IO) { NeteaseApi.fetchLyric(song.id) }
            if (lyric != null && !lyric.isEmpty) {
                NeteaseApi.saveLyric(context, song, lyric.merged())
            }
            status = "已开始下载：$name"
        }
    }
}

val LocalMediaSession = staticCompositionLocalOf<MediaSessionState> {
    error("MediaSessionState 尚未提供")
}
