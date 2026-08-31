package io.github.griffinkrutherford.liquidglass

import android.annotation.TargetApi
import android.animation.ValueAnimator
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
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.griffinkrutherford.liquidglass.core.FixedTimestepRunner
import com.griffinkrutherford.liquidglass.core.LiquidMembrane
import com.griffinkrutherford.liquidglass.core.LiquidPhysicsConfig
import kotlin.math.abs

/** A touch-reactive glass surface that refracts sibling content captured by [LiquidGlassScene]. */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    var effect: LiquidGlassEffect = LiquidGlassEffect.REGULAR
        set(value) {
            if (field == value) return
            field = value
            animateMaterialChange(
                targetMaterialization = if (value == LiquidGlassEffect.NONE) 0f else 1f,
                targetRegularity = when (value) {
                    LiquidGlassEffect.CLEAR -> 0f
                    LiquidGlassEffect.NOCTURNE -> 0.72f
                    else -> 1f
                },
                targetFrostiness = if (value == LiquidGlassEffect.SATIN) 1f else 0f,
                targetDarkness = if (value == LiquidGlassEffect.NOCTURNE) 1f else 0f,
            )
        }
    var colorScheme: LiquidGlassColorScheme = LiquidGlassColorScheme.SYSTEM
        set(value) { field = value; invalidate() }
    var interactive: Boolean = false
    var draggable: Boolean = false
    var animated: Boolean = true
    var animationDurationMillis: Long = 320L
        set(value) { field = value.coerceAtLeast(0L) }
    var cornerRadius = dp(32f)
        set(value) { field = value.coerceAtLeast(0f); invalidate() }
    var refractionStrength = dp(24f)
        set(value) { field = value.coerceIn(0f, dp(80f)); invalidate() }
    var dispersion = dp(2.4f)
        set(value) { field = value.coerceIn(0f, dp(12f)); invalidate() }
    var indexOfRefraction = 1.47f
        set(value) { field = value.coerceIn(1.01f, 3f); invalidate() }
    var bevelDepth = dp(22f)
        set(value) { field = value.coerceIn(dp(2f), dp(48f)); invalidate() }
    var baseThickness = dp(6f)
        set(value) { field = value.coerceIn(0f, dp(64f)); invalidate() }
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
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartTranslationX = 0f
    private var dragStartTranslationY = 0f
    private var hasDragged = false
    private var materialization = 1f
    private var regularity = 1f
    private var frostiness = 0f
    private var darkness = 0f
    private var materialAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 33) runtimeShader = RuntimeShader(GLASS_SHADER)
    }

    internal fun setSceneBackdrop(bitmap: Bitmap) {
        sceneBackdrop = bitmap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        for (index in 0 until childCount) {
            measureChildWithMargins(getChildAt(index), widthMeasureSpec, paddingLeft + paddingRight, heightMeasureSpec, paddingTop + paddingBottom)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

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

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width > 0 && height > 0) {
            membrane.resize(width.toFloat(), height.toFloat())
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
        shader.setFloatUniform("sceneOrigin", x, y)
        shader.setFloatUniform("gridSize", normalBitmap.width.toFloat(), normalBitmap.height.toFloat())
        shader.setFloatUniform("cornerRadius", cornerRadius)
        shader.setFloatUniform("refraction", refractionStrength)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("indexOfRefraction", indexOfRefraction)
        shader.setFloatUniform("bevelDepth", bevelDepth)
        shader.setFloatUniform("baseThickness", baseThickness)
        shader.setFloatUniform("blurRadius", blurRadius)
        shader.setFloatUniform("effectAmount", effectAmount)
        shader.setFloatUniform("regularity", regularity)
        shader.setFloatUniform("frostiness", frostiness)
        shader.setFloatUniform("darkness", darkness)
        shader.setFloatUniform("materialization", materialization)
        shader.setFloatUniform("appearance", resolvedAppearance())
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
        if (materialization <= 0.001f) return
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(125, 224, 245, 255), Color.argb(70, 126, 196, 232), Color.argb(105, 216, 242, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        paint.alpha = (materialization * if (effect == LiquidGlassEffect.CLEAR) 150 else 220).toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        paint.alpha = 255
        paint.shader = null
    }

    private fun drawBorder(canvas: Canvas) {
        if (materialization <= 0.001f) return
        borderPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.argb(235, 255, 255, 255), Color.argb(45, 255, 255, 255), Color.argb(170, 168, 220, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        val inset = borderPaint.strokeWidth / 2f
        borderPaint.alpha = (materialization * 255).toInt()
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, cornerRadius, cornerRadius, borderPaint)
        borderPaint.alpha = 255
        borderPaint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!interactive) return false
                parent.requestDisallowInterceptTouchEvent(true)
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = translationX
                dragStartTranslationY = translationY
                hasDragged = false
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggable) {
                    if (abs(event.rawX - dragStartRawX) + abs(event.rawY - dragStartRawY) > dp(6f)) hasDragged = true
                    updateDragPosition(event)
                }
                if (abs(event.x - lastTouchX) + abs(event.y - lastTouchY) > dp(7f)) {
                    applyImpulse(event.x, event.y, 2.8f)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!hasDragged && event.actionMasked == MotionEvent.ACTION_UP) performClick()
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
        materialAnimator?.cancel()
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

    private fun updateDragPosition(event: MotionEvent) {
        val container = parent as? ViewGroup ?: return
        val targetX = dragStartTranslationX + event.rawX - dragStartRawX
        val targetY = dragStartTranslationY + event.rawY - dragStartRawY
        translationX = targetX.coerceIn(-left.toFloat(), (container.width - right).toFloat())
        translationY = targetY.coerceIn(-top.toFloat(), (container.height - bottom).toFloat())
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun animateMaterialChange(
        targetMaterialization: Float,
        targetRegularity: Float,
        targetFrostiness: Float,
        targetDarkness: Float,
    ) {
        materialAnimator?.cancel()
        if (!animated || animationDurationMillis == 0L) {
            materialization = targetMaterialization
            regularity = targetRegularity
            frostiness = targetFrostiness
            darkness = targetDarkness
            invalidate()
            return
        }
        val startMaterialization = materialization
        val startRegularity = regularity
        val startFrostiness = frostiness
        val startDarkness = darkness
        materialAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDurationMillis
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                materialization = startMaterialization + (targetMaterialization - startMaterialization) * fraction
                regularity = startRegularity + (targetRegularity - startRegularity) * fraction
                frostiness = startFrostiness + (targetFrostiness - startFrostiness) * fraction
                darkness = startDarkness + (targetDarkness - startDarkness) * fraction
                invalidate()
            }
            start()
        }
    }

    private fun resolvedAppearance(): Float {
        val dark = when (colorScheme) {
            LiquidGlassColorScheme.DARK -> true
            LiquidGlassColorScheme.LIGHT -> false
            LiquidGlassColorScheme.SYSTEM ->
                resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) -1f else 1f
    }

    private companion object {
        const val GLASS_SHADER = """
            uniform shader backdrop;
            uniform shader heightMap;
            uniform float2 size;
            uniform float2 sceneOrigin;
            uniform float2 gridSize;
            uniform float cornerRadius;
            uniform float refraction;
            uniform float dispersion;
            uniform float indexOfRefraction;
            uniform float bevelDepth;
            uniform float baseThickness;
            uniform float blurRadius;
            uniform float effectAmount;
            uniform float4 tint;
            uniform float regularity;
            uniform float frostiness;
            uniform float darkness;
            uniform float materialization;
            uniform float appearance;

            half heightAt(float2 uv) {
                float2 coordinate = clamp(uv, float2(0.0), float2(1.0)) * (gridSize - 1.0);
                return heightMap.eval(coordinate).r;
            }

            float roundedBoxSdf(float2 p) {
                float2 halfSize = size * 0.5;
                float radius = min(cornerRadius, min(halfSize.x, halfSize.y));
                float2 q = abs(p - halfSize) - (halfSize - radius);
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
            }

            float2 edgeNormal(float2 p) {
                float epsilon = 1.25;
                float dx = roundedBoxSdf(p + float2(epsilon, 0.0)) - roundedBoxSdf(p - float2(epsilon, 0.0));
                float dy = roundedBoxSdf(p + float2(0.0, epsilon)) - roundedBoxSdf(p - float2(0.0, epsilon));
                return normalize(float2(dx, dy) + float2(0.0001));
            }

            float bevelHeight(float2 p, float radius) {
                float distanceInside = max(-roundedBoxSdf(p), 0.0);
                if (distanceInside >= radius) return radius;
                return sqrt(max(distanceInside * (2.0 * radius - distanceInside), 0.0));
            }

            float2 bevelGradient(float2 p, float radius) {
                float epsilon = 1.5;
                float dx = bevelHeight(p + float2(epsilon, 0.0), radius) -
                    bevelHeight(p - float2(epsilon, 0.0), radius);
                float dy = bevelHeight(p + float2(0.0, epsilon), radius) -
                    bevelHeight(p - float2(0.0, epsilon), radius);
                return float2(dx, dy) / (2.0 * epsilon);
            }

            float2 refractedRayOffset(float2 slope, float opticalHeight, float ior, float gain) {
                float3 normal = normalize(float3(-slope.x, -slope.y, 1.0));
                float3 incident = float3(0.0, 0.0, -1.0);
                float eta = 1.0 / max(ior, 1.001);
                float incidentCosine = dot(normal, incident);
                float discriminant = max(1.0 - eta * eta * (1.0 - incidentCosine * incidentCosine), 0.0);
                float3 transmitted = eta * incident -
                    (eta * incidentCosine + sqrt(discriminant)) * normal;
                float pathLength = baseThickness + opticalHeight * 2.0;
                float2 result = transmitted.xy / max(abs(transmitted.z), 0.08) * pathLength * gain;
                float limit = refraction * mix(0.86, 1.32, clamp(baseThickness / max(size.y, 1.0), 0.0, 1.0));
                float magnitude = length(result);
                return result * min(1.0, limit / max(magnitude, 0.001));
            }

            half4 main(float2 p) {
                float2 uv = p / size;
                float2 texel = 1.0 / max(gridSize - 1.0, float2(1.0));
                float dx = float(heightAt(uv + float2(texel.x, 0.0)) - heightAt(uv - float2(texel.x, 0.0)));
                float dy = float(heightAt(uv + float2(0.0, texel.y)) - heightAt(uv - float2(0.0, texel.y)));
                float2 physicsSlope = float2(dx, dy) * mix(1.1, 0.82, regularity);

                float insideDistance = max(-roundedBoxSdf(p), 0.0);
                float zRadius = min(bevelDepth, min(size.x, size.y) * 0.24);
                float rimCoordinate = clamp(insideDistance / zRadius, 0.0, 1.0);
                float rim = 1.0 - smoothstep(0.0, 1.0, rimCoordinate);
                float2 boundaryNormal = edgeNormal(p);
                float opticalHeight = bevelHeight(p, zRadius);
                float2 lensCoordinate = (p - size * 0.5) / max(size * 0.5, float2(1.0));
                float lensDistance = clamp(length(lensCoordinate) * 0.7071, 0.0, 1.0);
                float lensProfile = smoothstep(0.0, 1.0, lensDistance);
                float2 broadLensSlope = lensCoordinate * mix(0.22, 0.32, regularity) * lensProfile;
                float2 surfaceSlope = bevelGradient(p, zRadius) + broadLensSlope + physicsSlope;
                float opticalGain = refraction / max(zRadius, 1.0) * mix(0.92, 0.72, regularity);

                float iorDelta = dispersion * 0.0012;
                float iorRed = max(indexOfRefraction - iorDelta, 1.001);
                float iorBlue = indexOfRefraction + iorDelta;
                float2 offsetRed = refractedRayOffset(surfaceSlope, opticalHeight, iorRed, opticalGain);
                float2 offsetGreen = refractedRayOffset(surfaceSlope, opticalHeight, indexOfRefraction, opticalGain);
                float2 offsetBlue = refractedRayOffset(surfaceSlope, opticalHeight, iorBlue, opticalGain);
                float2 sourceRed = sceneOrigin + p + offsetRed;
                float2 sourceGreen = sceneOrigin + p + offsetGreen;
                float2 sourceBlue = sceneOrigin + p + offsetBlue;

                half4 base = backdrop.eval(sceneOrigin + p);
                half4 b0 = backdrop.eval(sourceGreen);
                float edgeSharpness = 1.0 - rim * 0.78;
                float materialBlur = blurRadius * mix(0.48, edgeSharpness, regularity) * (1.0 + frostiness * 3.8);
                half4 b1 = backdrop.eval(sourceGreen + float2(materialBlur, 0.0));
                half4 b2 = backdrop.eval(sourceGreen - float2(materialBlur, 0.0));
                half4 b3 = backdrop.eval(sourceGreen + float2(0.0, materialBlur));
                half4 b4 = backdrop.eval(sourceGreen - float2(0.0, materialBlur));
                half3 blurred = (b0.rgb * 2.0 + b1.rgb + b2.rgb + b3.rgb + b4.rgb) / 6.0;

                half red = backdrop.eval(sourceRed).r;
                half blue = backdrop.eval(sourceBlue).b;
                half3 refracted = half3(red, blurred.g, blue);
                float interiorTransmission = (1.0 - rim) * mix(0.18, 0.08, regularity);
                refracted = mix(refracted, blurred, half(interiorTransmission));
                refracted = mix(refracted, blurred, half(frostiness * 0.76));

                float3 surfaceNormal = normalize(float3(-surfaceSlope.x, -surfaceSlope.y, 1.0));
                float f0 = pow((indexOfRefraction - 1.0) / (indexOfRefraction + 1.0), 2.0);
                float fresnel = f0 + (1.0 - f0) * pow(1.0 - abs(surfaceNormal.z), 5.0);
                float2 reflectionDirection = normalize(surfaceSlope + boundaryNormal * 0.001);
                half3 internalReflection = backdrop.eval(
                    sceneOrigin + p - reflectionDirection * zRadius * mix(0.32, 0.46, regularity)
                ).rgb;
                refracted = mix(refracted, internalReflection, half(fresnel * mix(0.58, 0.42, regularity)));

                float schemeLift = appearance * mix(0.025, 0.055, regularity);
                float materialTint = tint.a * mix(0.42, 1.0, regularity) + frostiness * 0.12;
                half3 glass = mix(refracted, half3(tint.rgb), half(materialTint));
                glass += half3(schemeLift);
                glass *= half(1.0 - darkness * 0.52);
                glass = mix(glass, glass * half3(0.62, 0.72, 0.84), half(darkness * 0.38));
                float opticalAmount = effectAmount * mix(0.90, 1.0, regularity) * materialization;
                glass = mix(base.rgb, glass, half(opticalAmount));
                return half4(glass, 1.0);
            }
        """
    }
}
