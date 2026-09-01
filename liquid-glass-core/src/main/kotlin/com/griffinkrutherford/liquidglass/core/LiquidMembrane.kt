package com.griffinkrutherford.liquidglass.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Allocation-free (during [step]) height-field membrane using semi-implicit Euler integration.
 * The outer ring is pinned, making energy leave the interactive region predictably.
 */
class LiquidMembrane(
    val config: LiquidPhysicsConfig = LiquidPhysicsConfig(),
) : LiquidSimulation {
    private val displacement = FloatArray(config.columns * config.rows)
    private val velocity = FloatArray(displacement.size)
    private val acceleration = FloatArray(displacement.size)
    private var width = 1f
    private var height = 1f
    private var settled = true

    override fun resize(width: Float, height: Float) {
        require(width > 0f && width.isFinite() && height > 0f && height.isFinite())
        this.width = width
        this.height = height
    }

    override fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float) {
        if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f || !strength.isFinite()) return
        val safeStrength = strength.coerceIn(-config.maxDisplacement * 20f, config.maxDisplacement * 20f) * config.touchForce
        val radiusSquared = radius * radius
        for (row in 1 until config.rows - 1) {
            val nodeY = row.toFloat() / (config.rows - 1) * height
            for (column in 1 until config.columns - 1) {
                val nodeX = column.toFloat() / (config.columns - 1) * width
                val dx = nodeX - x
                val dy = nodeY - y
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared < radiusSquared) {
                    val falloff = 1f - sqrt(distanceSquared) / radius
                    velocity[index(column, row)] += safeStrength * falloff * falloff
                }
            }
        }
        if (safeStrength != 0f) settled = false
    }

    override fun step(fixedDeltaSeconds: Float) {
        require(fixedDeltaSeconds > 0f && fixedDeltaSeconds <= 0.05f && fixedDeltaSeconds.isFinite())
        val dt = fixedDeltaSeconds
        val columns = config.columns
        val rows = config.rows

        for (row in 1 until rows - 1) {
            for (column in 1 until columns - 1) {
                val i = index(column, row)
                val center = displacement[i]
                val laplacian = displacement[i - 1] + displacement[i + 1] +
                    displacement[i - columns] + displacement[i + columns] - 4f * center
                acceleration[i] = -config.stiffness * center + config.viscosity * laplacian
            }
        }

        // Exponential damping is timestep-consistent and cannot reverse velocity.
        val dampingFactor = exp(-config.damping * dt)
        var peakVelocity = 0f
        var peakDisplacement = 0f
        for (row in 1 until rows - 1) {
            for (column in 1 until columns - 1) {
                val i = index(column, row)
                val nextVelocity = (velocity[i] + acceleration[i] * dt) * dampingFactor
                velocity[i] = nextVelocity
                val nextDisplacement = (displacement[i] + nextVelocity * dt)
                    .coerceIn(-config.maxDisplacement, config.maxDisplacement)
                displacement[i] = nextDisplacement
                peakVelocity = max(peakVelocity, abs(nextVelocity))
                peakDisplacement = max(peakDisplacement, abs(nextDisplacement))
            }
        }
        settled = peakDisplacement <= config.maxDisplacement * REST_DISPLACEMENT_EPSILON &&
            peakVelocity <= config.maxDisplacement * REST_VELOCITY_EPSILON
    }

    override fun snapshot(): SurfaceSnapshot = SurfaceSnapshot(
        columns = config.columns,
        rows = config.rows,
        width = width,
        height = height,
        displacementData = displacement.copyOf(),
        velocityData = velocity.copyOf(),
    )

    override fun reset() {
        displacement.fill(0f)
        velocity.fill(0f)
        acceleration.fill(0f)
        settled = true
    }

    override fun isAtRest(): Boolean = settled

    /** Copies the displacement field into a caller-owned array, avoiding per-frame allocation. */
    fun copyDisplacementsInto(target: FloatArray) {
        require(target.size == displacement.size) { "Target must hold columns * rows values" }
        displacement.copyInto(target)
    }

    internal fun maxAbsoluteDisplacement(): Float = displacement.maxOf { abs(it) }

    private fun index(column: Int, row: Int): Int = row * config.columns + column

    private companion object {
        const val REST_DISPLACEMENT_EPSILON = 1e-4f
        const val REST_VELOCITY_EPSILON = 1e-3f
    }
}
