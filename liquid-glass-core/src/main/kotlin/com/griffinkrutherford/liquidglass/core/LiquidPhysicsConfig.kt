package com.griffinkrutherford.liquidglass.core

/** Stable, immutable parameters for a deformable height-field membrane. */
data class LiquidPhysicsConfig(
    val columns: Int = 24,
    val rows: Int = 24,
    val stiffness: Float = 42f,
    val damping: Float = 5.5f,
    val viscosity: Float = 14f,
    val touchForce: Float = 1f,
    val maxDisplacement: Float = 1f,
    val fixedDeltaSeconds: Float = 1f / 120f,
    val maxCatchUpSteps: Int = 8,
) {
    init {
        require(columns >= 3 && rows >= 3) { "The membrane must be at least 3 by 3" }
        require(stiffness >= 0f && stiffness.isFinite())
        require(damping >= 0f && damping.isFinite())
        require(viscosity >= 0f && viscosity.isFinite())
        require(touchForce >= 0f && touchForce.isFinite())
        require(maxDisplacement > 0f && maxDisplacement.isFinite())
        require(fixedDeltaSeconds in 0f..0.05f && fixedDeltaSeconds > 0f)
        require(maxCatchUpSteps in 1..32)
    }
}
