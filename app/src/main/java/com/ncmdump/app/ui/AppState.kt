package com.ncmdump.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ncmdump.app.BuildConfig
import com.ncmdump.app.OutputDir
import com.ncmdump.app.ui.theme.SeedColor

/**
 * 全局 UI 状态：主题、外观、下载目录等偏好设置。
 * 数据仍然保存在 SharedPreferences("ncmdump")，与旧版本保持兼容。
 */
class AppState(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)

    /** system / light / dark */
    var darkMode by mutableStateOf(prefs.getString(KEY_DARK_MODE, "system") ?: "system")
        private set

    var dynamicColor by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC, false))
        private set

    var seed by mutableStateOf(readSeed(prefs))
        private set

    var cardAlpha by mutableFloatStateOf(readCardAlpha(prefs))
        private set

    /** 自定义背景图（content:// 字符串），空字符串表示不使用 */
    var background by mutableStateOf(prefs.getString(KEY_BACKGROUND, "").orEmpty())
        private set

    /** 底栏液态玻璃（关掉以后底栏是纯色，省 GPU） */
    var glassEnabled by mutableStateOf(prefs.getBoolean(KEY_GLASS, true))
        private set

    var blurEnabled by mutableStateOf(prefs.getBoolean(KEY_BLUR, false))
        private set

    var blurStrength by mutableIntStateOf(prefs.getInt(KEY_BLUR_STRENGTH, 45).coerceIn(0, 100))
        private set

    var autoUpdate by mutableStateOf(prefs.getBoolean(KEY_AUTO_UPDATE, !BuildConfig.DEBUG))
        private set

    var outputDirLabel by mutableStateOf(OutputDir.display(context))
        private set

    fun updateDarkMode(value: String) {
        darkMode = value
        prefs.edit().putString(KEY_DARK_MODE, value).apply()
    }

    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun updateSeed(value: SeedColor) {
        seed = value
        prefs.edit()
            .putString(KEY_SEED, value.id)
            .putBoolean(KEY_DYNAMIC, false)
            .apply()
        dynamicColor = false
    }

    fun updateCardAlpha(value: Float) {
        val v = value.coerceIn(0.3f, 1f)
        cardAlpha = v
        prefs.edit()
            .putInt(KEY_CARD_OPACITY, (v * 100).toInt())
            .apply()
    }

    fun updateBackground(uri: String?) {
        val v = uri.orEmpty()
        background = v
        if (v.isBlank()) {
            prefs.edit().remove(KEY_BACKGROUND).apply()
        } else {
            prefs.edit().putString(KEY_BACKGROUND, v).apply()
        }
    }

    fun updateGlassEnabled(enabled: Boolean) {
        glassEnabled = enabled
        prefs.edit().putBoolean(KEY_GLASS, enabled).apply()
    }

    fun updateBlurEnabled(enabled: Boolean) {
        blurEnabled = enabled
        prefs.edit().putBoolean(KEY_BLUR, enabled).apply()
    }

    fun updateBlurStrength(value: Int) {
        val v = value.coerceIn(0, 100)
        blurStrength = v
        prefs.edit().putInt(KEY_BLUR_STRENGTH, v).apply()
    }

    fun updateAutoUpdate(enabled: Boolean) {
        autoUpdate = enabled
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun refreshOutputDir(context: Context) {
        outputDirLabel = OutputDir.display(context)
    }

    private companion object {
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_DYNAMIC = "use_dynamic_color"
        const val KEY_SEED = "theme_seed"
        const val KEY_SEED_LEGACY = "theme_seed_color"
        const val KEY_CARD_OPACITY = "card_opacity"
        const val KEY_BACKGROUND = "custom_background"
        const val KEY_GLASS = "glass_enabled"
        const val KEY_BLUR = "enable_blur"
        const val KEY_BLUR_STRENGTH = "blur_strength"
        const val KEY_AUTO_UPDATE = "auto_update"

        fun readSeed(prefs: SharedPreferences): SeedColor {
            prefs.getString(KEY_SEED, null)?.let { return SeedColor.fromId(it) }
            // 旧版本按 ARGB 整数保存
            val legacy = prefs.getInt(KEY_SEED_LEGACY, 0)
            return when (legacy) {
                0xFF6750A4.toInt() -> SeedColor.Purple
                0xFF2E7D32.toInt() -> SeedColor.Green
                0xFF8C4A60.toInt() -> SeedColor.Pink
                0xFF8B4F24.toInt() -> SeedColor.Orange
                else -> SeedColor.Blue
            }
        }

        fun readCardAlpha(prefs: SharedPreferences): Float {
            val hasBackground = !prefs.getString(KEY_BACKGROUND, "").isNullOrEmpty()
            val default = if (hasBackground) 72 else 100
            return (prefs.getInt(KEY_CARD_OPACITY, default).coerceIn(30, 100)) / 100f
        }
    }
}

val LocalAppState = staticCompositionLocalOf<AppState> {
    error("AppState 尚未提供")
}
