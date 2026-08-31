package io.github.griffinkrutherford.liquidglass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * A compositing container that lets [LiquidGlassView] instances sample sibling views behind them.
 *
 * Add normal Android views and one or more glass views as children. Every frame, the scene renders
 * non-glass children into a shared offscreen bitmap before drawing the hierarchy to the screen.
 * This avoids GPU readback and keeps the sampled pixels synchronized with the visible UI.
 */
class LiquidGlassScene @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    private var backdrop: Bitmap? = null
    private var backdropCanvas: Canvas? = null

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams?): LayoutParams = MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams?): Boolean = params is MarginLayoutParams

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as MarginLayoutParams
            val childLeft = paddingLeft + params.leftMargin
            val childTop = paddingTop + params.topMargin
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        ensureBackdrop()
        val capture = backdropCanvas
        val bitmap = backdrop
        if (capture != null && bitmap != null) {
            capture.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
            background?.let {
                it.setBounds(0, 0, width, height)
                it.draw(capture)
            }
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility == View.VISIBLE && child !is LiquidGlassView) {
                    val checkpoint = capture.save()
                    capture.translate(child.left.toFloat(), child.top.toFloat())
                    child.draw(capture)
                    capture.restoreToCount(checkpoint)
                }
            }
            for (index in 0 until childCount) {
                (getChildAt(index) as? LiquidGlassView)?.setSceneBackdrop(bitmap)
            }
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        backdrop = null
        backdropCanvas = null
        super.onDetachedFromWindow()
    }

    private fun ensureBackdrop() {
        if (width <= 0 || height <= 0) return
        val current = backdrop
        if (current == null || current.width != width || current.height != height) {
            current?.recycle()
            backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            backdropCanvas = Canvas(requireNotNull(backdrop))
        }
    }
}
