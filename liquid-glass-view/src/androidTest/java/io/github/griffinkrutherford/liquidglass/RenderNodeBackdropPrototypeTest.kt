package io.github.griffinkrutherford.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** GPU-only backdrop experiments; these do not replace the production bitmap path. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class RenderNodeBackdropPrototypeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun recordingAnInnerNodeDuringOuterRecordingRetainsPixels() = onMain {
        val outer = RenderNode("outer").apply { setPosition(0, 0, 80, 40) }
        val inner = RenderNode("inner").apply { setPosition(0, 0, 80, 40) }
        try {
            val outerCanvas = outer.beginRecording(80, 40)
            val innerCanvas = inner.beginRecording(80, 40)
            innerCanvas.drawColor(Color.MAGENTA)
            inner.endRecording()
            outerCanvas.drawRenderNode(inner)
            outer.endRecording()
            val pixels = render(outer)
            try { assertEquals(Color.MAGENTA, pixels.getPixel(40, 20)) }
            finally { pixels.recycle() }
        } finally {
            outer.discardDisplayList()
            inner.discardDisplayList()
        }
    }

    @Test fun runtimeEffectSamplesUpdatedRenderNodeContent() = onMain {
        val source = RenderNode("backdrop").apply { setPosition(0, 0, 80, 40) }
        val shader = RuntimeShader("""
            uniform shader backdrop;
            half4 main(float2 p) { return backdrop.eval(p + float2(20.0, 0.0)); }
        """.trimIndent())
        val effectNode = RenderNode("glass").apply {
            setPosition(0, 0, 80, 40)
            setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "backdrop"))
        }
        val root = RenderNode("root").apply { setPosition(0, 0, 80, 40) }
        try {
            val sourceCanvas = source.beginRecording(80, 40)
            sourceCanvas.drawColor(Color.RED)
            sourceCanvas.drawRect(40f, 0f, 80f, 40f, Paint().apply { color = Color.BLUE })
            source.endRecording()
            val effectCanvas = effectNode.beginRecording()
            effectCanvas.drawRenderNode(source)
            effectNode.endRecording()
            val rootCanvas = root.beginRecording()
            rootCanvas.drawRenderNode(effectNode)
            root.endRecording()

            val first = render(root)
            try {
                assertEquals(Color.RED, first.getPixel(10, 20))
                assertEquals(Color.BLUE, first.getPixel(30, 20))
            } finally { first.recycle() }

            source.beginRecording().also { it.drawColor(Color.GREEN) }
            source.endRecording()
            effectNode.beginRecording().also { it.drawRenderNode(source) }
            effectNode.endRecording()
            root.beginRecording().also { it.drawRenderNode(effectNode) }
            root.endRecording()
            val updated = render(root)
            try { assertEquals(Color.GREEN, updated.getPixel(10, 20)) }
            finally { updated.recycle() }
        } finally {
            root.discardDisplayList()
            effectNode.discardDisplayList()
            source.discardDisplayList()
        }
    }

    @Test fun nestedGlassCanBeSuppressedAfterHardwareDisplayListWasRecorded() = onMain {
        val context = instrumentation.targetContext
        val wrapper = FrameLayout(context).apply { setBackgroundColor(Color.RED) }
        val glass = LiquidGlassView(context)
        wrapper.addView(glass, ViewGroup.LayoutParams(60, 20))
        wrapper.layout(0, 0, 80, 40)
        glass.layout(10, 10, 70, 30)
        val root = RenderNode("nested glass").apply { setPosition(0, 0, 80, 40) }
        try {
            root.beginRecording().also { wrapper.draw(it) }
            root.endRecording()
            val visible = render(root)
            try { assertNotEquals(Color.RED, visible.getPixel(40, 20)) }
            finally { visible.recycle() }

            glass.setSuppressedForCapture(true)
            glass.invalidate()
            root.beginRecording().also { wrapper.draw(it) }
            root.endRecording()
            val suppressed = render(root)
            try { assertEquals(Color.RED, suppressed.getPixel(40, 20)) }
            finally { suppressed.recycle() }
        } finally {
            glass.setSuppressedForCapture(false)
            root.discardDisplayList()
        }
    }

    @Ignore("Detached scene capture remains transparent even when recorded before the outer node")
    @Test fun sceneGlassTracksChangedBackdropWithoutBitmapCapture() = onMain {
        val context = instrumentation.targetContext
        val scene = LiquidGlassScene(context).apply {
            managesChildLayout = false
            useRenderNodeBackdrop = true
        }
        val backdrop = View(context).apply { setBackgroundColor(Color.RED) }
        val glass = LiquidGlassView(context).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            effectAmount = 0f
            cornerRadius = 0f
        }
        scene.addView(backdrop, ViewGroup.LayoutParams(80, 40))
        scene.addView(glass, ViewGroup.LayoutParams(60, 30))
        scene.registerGlassView(glass)
        scene.layout(0, 0, 80, 40)
        backdrop.layout(0, 0, 80, 40)
        glass.layout(10, 5, 70, 35)
        val root = RenderNode("scene scroll").apply { setPosition(0, 0, 80, 40) }
        try {
            scene.prepareRenderNodeBackdrop()
            root.beginRecording().also {
                assertTrue(it.isHardwareAccelerated)
                scene.draw(it)
            }
            root.endRecording()
            val capturedNode = scene.javaClass.getDeclaredField("backdropNode").apply { isAccessible = true }.get(scene) as RenderNode
            assertNotNull(capturedNode)
            assertTrue(capturedNode.hasDisplayList())
            val probeRoot = RenderNode("backdrop probe").apply { setPosition(0, 0, 80, 40) }
            probeRoot.beginRecording().also { it.drawRenderNode(capturedNode) }
            probeRoot.endRecording()
            val sourcePixels = render(probeRoot)
            try { assertEquals(Color.RED, sourcePixels.getPixel(40, 20)) }
            finally { sourcePixels.recycle() }
            probeRoot.discardDisplayList()
            val first = render(root)
            try { assertEquals(Color.RED, first.getPixel(40, 20)) }
            finally { first.recycle() }

            backdrop.setBackgroundColor(Color.BLUE)
            backdrop.invalidate()
            scene.onDescendantInvalidated(backdrop, backdrop)
            scene.prepareRenderNodeBackdrop()
            root.beginRecording().also { scene.draw(it) }
            root.endRecording()
            val second = render(root)
            try { assertEquals(Color.BLUE, second.getPixel(40, 20)) }
            finally { second.recycle() }
        } finally {
            scene.unregisterGlassView(glass)
            root.discardDisplayList()
        }
    }

    @Ignore("Detached scene node content renders transparent on API 35 emulator; test in an attached window")
    @Test fun overlappingGlassExcludesNestedGlassFromBackdrop() = onMain {
        val context = instrumentation.targetContext
        val scene = LiquidGlassScene(context).apply {
            managesChildLayout = false
            useRenderNodeBackdrop = true
            setBackgroundColor(Color.RED)
        }
        val wrapper = FrameLayout(context)
        val nested = LiquidGlassView(context).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            cornerRadius = 0f
            refractionStrength = 0f
            dispersion = 0f
            blurRadius = 0f
            tintAmount = 1f
            tintColor = Color.BLUE
        }
        val overlay = LiquidGlassView(context).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            cornerRadius = 0f
            effectAmount = 0f
        }
        wrapper.addView(nested, ViewGroup.LayoutParams(60, 30))
        scene.addView(wrapper, ViewGroup.LayoutParams(80, 40))
        scene.addView(overlay, ViewGroup.LayoutParams(40, 20))
        scene.registerGlassView(nested)
        scene.registerGlassView(overlay)
        scene.layout(0, 0, 80, 40)
        wrapper.layout(0, 0, 80, 40)
        nested.layout(10, 5, 70, 35)
        overlay.layout(20, 10, 60, 30)
        val root = RenderNode("overlapping glass").apply { setPosition(0, 0, 80, 40) }
        try {
            root.beginRecording().also {
                assertTrue(it.isHardwareAccelerated)
                scene.draw(it)
            }
            root.endRecording()
            assertNotNull(scene.javaClass.getDeclaredField("backdropNode").apply { isAccessible = true }.get(scene))
            val pixels = render(root)
            try { assertEquals(Color.RED, pixels.getPixel(40, 20)) }
            finally { pixels.recycle() }
        } finally {
            scene.unregisterGlassView(overlay)
            scene.unregisterGlassView(nested)
            root.discardDisplayList()
        }
    }

    private fun render(root: RenderNode): Bitmap {
        val reader = ImageReader.newInstance(
            80, 40, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(root)
            val result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            assertTrue(result == HardwareRenderer.SYNC_OK || result == HardwareRenderer.SYNC_REDRAW_REQUESTED)
            val image = requireNotNull(reader.acquireNextImage())
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
            reader.close()
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
