package com.ncmdump.app

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

/**
 * 主题、透明度、背景相关的统一入口。
 * 颜色板使用 seed 生成的 TonalSpot 方案，与 BakaSU 的做法一致。
 */
object ThemeHelper {

    val DEFAULT_SEED: Int = 0xFF3563E9.toInt()
    private const val PREFS = "ncmdump"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun applyNightMode(activity: Activity) {
        val mode = prefs(activity).getString("theme_mode", "system")
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun useDynamic(context: Context): Boolean =
        prefs(context).getBoolean("use_dynamic_color", false) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun applyDynamic(activity: Activity) {
        if (useDynamic(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun applyPalette(activity: Activity) {
        if (useDynamic(activity)) return
        val name = seedName(prefs(activity).getInt("theme_seed_color", DEFAULT_SEED))
        activity.theme.applyStyle(overlayFor(name), true)
    }

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun setSeedColor(context: Context, seed: Int) {
        prefs(context).edit()
            .putBoolean("use_dynamic_color", false)
            .putInt("theme_seed_color", seed)
            .apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("use_dynamic_color", enabled).apply()
    }

    fun seedFor(name: String): Int = when (name) {
        "purple" -> 0xFF6750A4.toInt()
        "green" -> 0xFF2E7D32.toInt()
        "pink" -> 0xFF8C4A60.toInt()
        "orange" -> 0xFF8B4F24.toInt()
        else -> DEFAULT_SEED
    }

    fun seedName(seed: Int): String = when (seed) {
        0xFF6750A4.toInt() -> "purple"
        0xFF2E7D32.toInt() -> "green"
        0xFF8C4A60.toInt() -> "pink"
        0xFF8B4F24.toInt() -> "orange"
        else -> "blue"
    }

    fun cardOpacity(context: Context): Int {
        val p = prefs(context)
        if (p.contains("card_opacity")) return p.getInt("card_opacity", 100)
        val hasBg = !p.getString("custom_background", "").isNullOrEmpty()
        return if (hasBg) 72 else 100
    }

    fun blurStrength(context: Context): Int {
        val p = prefs(context)
        if (!p.getBoolean("enable_blur", false)) return 0
        return p.getInt("blur_strength", 40).coerceIn(0, 100)
    }

    fun applyCardOpacity(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val alpha = (cardOpacity(activity) * 255 / 100).coerceIn(0, 255)
        val base = MaterialColors.getColor(
            root,
            com.google.android.material.R.attr.colorSurfaceContainer
        )
        val color = ColorUtils.setAlphaComponent(base, alpha)
        root.post {
            findAllCardViews(root).forEach { it.setCardBackgroundColor(color) }
        }
    }

    fun scrimColor(view: View, alpha: Int = 0x80): Int {
        val surface = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorSurface
        )
        return ColorUtils.setAlphaComponent(surface, alpha)
    }

    private fun overlayFor(name: String): Int = when (name) {
        "purple" -> R.style.ThemeOverlay_LHM_Purple
        "green" -> R.style.ThemeOverlay_LHM_Green
        "pink" -> R.style.ThemeOverlay_LHM_Pink
        "orange" -> R.style.ThemeOverlay_LHM_Orange
        else -> R.style.ThemeOverlay_LHM_Blue
    }

    private fun findAllCardViews(view: View): List<androidx.cardview.widget.CardView> {
        val result = mutableListOf<androidx.cardview.widget.CardView>()
        if (view is androidx.cardview.widget.CardView) result.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findAllCardViews(view.getChildAt(i)))
            }
        }
        return result
    }
}
