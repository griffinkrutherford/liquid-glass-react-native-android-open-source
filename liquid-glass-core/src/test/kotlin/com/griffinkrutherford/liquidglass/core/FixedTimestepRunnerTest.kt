package com.griffinkrutherford.liquidglass.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun defaultPhysicsRunsAt60HzAtEverySupportedRenderCadence() {
        assertEquals(1f / 60f, LiquidPhysicsConfig().fixedDeltaSeconds)

        for (renderRate in listOf(60, 90, 120, 144)) {
            val simulation = CountingSimulation()
            val runner = FixedTimestepRunner(
                simulation = simulation,
                fixedDeltaSeconds = LiquidPhysicsConfig().fixedDeltaSeconds,
                maxCatchUpSteps = 8,
            )
            var updateFrames = 0
            repeat(renderRate) {
                val steps = runner.advance(1f / renderRate)
                if (steps > 0) updateFrames++
                assertTrue(steps <= 1, "$renderRate Hz rendering must not cause redundant physics steps")
            }

            assertEquals(60, simulation.stepCount, "$renderRate Hz rendering changed physics cadence")
            assertTrue(updateFrames <= 60, "$renderRate Hz rendering caused excess normal-map uploads")
        }
    }

    @Test
    fun activeCatchUpStopsAtRestAndDiscardsRemainingTime() {
        val simulation = SettlingSimulation()
        val runner = FixedTimestepRunner(simulation, 0.01f, 8)

        assertEquals(1, runner.advanceIfActive(0.075f))
        assertEquals(1, simulation.stepCount)
        assertEquals(0, runner.advanceIfActive(1f))
        simulation.resting = false
        assertEquals(0, runner.advanceIfActive(0.005f))
        assertEquals(1, runner.advanceIfActive(0.005f))
        assertEquals(2, simulation.stepCount)
    }

    @Test
    fun settlingOnLastStepAlsoDiscardsFractionalTime() {
        val simulation = SettlingSimulation()
        val runner = FixedTimestepRunner(simulation, 0.01f, 8)

        assertEquals(1, runner.advanceIfActive(0.015f))
        simulation.resting = false
        assertEquals(0, runner.advanceIfActive(0.005f))
        assertEquals(1, runner.advanceIfActive(0.005f))
    }

    @Test
    fun unconditionalAdvanceStillExecutesAllRequestedSteps() {
        val simulation = SettlingSimulation()
        val runner = FixedTimestepRunner(simulation, 0.01f, 8)

        assertEquals(7, runner.advance(0.075f))
        assertEquals(7, simulation.stepCount)
    }

    private class SettlingSimulation : LiquidSimulation {
        var stepCount = 0
        var resting = false

        override fun resize(width: Float, height: Float) = Unit
        override fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float) = Unit
        override fun step(fixedDeltaSeconds: Float) {
            stepCount++
            resting = true
        }
        override fun snapshot(): SurfaceSnapshot = error("Not needed")
        override fun reset() {
            stepCount = 0
            resting = true
        }
        override fun isAtRest(): Boolean = resting
    }

    private class CountingSimulation : LiquidSimulation {
        var stepCount = 0

        override fun resize(width: Float, height: Float) = Unit
        override fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float) = Unit
        override fun step(fixedDeltaSeconds: Float) { stepCount++ }
        override fun snapshot(): SurfaceSnapshot = error("Not needed")
        override fun reset() { stepCount = 0 }
        override fun isAtRest(): Boolean = false
    }
}
