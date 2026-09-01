package com.griffinkrutherford.liquidglass.core

interface LiquidSimulation {
    fun resize(width: Float, height: Float)
    fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float)
    fun step(fixedDeltaSeconds: Float)
    fun snapshot(): SurfaceSnapshot
    fun reset()

    /** True when the surface has settled closely enough that further stepping cannot change output. */
    fun isAtRest(): Boolean
}
