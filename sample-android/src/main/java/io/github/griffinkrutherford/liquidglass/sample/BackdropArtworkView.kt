package io.github.griffinkrutherford.liquidglass.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/** High-contrast scene content that makes displacement and dispersion easy to inspect. */
class BackdropArtworkView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(4, 13, 25), Color.rgb(8, 38, 58), Color.rgb(20, 20, 50)),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        glow(canvas, width * .82f, height * .30f, dp(160f), Color.rgb(14, 165, 233))
        glow(canvas, width * .08f, height * .66f, dp(190f), Color.rgb(124, 58, 237))

        val colors = intArrayOf(
            Color.rgb(253, 186, 116),
            Color.rgb(103, 232, 249),
            Color.rgb(196, 181, 253),
            Color.rgb(134, 239, 172),
        )
        repeat(7) { index ->
            val y = dp(194f + index * 78f)
            paint.color = Color.argb(238, Color.red(colors[index % colors.size]), Color.green(colors[index % colors.size]), Color.blue(colors[index % colors.size]))
            canvas.drawRoundRect(dp(34f), y, width - dp(34f), y + dp(48f), dp(18f), dp(18f), paint)

            paint.color = Color.argb(120, 5, 20, 35)
            canvas.drawCircle(dp(62f), y + dp(24f), dp(10f), paint)
            canvas.drawRoundRect(dp(86f), y + dp(15f), width - dp(68f), y + dp(22f), dp(4f), dp(4f), paint)
            canvas.drawRoundRect(dp(86f), y + dp(28f), width - dp(126f), y + dp(34f), dp(3f), dp(3f), paint)
        }

        paint.color = Color.argb(180, 255, 255, 255)
        paint.strokeWidth = dp(2f)
        for (x in 0..width step dp(32f).toInt()) {
            canvas.drawLine(x.toFloat(), dp(155f), x.toFloat(), dp(735f), paint)
        }
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            x, y, radius,
            Color.argb(190, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun dp(value: Float): Float = value * density
}
