package com.ncmdump.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlin.concurrent.thread

/**
 * 网易云歌曲解析：搜索、选择播放、歌词/下载。
 * 播放器 UI 在 MainActivity 中全局显示。
 */
class NeteaseFragment : Fragment() {

    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvSongs: RecyclerView
    private lateinit var etQuality: MaterialAutoCompleteTextView

    private lateinit var adapter: SongAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var currentSong: NeteaseApi.Song? = null
    private var pendingSong: NeteaseApi.Song? = null
    private var searchToken = 0
    private var playToken = 0

    private val qualityLabels = arrayOf("标准音质", "无损音质", "Hi-Res音质", "沉浸环绕声", "高清环绕声", "超清母带")
    private val qualityValues = arrayOf("standard", "lossless", "hires", "sky", "jyeffect", "jymaster")

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val name = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            if (name.isNotEmpty()) {
                tvStatus.text = if (isPlaying) "正在播放：$name" else "已暂停：$name"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_netease, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etKeyword = view.findViewById(R.id.etKeyword)
        etQuality = view.findViewById(R.id.etQuality)
        btnSearch = view.findViewById(R.id.btnSearch)
        tvStatus = view.findViewById(R.id.tvNeteaseStatus)
        rvSongs = view.findViewById(R.id.rvSongs)

        adapter = SongAdapter(onPlay = { play(it) }, onMore = { showSongMenu(it) })
        rvSongs.layoutManager = LinearLayoutManager(requireContext())
        rvSongs.adapter = adapter

        btnSearch.setOnClickListener { doSearch() }
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        setupQuality()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(
            requireContext(),
            ComponentName(requireContext(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (_: Exception) {
                null
            } ?: return@addListener
            controller = c
            c.addListener(controllerListener)

            c.currentMediaItem?.let { item ->
                val md = item.mediaMetadata
                val name = md.title?.toString().orEmpty()
                currentSong = NeteaseApi.Song(
                    id = item.mediaId,
                    name = name,
                    artist = md.artist?.toString().orEmpty(),
                    album = "",
                    cover = md.artworkUri?.toString().orEmpty(),
                    durationMs = 0L
                )
                tvStatus.text = if (c.isPlaying) "正在播放：$name" else "已暂停：$name"
            }

            pendingSong?.let { song ->
                pendingSong = null
                play(song)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.removeListener(controllerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun doSearch() {
        val keyword = etKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            toast("请输入关键词")
            return
        }
        tvStatus.text = "搜索中…"
        val token = ++searchToken
        thread {
            val list = NeteaseApi.search(keyword)
            activity?.runOnUiThread {
                if (token != searchToken) return@runOnUiThread
                if (list.isEmpty()) {
                    adapter.submit(emptyList())
                    tvStatus.text = if (NeteaseApi.lastError.isNotEmpty()) {
                        "搜索失败：${NeteaseApi.lastError}"
                    } else {
                        "没有找到相关歌曲"
                    }
                } else {
                    adapter.submit(list)
                    tvStatus.text = "共 ${list.size} 首 · 点击试听，长按查看更多"
                }
            }
        }
    }

    private fun setupQuality() {
        etQuality.setSimpleItems(qualityLabels)
        val prefs = requireContext().getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
        val saved = prefs.getString("netease_quality", "standard") ?: "standard"
        val index = qualityValues.indexOf(saved).coerceAtLeast(0)
        etQuality.setText(qualityLabels[index], false)
        NeteaseApi.level = qualityValues[index]
        etQuality.setOnItemClickListener { _, _, position, _ ->
            NeteaseApi.level = qualityValues[position]
            prefs.edit().putString("netease_quality", qualityValues[position]).apply()
        }
    }

    private fun play(song: NeteaseApi.Song) {
        val c = controller
        if (c == null) {
            pendingSong = song
            toast("正在初始化播放器…")
            return
        }
        ensureNotificationPermission()

        currentSong = song
        tvStatus.text = "正在解析：${song.name}"

        val token = ++playToken
        thread {
            val url = NeteaseApi.resolveDirectUrl(song.id)
            activity?.runOnUiThread {
                if (token != playToken) return@runOnUiThread
                if (!c.isConnected) return@runOnUiThread
                if (url.isNullOrBlank()) {
                    tvStatus.text = "解析失败：${NeteaseApi.lastError.ifEmpty { "该歌曲暂不可播放" }}"
                    toast("解析失败")
                    return@runOnUiThread
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
                tvStatus.text = "正在加载：${song.name}"
            }
        }
    }

    private fun showSongMenu(song: NeteaseApi.Song) {
        val options = arrayOf("播放", "下载歌词", "复制音频直链", "下载音频")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (song.artist.isBlank()) song.name else "${song.name} - ${song.artist}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> play(song)
                    1 -> downloadLyric(song)
                    2 -> copyDirect(song)
                    3 -> download(song)
                }
            }
            .show()
    }

    private fun downloadLyric(song: NeteaseApi.Song) {
        toast("正在获取歌词…")
        thread {
            val lyric = NeteaseApi.fetchLyric(song.id)
            activity?.runOnUiThread {
                if (lyric == null || lyric.isEmpty) {
                    toast("未找到该歌曲的歌词")
                    return@runOnUiThread
                }
                val content = lyric.merged()
                val path = NeteaseApi.saveLyric(requireContext(), song, content)
                if (path != null) {
                    toast("歌词已保存：${File(path).name}")
                } else {
                    toast("歌词保存失败")
                }
                showLyricDialog(song, content)
            }
        }
    }

    private fun showLyricDialog(song: NeteaseApi.Song, content: String) {
        val view = layoutInflater.inflate(R.layout.dialog_lyric, null)
        view.findViewById<TextView>(R.id.tvLyricContent).text = content
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.name)
            .setView(view)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun copyDirect(song: NeteaseApi.Song) {
        toast("正在解析直链…")
        thread {
            val direct = NeteaseApi.resolveDirectUrl(song.id)
            activity?.runOnUiThread {
                if (direct.isNullOrBlank()) {
                    toast("解析失败：${NeteaseApi.lastError.ifEmpty { "该歌曲暂不可播放" }}")
                    return@runOnUiThread
                }
                val cm = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("音乐直链", direct))
                toast("已复制直链")
            }
        }
    }

    private fun download(song: NeteaseApi.Song) {
        val ctx = context ?: return
        toast("正在解析下载地址…")
        thread {
            val direct = NeteaseApi.resolveDirectUrl(song.id)
            activity?.runOnUiThread {
                if (direct.isNullOrBlank()) {
                    toast("解析失败：${NeteaseApi.lastError.ifEmpty { "该歌曲暂不可播放" }}")
                    return@runOnUiThread
                }
                val fileName = NeteaseApi.fileNameFor(song, ".mp3")
                DownloadHelper.download(ctx, direct, OutputDir.file(ctx, fileName), "https://music.163.com/")
                toast("已开始下载：$fileName")
            }
        }
        thread {
            val lyric = NeteaseApi.fetchLyric(song.id) ?: return@thread
            if (!lyric.isEmpty) {
                NeteaseApi.saveLyric(ctx, song, lyric.merged())
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) {
        activity?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }
}
