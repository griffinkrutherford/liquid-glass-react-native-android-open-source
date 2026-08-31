package com.griffinkrutherford.liquidglass.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidMembraneTest {
    private val config = LiquidPhysicsConfig(columns = 9, rows = 9, fixedDeltaSeconds = 1f / 120f)

    @Test
    fun impulseIsRadialAndPinnedBoundaryDoesNotMove() {
        val membrane = LiquidMembrane(config).apply { resize(100f, 100f) }
        membrane.applyImpulse(50f, 50f, 30f, 2f)
        membrane.step(config.fixedDeltaSeconds)
        val state = membrane.snapshot()

        assertTrue(state.displacement(4, 4) > state.displacement(3, 4))
        assertEquals(0f, state.displacement(0, 4))
        assertEquals(0f, state.displacement(8, 4))
    }

    @Test
    fun identicalInputsProduceBitIdenticalState() {
        fun run(): SurfaceSnapshot {
            val membrane = LiquidMembrane(config).apply { resize(320f, 180f) }
            membrane.applyImpulse(170f, 80f, 55f, -3f)
            repeat(1_000) { membrane.step(config.fixedDeltaSeconds) }
            return membrane.snapshot()
        }

        val first = run()
        val second = run()
        assertContentEquals(first.copyDisplacements(), second.copyDisplacements())
        assertContentEquals(first.copyVelocities(), second.copyVelocities())
    }

    @Test
    fun dampingSettlesAndLongRunRemainsFinite() {
        val membrane = LiquidMembrane(config).apply { resize(100f, 100f) }
        membrane.applyImpulse(50f, 50f, 35f, 10f)
        val initialVelocity = abs(membrane.snapshot().velocity(4, 4))
        repeat(30_000) { membrane.step(config.fixedDeltaSeconds) }
        val state = membrane.snapshot()

        assertTrue(abs(state.velocity(4, 4)) < initialVelocity)
        assertTrue(state.copyDisplacements().all(Float::isFinite))
        assertTrue(state.copyVelocities().all(Float::isFinite))
        assertTrue(membrane.maxAbsoluteDisplacement() < 0.0001f)
    }

    @Test
    fun displacementIsClampedUnderExtremeImpulse() {
        val membrane = LiquidMembrane(config).apply { resize(100f, 100f) }
        membrane.applyImpulse(50f, 50f, 100f, Float.MAX_VALUE)
        repeat(500) { membrane.step(config.fixedDeltaSeconds) }
        assertTrue(membrane.snapshot().copyDisplacements().all { abs(it) <= config.maxDisplacement })
    }

    @Test
    fun resetClearsAllDynamicState() {
        val membrane = LiquidMembrane(config).apply { resize(100f, 100f) }
        membrane.applyImpulse(50f, 50f, 40f, 5f)
        repeat(20) { membrane.step(config.fixedDeltaSeconds) }
        membrane.reset()

        assertTrue(membrane.snapshot().copyDisplacements().all { it == 0f })
        assertTrue(membrane.snapshot().copyVelocities().all { it == 0f })
    }
}
