package com.griffinkrutherford.liquidglass.core

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderSampleBoundsTest {
    @Test
    fun boundContainsEveryShaderSampleFamily() {
        val random = Random(20260901)
        repeat(10_000) {
            val width = random.nextFloat() * 2_000f + 1f
            val height = random.nextFloat() * 2_000f + 1f
            val refraction = random.nextFloat() * 100f
            val thickness = random.nextFloat() * height * 1.5f
            val blur = random.nextFloat() * 30f
            val frostiness = random.nextFloat()
            val regularity = random.nextFloat()
            val bevel = random.nextFloat() * 200f
            val bound = ShaderSampleBounds.maximumDistance(
                width, height, refraction, thickness, blur, frostiness, regularity, bevel,
            )

            val refractionLimit = refraction * lerp(
                0.86f,
                1.32f,
                (thickness / max(height, 1f)).coerceIn(0f, 1f),
            )
            for (rim in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) {
                val edgeSharpness = 1f - rim * 0.78f
                val materialBlur = blur * lerp(0.48f, edgeSharpness, regularity) *
                    (1f + frostiness * 3.8f)
                assertTrue(refractionLimit + materialBlur <= bound + 0.0001f)
            }
            val reflection = min(bevel, min(width, height) * 0.24f) *
                lerp(0.32f, 0.46f, regularity)
            assertTrue(refractionLimit <= bound + 0.0001f)
            assertTrue(reflection <= bound + 0.0001f)
        }
    }

    @Test
    fun calculationMatchesKnownDefaultCase() {
        // Refraction (24 * 0.86) plus blur (2.2 * 1.0) is larger than reflection here.
        assertEquals(
            22.84f,
            ShaderSampleBounds.maximumDistance(
                width = 320f,
                height = 180f,
                refraction = 24f,
                baseThickness = 0f,
                blurRadius = 2.2f,
                frostiness = 0f,
                regularity = 1f,
                bevelDepth = 22f,
            ),
            absoluteTolerance = 0.0001f,
        )
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount
}
