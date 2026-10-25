package com.ncmdump.app

import android.content.Context
import java.io.File

/**
 * 统一输出目录：只在设置页选择一次，所有功能共用。
 */
object OutputDir {

    private const val PREF = "output_dir"
    private const val LEGACY = "last_output_dir"

    fun get(context: Context): File {
        val prefs = context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF, "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString(LEGACY, "")?.takeIf { it.isNotBlank() }
        if (!saved.isNullOrBlank()) {
            val dir = File(saved)
            if (dir.isDirectory || dir.mkdirs()) return dir
        }
        val fallback = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(fallback, "MuLing")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun set(context: Context, path: String) {
        context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF, path)
            .putString(LEGACY, path)
            .apply()
    }

    fun file(context: Context, name: String): File = File(get(context), name)

    fun display(context: Context): String {
        val prefs = context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF, "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString(LEGACY, "")?.takeIf { it.isNotBlank() }
        return if (saved.isNullOrBlank()) "默认（应用目录）" else saved
    }
}
