package io.github.griffinkrutherford.liquidglass

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.griffinkrutherford.liquidglass.core.FixedTimestepRunner
import com.griffinkrutherford.liquidglass.core.LiquidMembrane
import com.griffinkrutherford.liquidglass.core.LiquidPhysicsConfig
import kotlin.math.abs

/** A touch-reactive glass surface that refracts sibling content captured by [LiquidGlassScene]. */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var cornerRadius = dp(32f)
        set(value) { field = value.coerceAtLeast(0f); invalidate() }
    var refractionStrength = dp(24f)
        set(value) { field = value.coerceIn(0f, dp(80f)); invalidate() }
    var dispersion = dp(2.4f)
        set(value) { field = value.coerceIn(0f, dp(12f)); invalidate() }
    var blurRadius = dp(2.2f)
        set(value) { field = value.coerceIn(0f, dp(12f)); invalidate() }
    var effectAmount = 0.96f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var tintColor: Int = Color.rgb(190, 229, 255)
        set(value) { field = value; invalidate() }
    var tintAmount = 0.11f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val membrane = LiquidMembrane(
        LiquidPhysicsConfig(columns = 25, rows = 25, stiffness = 42f, damping = 3.8f, viscosity = 22f),
    )
    private val runner = FixedTimestepRunner(membrane)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.35f)
    }
    private val normalPixels = IntArray(membrane.config.columns * membrane.config.rows)
    private val normalBitmap = Bitmap.createBitmap(
        membrane.config.columns,
        membrane.config.rows,
        Bitmap.Config.ARGB_8888,
    )
    private var sceneBackdrop: Bitmap? = null
    private var runtimeShader: RuntimeShader? = null
    private var backdropInput: BitmapShader? = null
    private var backdropInputBitmap: Bitmap? = null
    private var heightInput: BitmapShader? = null
    private var previousFrameNanos = 0L
    private var lastTouchX = Float.NaN
    private var lastTouchY = Float.NaN

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 33) runtimeShader = RuntimeShader(GLASS_SHADER)
    }

    internal fun setSceneBackdrop(bitmap: Bitmap) {
        sceneBackdrop = bitmap
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width > 0 && height > 0) {
            membrane.resize(width.toFloat(), height.toFloat())
            membrane.applyImpulse(width * 0.52f, height * 0.42f, minOf(width, height) * 0.28f, 4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        if (previousFrameNanos != 0L) {
            runner.advance(((now - previousFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f))
        }
        previousFrameNanos = now

        val backdrop = sceneBackdrop
        val shader = runtimeShader
        if (Build.VERSION.SDK_INT >= 33 && shader != null && backdrop != null && canvas.isHardwareAccelerated) {
            drawRuntimeGlass(canvas, shader, backdrop)
        } else {
            drawFallbackGlass(canvas)
        }
        drawBorder(canvas)
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    @TargetApi(33)
    private fun drawRuntimeGlass(canvas: Canvas, shader: RuntimeShader, backdrop: Bitmap) {
        updateNormalMap()
        if (backdropInputBitmap !== backdrop) {
            backdropInputBitmap = backdrop
            backdropInput = filteredShader(backdrop)
        }
        if (heightInput == null) heightInput = filteredShader(normalBitmap)
        shader.setInputShader("backdrop", requireNotNull(backdropInput))
        shader.setInputShader("heightMap", requireNotNull(heightInput))
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("sceneOrigin", left.toFloat(), top.toFloat())
        shader.setFloatUniform("gridSize", normalBitmap.width.toFloat(), normalBitmap.height.toFloat())
        shader.setFloatUniform("refraction", refractionStrength)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("blurRadius", blurRadius)
        shader.setFloatUniform("effectAmount", effectAmount)
        shader.setFloatUniform(
            "tint",
            Color.red(tintColor) / 255f,
            Color.green(tintColor) / 255f,
            Color.blue(tintColor) / 255f,
            tintAmount,
        )
        paint.shader = shader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        paint.shader = null
    }

    private fun updateNormalMap() {
        val state = membrane.snapshot()
        for (row in 0 until state.rows) {
            for (column in 0 until state.columns) {
                val normalized = (state.displacement(column, row) / membrane.config.maxDisplacement * 0.5f + 0.5f)
                    .coerceIn(0f, 1f)
                val value = (normalized * 255f).toInt()
                normalPixels[row * state.columns + column] = Color.rgb(value, value, value)
            }
        }
        normalBitmap.setPixels(normalPixels, 0, state.columns, 0, 0, state.columns, state.rows)
    }

    @TargetApi(33)
    private fun filteredShader(bitmap: Bitmap): BitmapShader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }

    private fun drawFallbackGlass(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(125, 224, 245, 255), Color.argb(70, 126, 196, 232), Color.argb(105, 216, 242, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        paint.shader = null
    }

    private fun drawBorder(canvas: Canvas) {
        borderPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(235, 255, 255, 255), Color.argb(45, 255, 255, 255), Color.argb(170, 168, 220, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        val inset = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, cornerRadius, cornerRadius, borderPaint)
        borderPaint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                applyImpulse(event.x, event.y, 5.2f)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - lastTouchX) + abs(event.y - lastTouchY) > dp(7f)) {
                    applyImpulse(event.x, event.y, 2.8f)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                applyImpulse(event.x, event.y, -2.4f)
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
        sceneBackdrop = null
        backdropInput = null
        backdropInputBitmap = null
        super.onDetachedFromWindow()
    }

    private fun applyImpulse(x: Float, y: Float, strength: Float) {
        membrane.applyImpulse(
            x.coerceIn(0f, width.toFloat()),
            y.coerceIn(0f, height.toFloat()),
            minOf(width, height) * 0.24f,
            strength,
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val GLASS_SHADER = """
            uniform shader backdrop;
            uniform shader heightMap;
            uniform float2 size;
            uniform float2 sceneOrigin;
            uniform float2 gridSize;
            uniform float refraction;
            uniform float dispersion;
            uniform float blurRadius;
            uniform float effectAmount;
            uniform float4 tint;

            half heightAt(float2 uv) {
                float2 coordinate = clamp(uv, float2(0.0), float2(1.0)) * (gridSize - 1.0);
                return heightMap.eval(coordinate).r;
            }

            half4 main(float2 p) {
                float2 uv = p / size;
                float2 texel = 1.0 / max(gridSize - 1.0, float2(1.0));
                float dx = float(heightAt(uv + float2(texel.x, 0.0)) - heightAt(uv - float2(texel.x, 0.0)));
                float dy = float(heightAt(uv + float2(0.0, texel.y)) - heightAt(uv - float2(0.0, texel.y)));
                float2 normal = float2(dx, dy) * 2.0;

                float2 edgeVector = uv - 0.5;
                float edgeDistance = length(edgeVector * 2.0);
                float edgeBend = smoothstep(0.55, 1.0, edgeDistance);
                float2 radial = edgeVector / max(length(edgeVector), 0.001);
                float2 offset = normal * refraction + radial * edgeBend * refraction * 0.38;
                float2 source = sceneOrigin + p + offset;

                half4 base = backdrop.eval(sceneOrigin + p);
                half4 b0 = backdrop.eval(source);
                half4 b1 = backdrop.eval(source + float2(blurRadius, 0.0));
                half4 b2 = backdrop.eval(source - float2(blurRadius, 0.0));
                half4 b3 = backdrop.eval(source + float2(0.0, blurRadius));
                half4 b4 = backdrop.eval(source - float2(0.0, blurRadius));
                half3 blurred = (b0.rgb * 2.0 + b1.rgb + b2.rgb + b3.rgb + b4.rgb) / 6.0;

                half red = backdrop.eval(source + normal * dispersion).r;
                half blue = backdrop.eval(source - normal * dispersion).b;
                half3 refracted = half3(red, blurred.g, blue);

                float3 surfaceNormal = normalize(float3(-normal.x * 3.0, -normal.y * 3.0, 1.0));
                float specular = pow(max(dot(surfaceNormal, normalize(float3(-0.45, -0.55, 1.0))), 0.0), 22.0);
                float fresnel = pow(clamp(edgeDistance, 0.0, 1.0), 3.0);
                half3 glass = mix(refracted, half3(tint.rgb), half(tint.a));
                glass += half3(specular * 0.32 + fresnel * 0.12);
                glass = mix(base.rgb, glass, half(effectAmount));
                return half4(glass, 1.0);
            }
        """
    }
}
