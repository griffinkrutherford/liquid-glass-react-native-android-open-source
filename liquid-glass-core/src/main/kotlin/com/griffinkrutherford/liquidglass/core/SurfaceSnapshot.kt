package com.griffinkrutherford.liquidglass.core

/** A detached, immutable-in-practice copy suitable for rendering on another thread. */
class SurfaceSnapshot internal constructor(
    val columns: Int,
    val rows: Int,
    val width: Float,
    val height: Float,
    private val displacementData: FloatArray,
    private val velocityData: FloatArray,
) {
    fun displacement(column: Int, row: Int): Float = displacementData[index(column, row)]

    fun velocity(column: Int, row: Int): Float = velocityData[index(column, row)]

    fun copyDisplacements(): FloatArray = displacementData.copyOf()

    fun copyVelocities(): FloatArray = velocityData.copyOf()

    private fun index(column: Int, row: Int): Int {
        require(column in 0 until columns && row in 0 until rows)
        return row * columns + column
    }
}
