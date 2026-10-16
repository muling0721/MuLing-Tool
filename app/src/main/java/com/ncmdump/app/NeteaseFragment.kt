package com.ncmdump.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import kotlin.concurrent.thread

/**
 * 网易云歌曲解析：在线搜索 → 试听 / 复制直链 / 下载
 * 接口参考 reference/netease-music/
 */
class NeteaseFragment : Fragment() {

    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvSongs: RecyclerView
    private lateinit var cardPlayer: View
    private lateinit var ivPlayerCover: ImageView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCopyUrl: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var btnClose: ImageButton

    private lateinit var adapter: SongAdapter
    private var player: MediaPlayer? = null
    private var currentSong: NeteaseApi.Song? = null
    private var isPlaying = false
    private var isPreparing = false
    private var searchToken = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_netease, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etKeyword = view.findViewById(R.id.etKeyword)
        btnSearch = view.findViewById(R.id.btnSearch)
        tvStatus = view.findViewById(R.id.tvNeteaseStatus)
        rvSongs = view.findViewById(R.id.rvSongs)
        cardPlayer = view.findViewById(R.id.cardPlayer)
        ivPlayerCover = view.findViewById(R.id.ivPlayerCover)
        tvPlayerTitle = view.findViewById(R.id.tvPlayerTitle)
        tvPlayerArtist = view.findViewById(R.id.tvPlayerArtist)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnCopyUrl = view.findViewById(R.id.btnCopyUrl)
        btnDownload = view.findViewById(R.id.btnDownload)
        btnClose = view.findViewById(R.id.btnClose)

        adapter = SongAdapter(onPlay = { play(it) }, onCopy = { copyDirect(it) })
        rvSongs.layoutManager = LinearLayoutManager(requireContext())
        rvSongs.adapter = adapter

        btnSearch.setOnClickListener { doSearch() }
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        btnPlayPause.setOnClickListener { togglePlay() }
        btnCopyUrl.setOnClickListener { currentSong?.let { copyDirect(it) } }
        btnDownload.setOnClickListener { currentSong?.let { download(it) } }
        btnClose.setOnClickListener { closePlayer() }
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
                    tvStatus.text = "共 ${list.size} 首 · 点击试听，长按复制直链"
                }
            }
        }
    }

    private fun play(song: NeteaseApi.Song) {
        currentSong = song
        cardPlayer.visibility = View.VISIBLE
        tvPlayerTitle.text = song.name
        tvPlayerArtist.text = song.artist
        loadPlayerCover(song.cover)
        startStream(song)
    }

    private fun startStream(song: NeteaseApi.Song) {
        stopPlayer()
        isPreparing = true
        isPlaying = false
        updatePlayIcon()
        tvStatus.text = "正在加载：${song.name}"

        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        try {
            mp.setDataSource(NeteaseApi.streamUrl(song.id))
        } catch (e: Exception) {
            mp.release()
            toast("播放失败：${e.message}")
            return
        }
        mp.setOnPreparedListener {
            isPreparing = false
            isPlaying = true
            it.start()
            updatePlayIcon()
            tvStatus.text = "正在播放：${song.name}"
        }
        mp.setOnCompletionListener { stopPlayer() }
        mp.setOnErrorListener { _, _, _ ->
            isPreparing = false
            isPlaying = false
            updatePlayIcon()
            toast("播放失败，可能该歌曲无版权或链接失效")
            true
        }
        mp.prepareAsync()
        player = mp
    }

    private fun togglePlay() {
        val mp = player
        if (mp == null) {
            currentSong?.let { startStream(it) }
            return
        }
        if (isPreparing) return
        try {
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                mp.start()
                isPlaying = true
            }
        } catch (_: Exception) {
        }
        updatePlayIcon()
    }

    private fun stopPlayer() {
        isPreparing = false
        isPlaying = false
        val mp = player
        player = null
        if (mp != null) {
            try { mp.stop() } catch (_: Exception) {}
            try { mp.reset() } catch (_: Exception) {}
            mp.release()
        }
        updatePlayIcon()
    }

    private fun closePlayer() {
        stopPlayer()
        currentSong = null
        cardPlayer.visibility = View.GONE
    }

    private fun updatePlayIcon() {
        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun copyDirect(song: NeteaseApi.Song) {
        toast("正在解析直链…")
        thread {
            val direct = NeteaseApi.resolveDirectUrl(song.id) ?: NeteaseApi.streamUrl(song.id)
            activity?.runOnUiThread {
                val cm = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("音乐直链", direct))
                toast("已复制直链")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun download(song: NeteaseApi.Song) {
        try {
            val fileName = buildFileName(song)
            val request = DownloadManager.Request(Uri.parse(NeteaseApi.streamUrl(song.id)))
                .setTitle(song.name)
                .setDescription(song.artist)
                .setMimeType("audio/mpeg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            toast("已加入下载：$fileName")
        } catch (e: Exception) {
            toast("下载失败：${e.message}")
        }
    }

    private fun buildFileName(song: NeteaseApi.Song): String {
        val base = listOf(song.name, song.artist).filter { it.isNotBlank() }.joinToString(" - ")
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "netease_${song.id}" }
        return "$safe.mp3"
    }

    private fun loadPlayerCover(url: String) {
        ivPlayerCover.tag = url
        ivPlayerCover.setImageDrawable(null)
        if (url.isBlank()) return
        thread {
            val bmp = try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                activity?.runOnUiThread {
                    if (ivPlayerCover.tag == url) ivPlayerCover.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun toast(msg: String) {
        activity?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPlayer()
    }
}
