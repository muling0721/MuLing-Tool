package com.ncmdump.app

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var appBar: AppBarLayout
    private lateinit var rootView: View
    private lateinit var prefs: SharedPreferences

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

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView = findViewById(R.id.mainRoot)
        bottomNav = findViewById(R.id.bottomNav)
        topAppBar = findViewById(R.id.topAppBar)
        appBar = findViewById(R.id.appBar)

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

        if (prefs.getBoolean("auto_update", true)) {
            UpdateChecker.checkUpdate(this, showNoUpdate = false)
        }
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
