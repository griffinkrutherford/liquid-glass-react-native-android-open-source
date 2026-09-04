package io.github.griffinkrutherford.liquidglass

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassShaderOptimizationTest {
    private val shaderSource: String by lazy {
        val relativeSource = Path.of("src/main/java/io/github/griffinkrutherford/liquidglass/LiquidGlassView.kt")
        val source = sequenceOf(relativeSource, Path.of("liquid-glass-view").resolve(relativeSource))
            .first(Files::exists)
        String(Files.readAllBytes(source))
            .substringAfter("const val GLASS_SHADER = \"\"\"")
            .substringBefore("\"\"\"")
    }

    @Test
    fun `zero blur uses the center sample exactly`() {
        val center = floatArrayOf(0.13f, 0.47f, 0.91f)
        val legacy = weightedCross(center, center, center, center, center)

        assertEquals(center.toList(), legacy.toList())
        assertTrue(shaderSource.contains("half3 blurred = b0.rgb;"))
        assertTrue(shaderSource.contains("if (materialBlur > 0.0)"))
    }

    @Test
    fun `zero dispersion reuses the green source sample exactly`() {
        val center = floatArrayOf(0.17f, 0.53f, 0.89f)
        val legacy = floatArrayOf(center[0], center[1], center[2])
        val optimized = floatArrayOf(center[0], center[1], center[2])

        assertEquals(legacy.toList(), optimized.toList())
        assertTrue(shaderSource.contains("half red = b0.r;"))
        assertTrue(shaderSource.contains("half blue = b0.b;"))
        assertTrue(shaderSource.contains("if (dispersion > 0.0)"))
    }

    @Test
    fun `nonzero paths retain all backdrop samples`() {
        val blurBranch = shaderSource.substringAfter("if (materialBlur > 0.0)").substringBefore("\n                }")
        val dispersionBranch = shaderSource.substringAfter("if (dispersion > 0.0)").substringBefore("\n                }")

        assertEquals(4, Regex("backdrop\\.eval").findAll(blurBranch).count())
        assertEquals(2, Regex("backdrop\\.eval").findAll(dispersionBranch).count())
    }

    @Test
    fun `exclusion scales displacement and internal reflection without replacing glass color`() {
        assertTrue(shaderSource.contains("if (exclusion.z > 0.001)"))
        assertTrue(shaderSource.contains("float exclusionMask ="))
        assertTrue(!shaderSource.contains("float exclusion ="))
        assertTrue(shaderSource.contains("offsetRed *= localRefraction;"))
        assertTrue(shaderSource.contains("offsetGreen *= localRefraction;"))
        assertTrue(shaderSource.contains("offsetBlue *= localRefraction;"))
        assertTrue(
            shaderSource.contains(
                "half(fresnel * mix(0.58, 0.42, regularity) * localRefraction)",
            ),
        )
        assertTrue(shaderSource.contains("float materialTint ="))
        assertTrue(shaderSource.contains("glass = mix(base.rgb, glass"))
    }

    @Test
    fun `undisturbed views compile a shader without physics texture samples`() {
        val source = String(
            Files.readAllBytes(
                sequenceOf(
                    Path.of("src/main/java/io/github/griffinkrutherford/liquidglass/LiquidGlassView.kt"),
                    Path.of("liquid-glass-view/src/main/java/io/github/griffinkrutherford/liquidglass/LiquidGlassView.kt"),
                ).first(Files::exists),
            ),
        )

        assertTrue(source.contains("ensureRuntimeShader(membraneWasDisturbed)"))
        assertTrue(source.contains("if (usePhysics) GLASS_SHADER else GLASS_SHADER_WITHOUT_PHYSICS"))
        assertTrue(source.contains("float2 physicsSlope = float2(0.0);"))
        assertTrue(source.contains("membraneWasDisturbed = true"))
    }

    @Test
    fun `retained shader state avoids unchanged input and uniform uploads`() {
        val source = String(
            Files.readAllBytes(
                sequenceOf(
                    Path.of("src/main/java/io/github/griffinkrutherford/liquidglass/LiquidGlassView.kt"),
                    Path.of("liquid-glass-view/src/main/java/io/github/griffinkrutherford/liquidglass/LiquidGlassView.kt"),
                ).first(Files::exists),
            ),
        )

        assertTrue(source.contains("if (backdropInputBitmap !== backdrop)"))
        assertTrue(source.contains("if (shaderUniformsDirty)"))
        assertTrue(source.contains("shaderUniformsDirty = false"))
        assertTrue(source.contains("shader.setFloatUniform(\"sceneOrigin\""))
    }

    private fun weightedCross(
        center: FloatArray,
        right: FloatArray,
        left: FloatArray,
        down: FloatArray,
        up: FloatArray,
    ): FloatArray = FloatArray(3) { channel ->
        (center[channel] * 2f + right[channel] + left[channel] + down[channel] + up[channel]) / 6f
    }
}
