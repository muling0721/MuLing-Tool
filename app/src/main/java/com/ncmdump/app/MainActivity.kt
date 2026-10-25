package com.ncmdump.app

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var appBar: AppBarLayout
    private lateinit var rootView: View

    private val tabTitles = arrayOf("音频处理", "视频处理", "短视频", "设置")
    private lateinit var prefs: SharedPreferences

    private val audioFragment = AudioFragment()
    private val videoFragment = VideoFragment()
    private val shortVideoFragment = ShortVideoFragment()
    private val settingsFragment = SettingsFragment()

    // 记录当前应用的主题设置，用于检测变化
    private var themeSignature: String = ""

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            audioFragment.onFilesSelected(uris)
        }
    }

    private val pickBackground = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.edit().putString("custom_background", it.toString()).apply()
            applyCustomBackground()
        }
    }

    private val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.fragmentContainer)

    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        themeSignature = ThemeHelper.themeSignature(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView = findViewById(android.R.id.content)
        bottomNav = findViewById(R.id.bottomNav)
        topAppBar = findViewById(R.id.topAppBar)
        appBar = findViewById(R.id.appBar)

        applyChrome()
        applyCardOpacity()

        // 返回键：二级页 → 返回上一页；其它标签 → 回音频页；音频首页 → 再按一次退出
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            private var lastBack = 0L

            override fun handleOnBackPressed() {
                val audioFrag = supportFragmentManager.fragments.find { it is AudioFragment } as? AudioFragment
                if (currentTabIndex == 0 && audioFrag != null && audioFrag.goBack()) return
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
        })

        if (savedInstanceState == null) {
            switchFragment(audioFragment, 0)
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

        // 自动检测更新
        if (prefs.getBoolean("auto_update", true)) {
            UpdateChecker.checkUpdate(this, showNoUpdate = false)
        }
    }

    override fun onResume() {
        super.onResume()
        // 检测主题模式变化，变化则 recreate
        val newSignature = ThemeHelper.themeSignature(this)
        if (newSignature != themeSignature) {
            themeSignature = newSignature
            recreate()
            return
        }
        // 实时应用卡片不透明度、背景遮罩、悬浮栏透明度
        applyCardOpacity()
        applyCustomBackground()
        applyChrome()
    }

    /**
     * 顶栏 / 底部导航栏：有自定义背景时半透明，让背景图透出来；无背景时使用纯主题色。
     */
    private fun applyChrome() {
        val hasBg = getCustomBackgroundUri() != null
        val surface = com.google.android.material.color.MaterialColors.getColor(
            rootView, com.google.android.material.R.attr.colorSurface
        )
        val container = com.google.android.material.color.MaterialColors.getColor(
            rootView, com.google.android.material.R.attr.colorSurfaceContainer
        )

        val barColor = if (hasBg) {
            androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 0xE6)
        } else {
            surface
        }
        appBar.setBackgroundColor(barColor)
        topAppBar.setBackgroundColor(barColor)

        val opacity = ThemeHelper.cardOpacity(this)
        val navAlpha = (opacity * 255 / 100).coerceIn(0, 255)
        val navColor = androidx.core.graphics.ColorUtils.setAlphaComponent(container, navAlpha)
        bottomNav.backgroundTintList = android.content.res.ColorStateList.valueOf(navColor)
        bottomNav.elevation = 8f
    }

    /**
     * 实时应用卡片不透明度：遍历所有 CardView 修改背景。
     * 有自定义背景时默认更透明（72%），避免卡片完全挡住背景图。
     */
    fun applyCardOpacity() {
        ThemeHelper.applyCardOpacity(this)
    }

    fun applyCustomBackground() {
        BackgroundHelper.apply(rootView, getCustomBackgroundUri(), ThemeHelper.blurStrength(this))
    }

    fun getCustomBackgroundUri(): Uri? {
        val uriStr = prefs.getString("custom_background", "")
        return if (!uriStr.isNullOrEmpty()) {
            try { Uri.parse(uriStr) } catch (_: Exception) { null }
        } else null
    }

    fun clearCustomBackground() {
        prefs.edit().remove("custom_background").apply()
        applyCustomBackground()
    }

    fun openBackgroundPicker() {
        try { pickBackground.launch(arrayOf("image/*")) }
        catch (e: Exception) { e.printStackTrace() }
    }

    fun openFilePicker() {
        try { pickFiles.launch(arrayOf("*/*")) }
        catch (e: Exception) { e.printStackTrace() }
    }

    private fun switchFragment(fragment: Fragment, targetIndex: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        if (targetIndex > currentTabIndex) {
            transaction.setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        currentTabIndex = targetIndex
        topAppBar.title = tabTitles.getOrElse(targetIndex) { getString(R.string.app_name) }
        if (fragment !is AudioFragment) showAudioBack(false)

        listOf(audioFragment, videoFragment, shortVideoFragment, settingsFragment).forEach {
            if (it.isAdded) transaction.hide(it)
        }
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragmentContainer, fragment)
        }
        transaction.commit()

        rootView.post {
            applyCardOpacity()
            if (fragment is AudioFragment) fragment.refreshToolbar()
        }
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
