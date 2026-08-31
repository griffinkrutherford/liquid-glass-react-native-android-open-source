package io.github.griffinkrutherford.liquidglass.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.griffinkrutherford.liquidglass.core.FixedTimestepRunner
import com.griffinkrutherford.liquidglass.core.LiquidMembrane
import com.griffinkrutherford.liquidglass.core.LiquidPhysicsConfig
import kotlin.math.abs

/** A deliberately shader-free visualizer: it makes the physics readable on every API level. */
class LiquidGlassDemoView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val membrane = LiquidMembrane(
        LiquidPhysicsConfig(columns = 21, rows = 27, stiffness = 38f, damping = 4.4f, viscosity = 18f),
    )
    private val runner = FixedTimestepRunner(membrane)
    private val glassBounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val line = Path()
    private val glassClip = Path()
    private var previousFrameNanos = 0L
    private var lastTouchX = Float.NaN
    private var lastTouchY = Float.NaN

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        glassBounds.set(18f * density, 142f * density, width - 18f * density, height - 70f * density)
        membrane.resize(glassBounds.width(), glassBounds.height())
        seedRipple()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        if (previousFrameNanos != 0L) {
            runner.advance(((now - previousFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f))
        }
        previousFrameNanos = now

        drawBackdrop(canvas)
        drawGlass(canvas)
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun drawBackdrop(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(7, 18, 31), Color.rgb(12, 39, 55), Color.rgb(14, 25, 45)),
            floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        glow(canvas, width * .86f, height * .27f, 150f * density, Color.rgb(14, 165, 233))
        glow(canvas, width * .05f, height * .74f, 170f * density, Color.rgb(99, 102, 241))

        paint.shader = null
        paint.color = Color.argb(90, 255, 255, 255)
        repeat(5) { i ->
            val y = glassBounds.top + 65f * density + i * 88f * density
            canvas.drawRoundRect(40f * density, y, width - 40f * density, y + 48f * density,
                18f * density, 18f * density, paint)
        }
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(x, y, radius,
            Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawGlass(canvas: Canvas) {
        val state = membrane.snapshot()
        val radius = 32f * density
        canvas.save()
        glassClip.reset()
        glassClip.addRoundRect(glassBounds, radius, radius, Path.Direction.CW)
        canvas.clipPath(glassClip)

        paint.shader = LinearGradient(glassBounds.left, glassBounds.top, glassBounds.right, glassBounds.bottom,
            intArrayOf(Color.argb(116, 220, 245, 255), Color.argb(54, 148, 213, 255), Color.argb(82, 238, 248, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(glassBounds, paint)
        paint.shader = null

        // The displaced grid is both a useful debugger and a crisp approximation of refracted light.
        for (row in 1 until state.rows - 1 step 2) {
            line.reset()
            for (column in 0 until state.columns) {
                val x = glassBounds.left + column.toFloat() / (state.columns - 1) * glassBounds.width()
                val baseY = glassBounds.top + row.toFloat() / (state.rows - 1) * glassBounds.height()
                val displacement = state.displacement(column, row)
                val y = baseY + displacement * 25f * density
                if (column == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            meshPaint.color = Color.argb(45, 255, 255, 255)
            canvas.drawPath(line, meshPaint)
        }

        for (row in 1 until state.rows - 1) for (column in 1 until state.columns - 1) {
            val displacement = state.displacement(column, row)
            if (abs(displacement) > .012f) {
                val x = glassBounds.left + column.toFloat() / (state.columns - 1) * glassBounds.width()
                val y = glassBounds.top + row.toFloat() / (state.rows - 1) * glassBounds.height() + displacement * 25f * density
                paint.color = if (displacement > 0f) Color.argb(115, 224, 250, 255) else Color.argb(80, 56, 189, 248)
                canvas.drawCircle(x, y, (1.2f + abs(displacement) * 2f) * density, paint)
            }
        }
        canvas.restore()

        highlightPaint.shader = LinearGradient(glassBounds.left, glassBounds.top, glassBounds.right, glassBounds.bottom,
            intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(155, 186, 230, 253)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(glassBounds, radius, radius, highlightPaint)
        highlightPaint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val localX = event.x - glassBounds.left
        val localY = event.y - glassBounds.top
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!glassBounds.contains(event.x, event.y)) return false
                parent.requestDisallowInterceptTouchEvent(true)
                impulse(localX, localY, 4.5f)
                lastTouchX = localX
                lastTouchY = localY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(localX - lastTouchX) + abs(localY - lastTouchY) > 8f * density) {
                    impulse(localX, localY, 2.4f)
                    lastTouchX = localX
                    lastTouchY = localY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                impulse(localX, localY, -2.2f)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        previousFrameNanos = 0L
        super.onDetachedFromWindow()
    }

    private fun impulse(x: Float, y: Float, strength: Float) {
        membrane.applyImpulse(x.coerceIn(0f, glassBounds.width()), y.coerceIn(0f, glassBounds.height()),
            72f * density, strength)
    }

    private fun seedRipple() {
        membrane.applyImpulse(glassBounds.width() * .5f, glassBounds.height() * .35f, 90f * density, 3.5f)
    }
}
