package com.ncmdump.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.View
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

object BackgroundHelper {

    private const val CACHE_FILE = "bg_cache.jpg"
    private const val CACHE_KEY = "bg_cache_key"
    private val tokens = HashMap<View, String>()

    fun apply(view: View, uri: Uri?, blurStrength: Int) {
        val key = "${uri?.toString()}:$blurStrength"
        synchronized(tokens) { tokens[view] = key }

        if (uri == null) {
            view.setBackgroundResource(R.color.bg_main)
            view.foreground = null
            return
        }

        val width = view.width.takeIf { it > 0 }
            ?: view.resources.displayMetrics.widthPixels
        val height = view.height.takeIf { it > 0 }
            ?: view.resources.displayMetrics.heightPixels
        val appContext = view.context.applicationContext
        val fullKey = "$key:$width:$height"

        // 优先用上次处理好的缓存，启动时直接显示，避免等 1 秒
        val cached = loadCache(appContext, fullKey)
        if (cached != null) {
            setBackground(view, cached)
            return
        }

        thread {
            val bitmap = decode(appContext, uri, width, height)
            val shown = when {
                bitmap == null -> null
                blurStrength > 0 -> blurSmall(bitmap, blurStrength)
                else -> bitmap
            }
            if (shown != null) saveCache(appContext, shown, fullKey)

            view.post {
                synchronized(tokens) {
                    if (tokens[view] != key) {
                        shown?.recycle()
                        return@post
                    }
                }
                if (shown == null || shown.isRecycled) {
                    view.setBackgroundResource(R.color.bg_main)
                    view.foreground = null
                } else {
                    setBackground(view, shown)
                }
            }
        }
    }

    private fun setBackground(view: View, bitmap: Bitmap) {
        val layers = arrayOf<Drawable>(
            CenterCropDrawable(bitmap),
            ColorDrawable(ThemeHelper.scrimColor(view))
        )
        view.background = LayerDrawable(layers)
        view.foreground = null
    }

    private fun loadCache(context: Context, key: String): Bitmap? {
        val prefs = context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
        if (prefs.getString(CACHE_KEY, "") != key) return null
        val file = File(context.cacheDir, CACHE_FILE)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun saveCache(context: Context, bitmap: Bitmap, key: String) {
        runCatching {
            val file = File(context.cacheDir, CACHE_FILE)
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
                .edit().putString(CACHE_KEY, key).apply()
        }
    }

    private fun decode(context: Context, uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetW &&
                bounds.outHeight / (sample * 2) >= targetH
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun blurSmall(src: Bitmap, strength: Int): Bitmap {
        val w = (src.width / 8).coerceAtLeast(1)
        val h = (src.height / 8).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        if (small !== src) src.recycle()
        val radius = max(1, min(w, h) * strength.coerceIn(0, 100) / 900)
        val blurred = boxBlur(small, radius)
        if (blurred !== small) small.recycle()
        return blurred
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var a = pixels
        var b = IntArray(w * h)
        repeat(3) {
            blurHorizontal(a, b, w, h, radius)
            blurVertical(b, a, w, h, radius)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                sa += (p ushr 24) and 0xFF
                sr += (p ushr 16) and 0xFF
                sg += (p ushr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or
                    ((sg / div) shl 8) or (sb / div)
                val outP = src[row + (x - r).coerceIn(0, w - 1)]
                val inP = src[row + (x + r + 1).coerceIn(0, w - 1)]
                sa += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                sr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                sg += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                sb += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                sa += (p ushr 24) and 0xFF
                sr += (p ushr 16) and 0xFF
                sg += (p ushr 8) and 0xFF
                sb += p and 0xFF
            }
            for (y in 0 until h) {
                val idx = y * w + x
                dst[idx] = ((sa / div) shl 24) or ((sr / div) shl 16) or
                    ((sg / div) shl 8) or (sb / div)
                val outP = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inP = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                sa += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                sr += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                sg += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                sb += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }
}
