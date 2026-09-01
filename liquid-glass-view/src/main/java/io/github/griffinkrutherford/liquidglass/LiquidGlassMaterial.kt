package io.github.griffinkrutherford.liquidglass

import android.graphics.Color

/**
 * Named optical presets: matched bundles of optical parameters that are known to look correct
 * together.
 *
 * These are the same bundles the React Native `material` prop resolves to, so an Android-only
 * integration and a React Native integration produce identical glass. Presets cover optics only;
 * geometry (corner radius), behaviour (interactive, draggable), motion and colour scheme are never
 * part of a preset, so switching material never moves or re-shapes a view.
 *
 * Dimension values are declared in density-independent pixels and converted when applied by
 * [applyMaterial]; [dispersion], [indexOfRefraction], [effectAmount] and [tintAmount] are
 * dimensionless.
 *
 * ```kotlin
 * glassView.applyMaterial(LiquidGlassMaterial.CRYSTAL)
 * glassView.blurRadius = 8f * resources.displayMetrics.density // per-property override
 * ```
 */
enum class LiquidGlassMaterial(
    /** Shader variant selected by this preset. */
    val effect: LiquidGlassEffect,
    /** Maximum refracted sampling displacement, in dp. */
    val refractionStrengthDp: Float,
    /** Chromatic dispersion amount, dimensionless. */
    val dispersion: Float,
    /** Index of refraction, dimensionless. */
    val indexOfRefraction: Float,
    /** Depth of the rounded edge bevel, in dp. */
    val bevelDepthDp: Float,
    /** Slab thickness, in dp. */
    val thicknessDp: Float,
    /** Blur radius applied to the refracted sample, in dp. */
    val blurRadiusDp: Float,
    /** Blend between the untouched backdrop (0) and the full glass result (1). */
    val effectAmount: Float,
    /** Tint colour applied to transmitted light. */
    val tintColor: Int,
    /** Tint strength, 0..1. */
    val tintAmount: Float,
) {
    /** Near-colourless, highly transmissive glass: strong refraction, crisp edges, almost no tint. */
    CRYSTAL(
        effect = LiquidGlassEffect.CLEAR,
        refractionStrengthDp = 34f,
        dispersion = 4.2f,
        indexOfRefraction = 1.52f,
        bevelDepthDp = 18f,
        thicknessDp = 8f,
        blurRadiusDp = 0.8f,
        effectAmount = 1f,
        tintColor = Color.rgb(255, 255, 255),
        tintAmount = 0.03f,
    ),

    /** Frosted, diffusing glass: soft blur, reduced refraction, cool milky tint. */
    SATIN(
        effect = LiquidGlassEffect.SATIN,
        refractionStrengthDp = 14f,
        dispersion = 1.2f,
        indexOfRefraction = 1.42f,
        bevelDepthDp = 26f,
        thicknessDp = 10f,
        blurRadiusDp = 6.5f,
        effectAmount = 0.92f,
        tintColor = Color.rgb(232, 242, 255),
        tintAmount = 0.22f,
    ),

    /** Smoked, dark glass: moderate refraction, thick slab, strong slate tint. */
    NOCTURNE(
        effect = LiquidGlassEffect.NOCTURNE,
        refractionStrengthDp = 20f,
        dispersion = 2f,
        indexOfRefraction = 1.49f,
        bevelDepthDp = 24f,
        thicknessDp = 12f,
        blurRadiusDp = 3.4f,
        effectAmount = 0.98f,
        tintColor = Color.rgb(90, 107, 133),
        tintAmount = 0.3f,
    ),
    ;

    companion object {
        /** Resolves a case-insensitive preset name, or null when the name is unknown. */
        @JvmStatic
        fun fromName(name: String?): LiquidGlassMaterial? = when (name?.lowercase()) {
            "crystal" -> CRYSTAL
            "satin" -> SATIN
            "nocturne" -> NOCTURNE
            else -> null
        }
    }
}

/**
 * Applies every optical parameter of [material] to this view, converting the preset's dp values to
 * pixels for the current display. Set individual properties afterwards to override the preset.
 */
fun LiquidGlassView.applyMaterial(material: LiquidGlassMaterial) {
    val density = resources.displayMetrics.density
    effect = material.effect
    refractionStrength = material.refractionStrengthDp * density
    dispersion = material.dispersion
    indexOfRefraction = material.indexOfRefraction
    bevelDepth = material.bevelDepthDp * density
    baseThickness = material.thicknessDp * density
    blurRadius = material.blurRadiusDp * density
    effectAmount = material.effectAmount
    tintColor = material.tintColor
    tintAmount = material.tintAmount
}
