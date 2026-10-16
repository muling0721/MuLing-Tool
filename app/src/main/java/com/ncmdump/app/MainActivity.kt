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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var rootView: View

    private val tabTitles = arrayOf("音频处理", "视频处理", "设置")
    private lateinit var prefs: SharedPreferences

    private val audioFragment = AudioFragment()
    private val videoFragment = VideoFragment()
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

    private val pickDir = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            audioFragment.onDirSelected(it)
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

        applyGlassEffect(bottomNav)
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
                R.id.nav_settings -> { switchFragment(settingsFragment, 2); true }
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
        // 实时应用卡片不透明度、背景暗度、模糊
        applyCardOpacity()
        applyCustomBackground()
        applyGlassEffect(bottomNav)
    }

    private fun applyThemeMode(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun applyGlassEffect(view: View) {
        view.elevation = 8f
        // 使用 Material 3 表面色作为导航栏背景，呈现 KernelSU 同款效果
        val color = com.google.android.material.color.MaterialColors.getColor(
            view, com.google.android.material.R.attr.colorSurfaceContainer
        )
        view.setBackgroundColor(color)
    }

    /**
     * 实时应用卡片不透明度：遍历所有 CardView 修改背景
     */
    private fun applyCardOpacity() {
        val opacity = prefs.getInt("card_opacity", 100)
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
        listOf(audioFragment, videoFragment, settingsFragment).forEach { fragment ->
            if (fragment.isAdded) {
                fragment.view?.let { view ->
                    applyBackgroundToView(view, bgUri)
                }
            }
        }
    }

    fun applyBackgroundToView(view: View, bgUri: Uri?) {
        val maskAlpha = 0

        if (bgUri != null) {
            try {
                val inputStream = contentResolver.openInputStream(bgUri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                    view.background = drawable
                    view.foreground = android.graphics.drawable.ColorDrawable(
                        Color.argb(maskAlpha, 255, 255, 255)
                    )
                }
            } catch (_: Exception) {
                view.setBackgroundResource(R.color.bg_main)
                view.foreground = null
            }
        } else {
            view.setBackgroundResource(R.color.bg_main)
            view.foreground = null
        }
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

    fun openDirPicker() {
        try { pickDir.launch(null) }
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

        listOf(audioFragment, videoFragment, settingsFragment).forEach {
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
