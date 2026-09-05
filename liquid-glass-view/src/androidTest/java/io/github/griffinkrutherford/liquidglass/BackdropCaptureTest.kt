package io.github.griffinkrutherford.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.graphics.Paint
import android.graphics.RenderNode
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class BackdropCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun captureUsesPhysicalCoordinatesAndRestoresCanvasState() {
        val frame = SceneBackdrop.create(BackdropGeometry(101, 73, 17f, 23f))
        try {
            val canvas = Canvas(frame.bitmap)
            assertEquals(Bitmap.DENSITY_NONE, canvas.density)
            repeat(2) {
                frame.bitmap.eraseColor(Color.TRANSPARENT)
                frame.draw(canvas) {
                    it.drawRect(17f, 23f, 37f, 43f, Paint().apply { color = Color.RED })
                }
                assertEquals(Color.RED, frame.bitmap.getPixel(0, 0))
                assertEquals(Color.RED, frame.bitmap.getPixel(9, 9))
                assertEquals(Color.TRANSPARENT, frame.bitmap.getPixel(10, 10))
                assertEquals(1, canvas.saveCount)
            }
        } finally {
            frame.bitmap.recycle()
        }
    }

    @Test fun densityTaggedSourceBitmapIsNotScaledTwice() {
        val frame = SceneBackdrop.create(BackdropGeometry(40, 40))
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            density = 640
            eraseColor(Color.GREEN)
        }
        try {
            frame.draw(Canvas(frame.bitmap)) { it.drawBitmap(source, 0f, 0f, null) }
            assertEquals(Color.GREEN, frame.bitmap.getPixel(9, 9))
            assertEquals(Color.TRANSPARENT, frame.bitmap.getPixel(10, 10))
        } finally {
            source.recycle()
            frame.bitmap.recycle()
        }
    }

    @Test fun canvasStateIsRestoredAfterFailedCapture() {
        val frame = SceneBackdrop.create(BackdropGeometry(10, 10))
        try {
            val canvas = Canvas(frame.bitmap)
            assertThrows(IllegalStateException::class.java) {
                frame.draw(canvas) { error("capture failed") }
            }
            assertEquals(1, canvas.saveCount)
            canvas.drawRect(4f, 4f, 5f, 5f, Paint().apply { color = Color.BLUE })
            assertEquals(Color.BLUE, frame.bitmap.getPixel(4, 4))
        } finally {
            frame.bitmap.recycle()
        }
    }

    @Test fun sceneReusesCaptureAndPublishesMatchingMetadataOnResizeAndRegistration() = onMain {
        val scene = LiquidGlassScene(instrumentation.targetContext).apply {
            managesChildLayout = false
            setBackgroundColor(Color.RED)
        }
        val glass = LiquidGlassView(instrumentation.targetContext)
        scene.addView(glass, ViewGroup.LayoutParams(40, 40))
        scene.registerGlassView(glass)
        val output = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        try {
            scene.layout(0, 0, 101, 73)
            glass.layout(10, 10, 50, 50)
            scene.draw(Canvas(output))
            val first = backdrop(scene)!!
            assertEquals(51, first.bitmap.width)
            assertEquals(37, first.bitmap.height)
            assertEquals(Color.RED, first.bitmap.getPixel(20, 20))
            assertSame(first, backdrop(glass))
            val generation = first.bitmap.generationId
            scene.draw(Canvas(output))
            assertSame(first, backdrop(scene))
            assertEquals(generation, first.bitmap.generationId)

            glass.visibility = View.INVISIBLE
            scene.setBackgroundColor(Color.BLUE)
            scene.draw(Canvas(output))
            glass.visibility = View.VISIBLE
            assertSame(first, backdrop(glass))
            assertEquals(Color.BLUE, first.bitmap.getPixel(20, 20))

            val secondGlass = LiquidGlassView(instrumentation.targetContext)
            scene.registerGlassView(secondGlass)
            scene.draw(Canvas(output))
            assertSame(first, backdrop(secondGlass))

            // 101 and 102 share a rounded texture width: metadata must still be replaced.
            scene.layout(0, 0, 102, 74)
            scene.draw(Canvas(output))
            val resized = backdrop(scene)!!
            assertNotSame(first, resized)
            assertTrue(first.bitmap.isRecycled)
            assertEquals(102, resized.geometry.width)
            assertSame(resized, backdrop(glass))
            assertSame(resized, backdrop(secondGlass))
            scene.unregisterGlassView(secondGlass)
            assertNull(backdrop(secondGlass))
            scene.unregisterGlassView(glass)
            assertNull(backdrop(glass))
            assertTrue(resized.bitmap.isRecycled)
            assertNull(backdrop(scene))
        } finally {
            scene.unregisterGlassView(glass)
            output.recycle()
        }
    }

    @Test fun bothShaderVariantsAcceptScaledAndFullResolutionBackdrops() = onMain {
        val node = RenderNode("glass capture test").apply { setPosition(0, 0, 100, 80) }
        for (factor in listOf(2, 1)) {
            val glass = LiquidGlassView(instrumentation.targetContext).apply {
                interactive = true
                layout(0, 0, 100, 80)
            }
            FrameLayout(instrumentation.targetContext).addView(glass)
            val frame = SceneBackdrop.create(BackdropGeometry(101, 81, 13f, 17f, factor))
            try {
                frame.bitmap.eraseColor(Color.CYAN)
                glass.setSceneBackdrop(frame)
                repeat(2) { physics ->
                    if (physics == 1) {
                        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 40f, 0)
                        try { glass.onTouchEvent(touch) } finally { touch.recycle() }
                    }
                    val canvas = node.beginRecording()
                    try { glass.draw(canvas) } finally { node.endRecording() }
                }
            } finally {
                glass.clearSceneBackdrop()
                node.discardDisplayList()
                frame.bitmap.recycle()
            }
        }
    }

    @Test fun gpuSamplingUpdatesScaleAndOriginWithThePublishedBitmap() = onMain {
        val half = SceneBackdrop.create(BackdropGeometry(101, 81, 13f, 17f))
        val glass = LiquidGlassView(instrumentation.targetContext).apply {
            effectAmount = 0f // Isolate backdrop coordinates from optical color processing.
            cornerRadius = 4f
            layout(0, 0, 100, 80)
        }
        try {
            half.bitmap.eraseColor(Color.BLUE)
            Canvas(half.bitmap).drawRect(0f, 0f, 25f, 41f, Paint().apply { color = Color.RED })
            glass.setSceneBackdrop(half)
            render(glass).let { pixels ->
                try {
                    assertEquals(Color.RED, pixels.getPixel(45, 40))
                    assertEquals(Color.BLUE, pixels.getPixel(80, 40))
                } finally { pixels.recycle() }
            }
            // Reuse the same bitmap with a different mapping: identity caching cannot keep old uniforms.
            glass.setSceneBackdrop(SceneBackdrop(half.bitmap, BackdropGeometry(51, 41, 13f, 17f, 1)))
            render(glass).let { pixels ->
                try { assertEquals(Color.BLUE, pixels.getPixel(45, 40)) } finally { pixels.recycle() }
            }
            glass.setSceneBackdrop(SceneBackdrop(half.bitmap, BackdropGeometry(101, 81, 43f, 17f)))
            render(glass).let { pixels ->
                try { assertEquals(Color.RED, pixels.getPixel(80, 40)) } finally { pixels.recycle() }
            }
        } finally {
            glass.clearSceneBackdrop()
            half.bitmap.recycle()
        }
    }

    @Test fun invalidationRaisedDuringCaptureSurvivesUntilTheNextPass() = onMain {
        val scene = LiquidGlassScene(instrumentation.targetContext).apply { managesChildLayout = false }
        var drawCount = 0
        val child = object : View(instrumentation.targetContext) {
            override fun onDraw(canvas: Canvas) {
                drawCount++
                if (drawCount == 1) scene.onDescendantInvalidated(this, this)
            }
        }
        scene.addView(child)
        child.layout(0, 0, 20, 20)
        val glass = LiquidGlassView(instrumentation.targetContext)
        scene.registerGlassView(glass)
        scene.layout(0, 0, 40, 40)
        val output = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        try {
            scene.draw(Canvas(output))
            val first = backdrop(scene)!!
            val generation = first.bitmap.generationId
            scene.draw(Canvas(output))
            assertNotEquals(generation, first.bitmap.generationId)
        } finally {
            scene.unregisterGlassView(glass)
            output.recycle()
        }
    }

    /** Isolated GPU readback for tests only; production capture remains a software canvas. */
    private fun render(view: View): Bitmap {
        val node = RenderNode("backdrop mapping pixels").apply { setPosition(0, 0, view.width, view.height) }
        val reader = ImageReader.newInstance(
            view.width, view.height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            val canvas = node.beginRecording()
            try { view.draw(canvas) } finally { node.endRecording() }
            val result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            assertTrue("GPU draw failed: $result", result == HardwareRenderer.SYNC_OK ||
                result == HardwareRenderer.SYNC_REDRAW_REQUESTED)
            val image = requireNotNull(reader.acquireNextImage()) { "No GPU image produced" }
            image.use {
                val buffer = requireNotNull(image.hardwareBuffer)
                buffer.use {
                    val bitmap = requireNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)))
                    try { return requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
                    finally { bitmap.recycle() }
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    private fun backdrop(owner: Any): SceneBackdrop? = owner.javaClass
        .getDeclaredField(if (owner is LiquidGlassScene) "backdrop" else "sceneBackdrop")
        .apply { isAccessible = true }.get(owner) as SceneBackdrop?

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
