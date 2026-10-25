package com.ncmdump.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.View

object BackgroundHelper {

    fun apply(view: View, uri: Uri?, blur: Boolean) {
        if (uri == null) {
            view.setBackgroundResource(R.color.bg_main)
            view.foreground = null
            return
        }
        val width = view.width.takeIf { it > 0 }
            ?: view.resources.displayMetrics.widthPixels
        val height = view.height.takeIf { it > 0 }
            ?: view.resources.displayMetrics.heightPixels

        val bitmap = decode(view.context, uri, width, height)
        if (bitmap == null) {
            view.setBackgroundResource(R.color.bg_main)
            view.foreground = null
            return
        }

        val shown = if (blur) blurBitmap(bitmap) else bitmap
        val layers = arrayOf<Drawable>(
            CenterCropDrawable(shown),
            ColorDrawable(ThemeHelper.scrimColor(view))
        )
        view.background = LayerDrawable(layers)
        view.foreground = null
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

    private fun blurBitmap(src: Bitmap): Bitmap {
        val w = (src.width / 12).coerceAtLeast(1)
        val h = (src.height / 12).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (small !== out) small.recycle()
        if (out !== src) src.recycle()
        return out
    }
}
