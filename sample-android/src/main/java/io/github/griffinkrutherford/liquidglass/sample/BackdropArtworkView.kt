package io.github.griffinkrutherford.liquidglass.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.View

/** A center-cropped real photograph spanning the complete refraction scene. */
class BackdropArtworkView(context: Context) : View(context) {
    private val photograph: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tropical_beach_cc0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = Rect()

    override fun onDraw(canvas: Canvas) {
        val sourceAspect = photograph.width.toFloat() / photograph.height
        val destinationAspect = width.toFloat() / height
        if (sourceAspect > destinationAspect) {
            val visibleWidth = (photograph.height * destinationAspect).toInt()
            val left = (photograph.width - visibleWidth) / 2
            source.set(left, 0, left + visibleWidth, photograph.height)
        } else {
            val visibleHeight = (photograph.width / destinationAspect).toInt()
            val top = (photograph.height - visibleHeight) / 2
            source.set(0, top, photograph.width, top + visibleHeight)
        }
        destination.set(0, 0, width, height)
        canvas.drawBitmap(photograph, source, destination, paint)

        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.argb(135, 0, 10, 22), Color.TRANSPARENT, Color.argb(65, 0, 8, 18)),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(destination, paint)
        paint.shader = null
    }
}
