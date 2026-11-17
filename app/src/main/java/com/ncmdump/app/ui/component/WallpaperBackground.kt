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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义背景图：解码 → 缩放 → CPU 模糊一次，之后直接当位图画。
 * 之前用 Modifier.blur 是每帧跑一次 RenderEffect，全屏模糊很吃 GPU，
 * 预处理好以后背景绘制就是一张贴图。
 */
@Composable
fun rememberWallpaper(uri: String, blurEnabled: Boolean, blurStrength: Int): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(uri, blurEnabled, blurStrength) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, blurEnabled, blurStrength) {
        image = withContext(Dispatchers.IO) {
            loadWallpaper(context, uri, blurEnabled, blurStrength)
        }
    }
    return image
}

private fun loadWallpaper(
    context: Context,
    uri: String,
    blurEnabled: Boolean,
    blurStrength: Int
): ImageBitmap? {
    if (uri.isBlank()) return null
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 720) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = runCatching {
        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null

    // 模糊只需要很小的图，缩到 1/3 再模糊，速度快、效果够
    val scale = if (blurEnabled) 3 else 1
    val targetW = (decoded.width / scale).coerceAtLeast(1)
    val targetH = (decoded.height / scale).coerceAtLeast(1)
    val small = if (targetW != decoded.width) {
        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }
    if (!blurEnabled) return small.asImageBitmap()

    val radius = max(1, min(small.width, small.height) * blurStrength.coerceIn(0, 100) / 400)
    val blurred = boxBlur(small, radius)
    if (blurred !== small) small.recycle()
    return blurred.asImageBitmap()
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
