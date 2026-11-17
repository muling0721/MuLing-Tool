package com.ncmdump.app.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.ncmdump.app.ui.LocalAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义背景的缓存：选好图以后解码 / 缩放 / 模糊一次，结果存到 cacheDir，
 * 下次启动直接读这张图，不用重新解码原图也不重复模糊。
 */
object BackgroundCache {

    private const val FILE_NAME = "bg_cache.jpg"
    private const val PREF_KEY = "bg_cache_key"
    private const val MAX_WIDTH = 1080

    private var memoryKey: String? = null
    private var memoryBitmap: Bitmap? = null

    fun load(context: Context, uri: String, blurEnabled: Boolean, blurStrength: Int): Bitmap? {
        if (uri.isBlank()) return null
        val key = "$uri|$blurEnabled|$blurStrength"

        memoryBitmap?.let { if (memoryKey == key && !it.isRecycled) return it }

        val file = File(context.cacheDir, FILE_NAME)
        val prefs = context.getSharedPreferences("ncmdump", Context.MODE_PRIVATE)
        if (prefs.getString(PREF_KEY, null) == key && file.exists()) {
            val cached = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (cached != null) {
                memoryKey = key
                memoryBitmap = cached
                return cached
            }
        }

        val decoded = decode(context, uri) ?: return null
        val result = if (blurEnabled) blur(decoded, blurStrength) else decoded
        if (result !== decoded) decoded.recycle()

        runCatching {
            file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            prefs.edit().putString(PREF_KEY, key).apply()
        }

        memoryKey = key
        memoryBitmap = result
        return result
    }

    private fun decode(context: Context, uri: String): Bitmap? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun blur(src: Bitmap, strength: Int): Bitmap {
        // 缩到一半再模糊，快很多，反正结果本来就是糊的
        val w = (src.width / 2).coerceAtLeast(1)
        val h = (src.height / 2).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val radius = max(1, min(w, h) * strength.coerceIn(0, 100) / 400)
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

/** 背景图：优先用缓存，读不到再去处理原图 */
@Composable
fun rememberWallpaperBackground(): ImageBitmap? {
    val state = LocalAppState.current
    val context = LocalContext.current
    var image by remember(state.background, state.blurEnabled, state.blurStrength) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(state.background, state.blurEnabled, state.blurStrength) {
        if (state.background.isBlank()) {
            image = null
            return@LaunchedEffect
        }
        image = withContext(Dispatchers.IO) {
            BackgroundCache.load(context, state.background, state.blurEnabled, state.blurStrength)
                ?.asImageBitmap()
        }
    }
    return image
}
