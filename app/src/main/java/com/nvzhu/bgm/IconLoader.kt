package com.nvzhu.bgm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IconLoader {

    fun bind(
        scope: CoroutineScope,
        image: ImageView,
        uriString: String,
        sizePx: Int,
        hideWhenEmpty: Boolean = true
    ) {
        if (uriString.isEmpty()) {
            image.tag = null
            image.setImageDrawable(null)
            image.visibility = if (hideWhenEmpty) ImageView.GONE else ImageView.VISIBLE
            return
        }
        image.visibility = ImageView.VISIBLE
        image.tag = uriString
        val context = image.context.applicationContext
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decode(context, uriString, sizePx)
            }
            if (image.tag == uriString) {
                if (bitmap != null) {
                    image.setImageBitmap(bitmap)
                    image.visibility = ImageView.VISIBLE
                } else {
                    image.setImageDrawable(null)
                    image.visibility = if (hideWhenEmpty) ImageView.GONE else ImageView.VISIBLE
                }
            }
        }
    }

    private fun decode(context: Context, uriString: String, reqPx: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqPx)
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sampleSize(width: Int, height: Int, req: Int): Int {
        var size = 1
        while (width / size > req * 2 && height / size > req * 2) {
            size *= 2
        }
        return size.coerceAtLeast(1)
    }
}
