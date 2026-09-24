package io.github.griffinkrutherford.liquidglass

import android.annotation.TargetApi
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Trace
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

/**
 * A compositing container that lets [LiquidGlassView] instances sample sibling views behind them.
 *
 * Add normal Android views and one or more glass views as children. The scene renders non-glass
 * children into a shared offscreen bitmap whenever that content changes, then draws the hierarchy
 * to the screen. This avoids GPU readback and keeps the sampled pixels synchronized with the
 * visible UI without re-capturing a static screen.
 *
 * The scene owns the capture bitmap. Glass views only borrow it between
 * [LiquidGlassView.setSceneBackdrop] and [LiquidGlassView.clearSceneBackdrop].
 */
class LiquidGlassScene @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    /** Set false when an external layout engine such as React Native Yoga positions children. */
    var managesChildLayout: Boolean = true
    /** Experimental GPU capture path. The bitmap path remains the default. */
    var useRenderNodeBackdrop: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            releaseBackdrop()
            markBackdropDirty()
        }
    private val glassViews = ArrayList<LiquidGlassView>(2)
    private var backdrop: SceneBackdrop? = null
    private var backdropCanvas: Canvas? = null
    private var backdropNode: RenderNode? = null
    private var nodeWidth = 0
    private var nodeHeight = 0
    private var backdropDirty = true
    private var deliveringBackdrop = false
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = releaseBackdrop()

        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releaseBackdrop()
        }
    }

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        if (managesChildLayout) {
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            }
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams?): LayoutParams = MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams?): Boolean = params is MarginLayoutParams

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) markBackdropDirty()
        if (!managesChildLayout) return
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as MarginLayoutParams
            val childLeft = paddingLeft + params.leftMargin
            val childTop = paddingTop + params.topMargin
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
        }
    }

    internal fun registerGlassView(view: LiquidGlassView) {
        if (!glassViews.contains(view)) glassViews.add(view)
        markBackdropDirty()
    }

    internal fun unregisterGlassView(view: LiquidGlassView) {
        if (!glassViews.remove(view)) return
        view.clearSceneBackdrop()
        view.clearSceneBackdropNode()
        if (glassViews.isEmpty()) releaseBackdrop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        markBackdropDirty()
    }

    override fun onDetachedFromWindow() {
        context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
        releaseBackdrop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        markBackdropDirty()
    }

    override fun setBackground(background: Drawable?) {
        super.setBackground(background)
        markBackdropDirty()
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        if (!deliveringBackdrop) markBackdropDirty()
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        markBackdropDirty()
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        (child as? LiquidGlassView)?.let(::unregisterGlassView)
        markBackdropDirty()
    }

    @TargetApi(26)
    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        if (deliveringBackdrop) return
        if (child is LiquidGlassView || isInsideGlass(target)) return
        markBackdropDirty()
    }

    private fun isInsideGlass(target: View): Boolean {
        var current: View? = target
        var passedGlass = false
        while (current != null && current !== this) {
            if (current is LiquidGlassView) passedGlass = true
            current = current.parent as? View
        }
        return current === this && passedGlass
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun invalidateChildInParent(location: IntArray?, dirty: Rect?): ViewParent? {
        if (!deliveringBackdrop) markBackdropDirty()
        return super.invalidateChildInParent(location, dirty)
    }

    /**
     * Records that the captured content changed. The scene must invalidate itself: a descendant
     * invalidation alone never re-runs [dispatchDraw] under hardware rendering.
     */
    private fun markBackdropDirty() {
        backdropDirty = true
        if (isAttachedToWindow && shouldCapture()) invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (shouldCapture()) {
            if (canvas.isHardwareAccelerated && useRenderNodeBackdrop) {
                prepareRenderNodeBackdrop()
                super.dispatchDraw(canvas)
                return
            }
            ensureBackdrop()
            val capture = backdropCanvas
            val frame = backdrop
            if (capture != null && frame != null && !frame.bitmap.isRecycled) {
                if (backdropDirty) {
                    // Clear before drawing so an invalidation raised during capture survives.
                    backdropDirty = false
                    try {
                        Trace.beginSection("LiquidGlass.captureBackdrop")
                        try {
                            capture.drawColor(0, PorterDuff.Mode.CLEAR)
                            frame.draw(capture, ::captureBackdrop)
                        } finally {
                            Trace.endSection()
                        }
                    } catch (failure: Throwable) {
                        backdropDirty = true
                        throw failure
                    }
                    deliverBackdrop(frame)
                }
            }
        }
        super.dispatchDraw(canvas)
    }

    private fun shouldCapture(): Boolean =
        Build.VERSION.SDK_INT >= 33 && glassViews.isNotEmpty() && width > 0 && height > 0

    internal fun prepareRenderNodeBackdrop() {
        if (!shouldCapture() || !useRenderNodeBackdrop) return
        ensureBackdropNode()
        val node = backdropNode ?: return
        if (!backdropDirty) return
        backdropDirty = false
        Trace.beginSection("LiquidGlass.recordBackdropNode")
        try {
            val recording = node.beginRecording(width, height)
            try { captureBackdrop(recording) }
            finally { node.endRecording() }
        } catch (failure: Throwable) {
            backdropDirty = true
            throw failure
        } finally {
            Trace.endSection()
        }
        deliverBackdropNode(node)
    }

    private fun captureBackdrop(capture: Canvas) {
        for (index in glassViews.indices) {
            glassViews[index].setSuppressedForCapture(true)
            if (capture.isHardwareAccelerated) glassViews[index].invalidate()
        }
        try {
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
        } finally {
            for (index in glassViews.indices) {
                glassViews[index].setSuppressedForCapture(false)
                if (capture.isHardwareAccelerated) glassViews[index].invalidate()
            }
        }
    }

    private fun deliverBackdrop(frame: SceneBackdrop) {
        deliveringBackdrop = true
        try {
            for (index in glassViews.indices) {
                glassViews[index].setSceneBackdrop(frame)
            }
        } finally {
            deliveringBackdrop = false
        }
    }

    private fun deliverBackdropNode(node: RenderNode) {
        deliveringBackdrop = true
        try {
            for (index in glassViews.indices) glassViews[index].setSceneBackdropNode(node)
        } finally {
            deliveringBackdrop = false
        }
    }

    private fun ensureBackdropNode() {
        if (backdropNode != null && nodeWidth == width && nodeHeight == height) return
        backdropNode?.discardDisplayList()
        backdropNode = RenderNode("LiquidGlass backdrop").apply { setPosition(0, 0, width, height) }
        nodeWidth = width
        nodeHeight = height
        backdropDirty = true
    }

    private fun ensureBackdrop() {
        val current = backdrop
        if (current != null && !current.bitmap.isRecycled &&
            current.geometry.width == width && current.geometry.height == height
        ) return
        releaseBackdrop()
        val created = SceneBackdrop.create(BackdropGeometry(width, height))
        backdrop = created
        backdropCanvas = Canvas(created.bitmap)
        backdropDirty = true
    }

    private fun releaseBackdrop() {
        for (index in glassViews.indices) {
            glassViews[index].clearSceneBackdrop()
            glassViews[index].clearSceneBackdropNode()
        }
        backdropNode?.discardDisplayList()
        backdropNode = null
        backdropCanvas = null
        backdrop?.bitmap?.recycle()
        backdrop = null
        backdropDirty = true
    }
}
