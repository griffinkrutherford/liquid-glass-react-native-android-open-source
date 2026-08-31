package com.griffinkrutherford.liquidglass.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FixedTimestepRunnerTest {
    @Test
    fun renderSchedulesReachEquivalentFixedStepState() {
        val config = LiquidPhysicsConfig(columns = 7, rows = 7, fixedDeltaSeconds = 1f / 120f)

        fun run(frameRate: Int): FloatArray {
            val membrane = LiquidMembrane(config).apply {
                resize(100f, 100f)
                applyImpulse(50f, 50f, 30f, 2f)
            }
            val runner = FixedTimestepRunner(membrane)
            repeat(frameRate) { runner.advance(1f / frameRate) }
            return membrane.snapshot().copyDisplacements()
        }

        assertContentEquals(run(60), run(120))
    }

    @Test
    fun catchUpWorkIsCapped() {
        val membrane = LiquidMembrane(LiquidPhysicsConfig(maxCatchUpSteps = 4))
        val runner = FixedTimestepRunner(membrane)
        assertEquals(4, runner.advance(60f))
        assertEquals(0, runner.advance(0f))
    }
}
