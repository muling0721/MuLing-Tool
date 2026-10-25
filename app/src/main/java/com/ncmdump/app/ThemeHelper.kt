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

object ThemeHelper {

    val DEFAULT_SEED: Int = 0xFF3563E9.toInt()

    fun prefs(context: Context) =
        context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)

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

    fun applyColors(activity: Activity) {
        val p = prefs(activity)
        val dynamic = p.getBoolean("use_dynamic_color", false)
        if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            val name = seedName(p.getInt("theme_seed_color", DEFAULT_SEED))
            activity.theme.applyStyle(overlayFor(name), true)
        }
    }

    private fun overlayFor(name: String): Int = when (name) {
        "purple" -> R.style.ThemeOverlay_LHM_Purple
        "green" -> R.style.ThemeOverlay_LHM_Green
        "pink" -> R.style.ThemeOverlay_LHM_Pink
        "orange" -> R.style.ThemeOverlay_LHM_Orange
        else -> R.style.ThemeOverlay_LHM_Blue
    }

    fun blurStrength(context: Context): Int {
        val p = prefs(context)
        if (!p.getBoolean("enable_blur", false)) return 0
        return p.getInt("blur_strength", 40).coerceIn(0, 100)
    }

    fun themeSignature(context: Context): String {
        val p = prefs(context)
        return buildString {
            append(p.getString("theme_mode", "system"))
            append('|')
            append(p.getBoolean("use_dynamic_color", false))
            append('|')
            append(p.getInt("theme_seed_color", DEFAULT_SEED))
        }
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

    fun cardOpacity(context: Context): Int {
        val p = prefs(context)
        if (p.contains("card_opacity")) return p.getInt("card_opacity", 100)
        val hasBg = !p.getString("custom_background", "").isNullOrEmpty()
        return if (hasBg) 72 else 100
    }

    fun applyCardOpacity(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val opacity = cardOpacity(activity)
        val alpha = (opacity * 255 / 100).coerceIn(0, 255)
        val base = MaterialColors.getColor(
            root,
            com.google.android.material.R.attr.colorSurfaceContainer
        )
        val color = ColorUtils.setAlphaComponent(base, alpha)
        root.post {
            findAllCardViews(root).forEach { it.setCardBackgroundColor(color) }
        }
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

    fun scrimColor(view: View, alpha: Int = 0x80): Int {
        val surface = MaterialColors.getColor(
            view,
            com.google.android.material.R.attr.colorSurface
        )
        return ColorUtils.setAlphaComponent(surface, alpha)
    }
}
