package com.ncmdump.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun copyText(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText(label, text))
    toast(context, "$label 已复制")
}

fun toast(context: Context, message: String) {
    runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}

/** 过滤文件名中的非法字符 */
fun safeFileName(raw: String, fallback: String, limit: Int = 60): String {
    val safe = raw.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(limit)
    return safe.ifBlank { fallback }
}
