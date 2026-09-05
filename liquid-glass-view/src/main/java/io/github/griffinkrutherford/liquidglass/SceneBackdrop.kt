package io.github.griffinkrutherford.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas

/** Publishes a bitmap and its immutable coordinate mapping together. The scene owns the pixels. */
internal class SceneBackdrop(val bitmap: Bitmap, val geometry: BackdropGeometry) {
    init {
        require(bitmap.width == geometry.bitmapWidth && bitmap.height == geometry.bitmapHeight)
    }

    /** Each pass starts in physical scene coordinates; save/restore prevents cumulative scaling. */
    inline fun draw(canvas: Canvas, block: (Canvas) -> Unit) {
        val checkpoint = canvas.save()
        try {
            canvas.scale(geometry.scale, geometry.scale)
            canvas.translate(-geometry.originX, -geometry.originY)
            canvas.clipRect(
                geometry.originX, geometry.originY,
                geometry.originX + geometry.width, geometry.originY + geometry.height,
            )
            block(canvas)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    companion object {
        fun create(geometry: BackdropGeometry): SceneBackdrop = SceneBackdrop(
            Bitmap.createBitmap(geometry.bitmapWidth, geometry.bitmapHeight, Bitmap.Config.ARGB_8888).apply {
                // Canvas scaling is explicit; density conversion must not add a second scale.
                density = Bitmap.DENSITY_NONE
            },
            geometry,
        )
    }
}
