package com.griffinkrutherford.liquidglass.core

/** Converts irregular frame times into deterministic fixed simulation steps. */
class FixedTimestepRunner(
    private val simulation: LiquidSimulation,
    private val fixedDeltaSeconds: Float,
    private val maxCatchUpSteps: Int,
) {
    constructor(simulation: LiquidMembrane) : this(
        simulation,
        simulation.config.fixedDeltaSeconds,
        simulation.config.maxCatchUpSteps,
    )

    private var accumulator = 0f

    init {
        require(fixedDeltaSeconds > 0f && fixedDeltaSeconds <= 0.05f)
        require(maxCatchUpSteps > 0)
    }

    /** Returns the number of fixed steps executed. Excess catch-up time is discarded safely. */
    fun advance(elapsedSeconds: Float): Int {
        if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return 0
        accumulator = (accumulator + elapsedSeconds)
            .coerceAtMost(fixedDeltaSeconds * maxCatchUpSteps)
        var steps = 0
        while (accumulator + 1e-7f >= fixedDeltaSeconds && steps < maxCatchUpSteps) {
            simulation.step(fixedDeltaSeconds)
            accumulator -= fixedDeltaSeconds
            steps++
        }
        return steps
    }

    fun reset() {
        accumulator = 0f
        simulation.reset()
    }
}
