package com.griffinkrutherford.liquidglass.core

import kotlin.math.max
import kotlin.math.min

/**
 * CPU-side mirror of the shader's backdrop sampling limits.
 *
 * A capture inflated by [maximumDistance] contains every refraction, blur, and internal-reflection
 * lookup made by the current glass shader. Keep the constants paired with the corresponding AGSL
 * expressions when its optical model changes.
 */
object ShaderSampleBounds {
    fun maximumDistance(
        width: Float,
        height: Float,
        refraction: Float,
        baseThickness: Float,
        blurRadius: Float,
        frostiness: Float,
        regularity: Float,
        bevelDepth: Float,
    ): Float {
        require(width >= 0f && width.isFinite())
        require(height >= 0f && height.isFinite())
        val safeRegularity = regularity.finiteOrZero().coerceIn(0f, 1f)
        val safeFrostiness = frostiness.finiteOrZero().coerceIn(0f, 1f)
        val thicknessRatio = (baseThickness.finiteOrZero().coerceAtLeast(0f) / max(height, 1f))
            .coerceIn(0f, 1f)

        // refractedRayOffset clamps every spectral ray to this magnitude.
        val refractionLimit = refraction.finiteOrZero().coerceAtLeast(0f) *
            lerp(0.86f, 1.32f, thicknessRatio)

        // edgeSharpness is in [0.22, 1], so its largest possible mix occurs at 1.
        val maximumBlur = blurRadius.finiteOrZero().coerceAtLeast(0f) *
            lerp(0.48f, 1f, safeRegularity) * (1f + safeFrostiness * 3.8f)

        val zRadius = min(
            bevelDepth.finiteOrZero().coerceAtLeast(0f),
            min(width, height) * 0.24f,
        )
        val reflectionDistance = zRadius * lerp(0.32f, 0.46f, safeRegularity)

        // Blur taps are relative to a refracted source; reflection is relative to the base pixel.
        return max(refractionLimit + maximumBlur, reflectionDistance)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount

    private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
}
