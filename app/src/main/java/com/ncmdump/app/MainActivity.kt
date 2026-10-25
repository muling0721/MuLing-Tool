package com.ncmdump.app

import android.Manifest
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var appBar: AppBarLayout
    private lateinit var rootView: View
    private lateinit var prefs: SharedPreferences

    private lateinit var globalPlayer: View
    private lateinit var glCover: ImageView
    private lateinit var glTitle: TextView
    private lateinit var glArtist: TextView
    private lateinit var glPlayPause: ImageButton

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var playerClosed = false

    private val tabTitles = arrayOf("音频处理", "视频处理", "短视频", "设置")
    private val audioFragment = AudioFragment()
    private val videoFragment = VideoFragment()
    private val shortVideoFragment = ShortVideoFragment()
    private val settingsFragment = SettingsFragment()

    private var currentTabIndex = 0

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) audioFragment.onFilesSelected(uris)
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateGlobalPlayer()
        override fun onPlaybackStateChanged(playbackState: Int) = updateGlobalPlayer()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateGlobalPlayer()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) playerClosed = false
            updateGlobalPlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView = findViewById(R.id.mainRoot)
        bottomNav = findViewById(R.id.bottomNav)
        topAppBar = findViewById(R.id.topAppBar)
        appBar = findViewById(R.id.appBar)

        globalPlayer = findViewById(R.id.globalPlayer)
        glCover = findViewById(R.id.glCover)
        glTitle = findViewById(R.id.glTitle)
        glArtist = findViewById(R.id.glArtist)
        glPlayPause = findViewById(R.id.glPlayPause)

        glPlayPause.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
            updateGlobalPlayer()
        }
        findViewById<View>(R.id.glDownload).setOnClickListener { downloadCurrentSong() }
        findViewById<View>(R.id.glClose).setOnClickListener {
            controller?.apply {
                stop()
                clearMediaItems()
            }
            playerClosed = true
            updateGlobalPlayer()
        }

        applyChrome()
        applyCardOpacity()

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                private var lastBack = 0L

                override fun handleOnBackPressed() {
                    val audio = supportFragmentManager.fragments
                        .find { it is AudioFragment } as? AudioFragment
                    if (currentTabIndex == 0 && audio != null && audio.goBack()) return
                    if (currentTabIndex != 0) {
                        bottomNav.selectedItemId = R.id.nav_audio
                        return
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastBack < 2000) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        lastBack = now
                        Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        currentTabIndex = savedInstanceState?.getInt("tab", 0) ?: 0
        if (savedInstanceState == null) {
            switchFragment(audioFragment, 0)
        } else {
            topAppBar.title = tabTitles.getOrElse(currentTabIndex) { getString(R.string.app_name) }
            bottomNav.selectedItemId = menuIdOf(currentTabIndex)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_audio -> { switchFragment(audioFragment, 0); true }
                R.id.nav_video -> { switchFragment(videoFragment, 1); true }
                R.id.nav_short -> { switchFragment(shortVideoFragment, 2); true }
                R.id.nav_settings -> { switchFragment(settingsFragment, 3); true }
                else -> false
            }
        }

        applyCustomBackground()
        bottomNav.post { positionGlobalPlayer() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (prefs.getBoolean("auto_update", true)) {
            UpdateChecker.checkUpdate(this, showNoUpdate = false)
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (_: Exception) {
                null
            } ?: return@addListener
            controller = c
            c.addListener(playerListener)
            updateGlobalPlayer()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTabIndex)
    }

    override fun onResume() {
        super.onResume()
        applyCardOpacity()
        applyCustomBackground()
        applyChrome()
        positionGlobalPlayer()
    }

    fun applyCardOpacity() {
        ThemeHelper.applyCardOpacity(this)
    }

    fun applyCustomBackground() {
        BackgroundHelper.apply(rootView, customBackgroundUri(), ThemeHelper.blurStrength(this))
    }

    fun customBackgroundUri(): Uri? {
        val value = prefs.getString("custom_background", "")
        return if (value.isNullOrEmpty()) null else runCatching { Uri.parse(value) }.getOrNull()
    }

    fun openFilePicker() {
        runCatching { pickFiles.launch(arrayOf("*/*")) }
    }

    private fun updateGlobalPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null || playerClosed) {
            globalPlayer.visibility = View.GONE
            return
        }
        globalPlayer.visibility = View.VISIBLE
        glTitle.text = item.mediaMetadata.title?.toString().orEmpty()
        glArtist.text = item.mediaMetadata.artist?.toString().orEmpty()
        glPlayPause.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        loadGlobalCover(item.mediaMetadata.artworkUri?.toString().orEmpty())
    }

    private fun downloadCurrentSong() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        if (item.mediaId.isBlank()) return
        val md = item.mediaMetadata
        val song = NeteaseApi.Song(
            id = item.mediaId,
            name = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = "",
            cover = md.artworkUri?.toString().orEmpty(),
            durationMs = 0L
        )
        Toast.makeText(this, "正在解析下载地址…", Toast.LENGTH_SHORT).show()
        thread {
            val direct = NeteaseApi.resolveDirectUrl(song.id)
            runOnUiThread {
                if (direct.isNullOrBlank()) {
                    Toast.makeText(this, "下载失败：${NeteaseApi.lastError}", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val name = NeteaseApi.fileNameFor(song, ".mp3")
                DownloadHelper.download(
                    this,
                    direct,
                    OutputDir.file(this, name),
                    "https://music.163.com/",
                    song.name
                )
            }
            val lyric = NeteaseApi.fetchLyric(song.id)
            if (lyric != null && !lyric.isEmpty) {
                NeteaseApi.saveLyric(this, song, lyric.merged())
            }
        }
    }

    private fun loadGlobalCover(url: String) {
        glCover.tag = url
        glCover.setImageDrawable(null)
        if (url.isBlank()) return
        thread {
            val bitmap = runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    if (glCover.tag == url) glCover.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun positionGlobalPlayer() {
        if (!::globalPlayer.isInitialized || bottomNav.height <= 0) return
        val lp = globalPlayer.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.bottomMargin = bottomNav.height + dp(12)
        globalPlayer.layoutParams = lp
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applyChrome() {
        val surface = com.google.android.material.color.MaterialColors.getColor(
            rootView,
            com.google.android.material.R.attr.colorSurface
        )
        val container = com.google.android.material.color.MaterialColors.getColor(
            rootView,
            com.google.android.material.R.attr.colorSurfaceContainer
        )
        val barColor = if (customBackgroundUri() != null) {
            androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 0xE6)
        } else {
            surface
        }
        appBar.setBackgroundColor(barColor)
        topAppBar.setBackgroundColor(barColor)

        val alpha = (ThemeHelper.cardOpacity(this) * 255 / 100).coerceIn(0, 255)
        val navColor = androidx.core.graphics.ColorUtils.setAlphaComponent(container, alpha)
        bottomNav.backgroundTintList = android.content.res.ColorStateList.valueOf(navColor)
        bottomNav.elevation = 8f
    }

    private fun switchFragment(fragment: Fragment, index: Int) {
        val tx = supportFragmentManager.beginTransaction()
        if (index > currentTabIndex) {
            tx.setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
        } else {
            tx.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        currentTabIndex = index
        topAppBar.title = tabTitles.getOrElse(index) { getString(R.string.app_name) }
        if (fragment !is AudioFragment) showAudioBack(false)

        listOf(audioFragment, videoFragment, shortVideoFragment, settingsFragment).forEach {
            if (it.isAdded) tx.hide(it)
        }
        if (fragment.isAdded) tx.show(fragment) else tx.add(R.id.fragmentContainer, fragment)
        tx.commit()

        rootView.post {
            applyCardOpacity()
            if (fragment is AudioFragment) fragment.refreshToolbar()
        }
    }

    private fun menuIdOf(index: Int): Int = when (index) {
        0 -> R.id.nav_audio
        1 -> R.id.nav_video
        2 -> R.id.nav_short
        else -> R.id.nav_settings
    }

    fun showAudioBack(show: Boolean) {
        if (show) {
            topAppBar.title = ""
            topAppBar.setNavigationIcon(R.drawable.ic_arrow_back)
            topAppBar.setNavigationOnClickListener { audioFragment.goBack() }
        } else {
            topAppBar.navigationIcon = null
            topAppBar.setNavigationOnClickListener(null)
            topAppBar.title = tabTitles.getOrElse(currentTabIndex) { getString(R.string.app_name) }
        }
    }
}
