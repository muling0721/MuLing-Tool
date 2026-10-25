package com.ncmdump.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * 把图片按控件尺寸居中裁剪铺满，避免被拉伸变形。
 */
class CenterCropDrawable(private val bitmap: Bitmap) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrix = Matrix()

    init {
        paint.shader = shader
    }

    override fun onBoundsChange(bounds: Rect) {
        val bw = bounds.width().toFloat()
        val bh = bounds.height().toFloat()
        if (bw <= 0f || bh <= 0f || bitmap.width == 0 || bitmap.height == 0) return
        val scale = max(bw / bitmap.width, bh / bitmap.height)
        val dx = (bw - bitmap.width * scale) / 2f
        val dy = (bh - bitmap.height * scale) / 2f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx + bounds.left, dy + bounds.top)
        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
