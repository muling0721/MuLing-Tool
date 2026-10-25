package com.ncmdump.app

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

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
    private var currentThemeMode: String = "system"

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
        currentThemeMode = prefs.getString("theme_mode", "system") ?: "system"
        applyThemeMode(currentThemeMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView = findViewById(android.R.id.content)
        bottomNav = findViewById(R.id.bottomNav)
        topAppBar = findViewById(R.id.topAppBar)
        appBar = findViewById(R.id.appBar)

        applyChrome()
        applyCardOpacity()

        // 返回键处理：先退出音频二级页面
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val audioFrag = supportFragmentManager.fragments.find { it is AudioFragment } as? AudioFragment
                if (audioFrag != null && audioFrag.goBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
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
        val newMode = prefs.getString("theme_mode", "system") ?: "system"
        if (newMode != currentThemeMode) {
            currentThemeMode = newMode
            recreate()
            return
        }
        // 实时应用卡片不透明度、背景遮罩、悬浮栏透明度
        applyCardOpacity()
        applyCustomBackground()
        applyChrome()
    }

    private fun applyThemeMode(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
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

        if (hasBg) {
            val barColor = androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 0xE6)
            appBar.setBackgroundColor(barColor)
            topAppBar.setBackgroundColor(barColor)
            bottomNav.setBackgroundColor(
                androidx.core.graphics.ColorUtils.setAlphaComponent(container, 0xE6)
            )
            bottomNav.elevation = 8f
        } else {
            appBar.setBackgroundColor(surface)
            topAppBar.setBackgroundColor(surface)
            bottomNav.setBackgroundColor(container)
            bottomNav.elevation = 3f
        }
    }

    /**
     * 实时应用卡片不透明度：遍历所有 CardView 修改背景。
     * 有自定义背景时默认更透明（72%），避免卡片完全挡住背景图。
     */
    fun applyCardOpacity() {
        val hasBg = getCustomBackgroundUri() != null
        val opacity = prefs.getInt("card_opacity", if (hasBg) 72 else 100)
        val alpha = (opacity * 255 / 100).coerceIn(0, 255)
        // 基于主题表面色应用不透明度，保持 Material 3 观感
        val base = com.google.android.material.color.MaterialColors.getColor(
            rootView, com.google.android.material.R.attr.colorSurfaceContainer
        )
        val cardColor = androidx.core.graphics.ColorUtils.setAlphaComponent(base, alpha)

        rootView.post {
            findAllCardViews(rootView).forEach { cardView ->
                cardView.setCardBackgroundColor(cardColor)
            }
        }
    }

    private fun findAllCardViews(view: View): List<CardView> {
        val result = mutableListOf<CardView>()
        if (view is CardView) {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findAllCardViews(view.getChildAt(i)))
            }
        }
        return result
    }

    fun applyCustomBackground() {
        val bgUri = getCustomBackgroundUri()
        listOf(audioFragment, videoFragment, shortVideoFragment, settingsFragment).forEach { fragment ->
            if (fragment.isAdded) {
                fragment.view?.let { view ->
                    applyBackgroundToView(view, bgUri)
                }
            }
        }
    }

    fun applyBackgroundToView(view: View, bgUri: Uri?) {
        if (bgUri != null) {
            try {
                val inputStream = contentResolver.openInputStream(bgUri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val bitmapDrawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                    bitmapDrawable.gravity = android.view.Gravity.FILL
                    // 主题色半透明遮罩：让任意明暗的背景图都能与 M3 配色融合，并保证前景内容可读
                    val surface = com.google.android.material.color.MaterialColors.getColor(
                        view, com.google.android.material.R.attr.colorSurface
                    )
                    val scrim = android.graphics.drawable.ColorDrawable(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 0x5A)
                    )
                    // 先铺图片，再叠遮罩，最后才是内容（background 永远在子 View 之下）
                    view.background = android.graphics.drawable.LayerDrawable(
                        arrayOf(bitmapDrawable, scrim)
                    )
                    view.foreground = null
                    return
                }
            } catch (_: Exception) {
                // 回退到默认背景
            }
        }
        view.setBackgroundResource(R.color.bg_main)
        view.foreground = null
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
            fragment.view?.let { view ->
                applyBackgroundToView(view, getCustomBackgroundUri())
            }
            applyCardOpacity()
        }
    }
}
