package io.github.griffinkrutherford.liquidglass

/** Physical scene coordinates stay independent of rounded texture allocation dimensions. */
internal data class BackdropGeometry(
    val width: Int,
    val height: Int,
    val originX: Float = 0f,
    val originY: Float = 0f,
    val downsampleFactor: Int = 2,
) {
    init {
        require(width > 0 && height > 0)
        require(originX.isFinite() && originY.isFinite())
        require(downsampleFactor == 1 || downsampleFactor == 2)
    }

    // Integer ceiling avoids Float rounding and Int overflow at large dimensions.
    val bitmapWidth: Int = width / downsampleFactor + if (width % downsampleFactor != 0) 1 else 0
    val bitmapHeight: Int = height / downsampleFactor + if (height % downsampleFactor != 0) 1 else 0
    val scale: Float = 1f / downsampleFactor
}
