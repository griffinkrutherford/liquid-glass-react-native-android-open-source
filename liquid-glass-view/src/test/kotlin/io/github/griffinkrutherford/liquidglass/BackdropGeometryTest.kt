package io.github.griffinkrutherford.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackdropGeometryTest {
    @Test fun evenDimensionsUseOneQuarterOfThePixels() {
        val geometry = BackdropGeometry(1440, 3120)
        assertEquals(720, geometry.bitmapWidth)
        assertEquals(1560, geometry.bitmapHeight)
        assertEquals(1440L * 3120 / 4, geometry.bitmapWidth.toLong() * geometry.bitmapHeight)
    }

    @Test fun oddAndTinyDimensionsRoundUpWithoutChangingScale() {
        for (size in listOf(1, 2, 3, 101, Int.MAX_VALUE)) {
            val geometry = BackdropGeometry(size, size)
            assertEquals((size.toLong() + 1) / 2, geometry.bitmapWidth.toLong())
            assertEquals(geometry.bitmapWidth, geometry.bitmapHeight)
            assertEquals(0.5f, geometry.scale, 0f)
        }
    }

    @Test fun nonzeroOriginsRemainInPhysicalCoordinates() {
        val geometry = BackdropGeometry(101, 73, originX = 17f, originY = -23f)
        assertEquals(17f, geometry.originX, 0f)
        assertEquals(-23f, geometry.originY, 0f)
        assertEquals(101, geometry.width)
        assertEquals(73, geometry.height)
        assertEquals(51, geometry.bitmapWidth)
        assertEquals(37, geometry.bitmapHeight)
    }

    @Test fun fullResolutionMappingRemainsAvailableInternally() {
        val geometry = BackdropGeometry(101, 73, downsampleFactor = 1)
        assertEquals(101, geometry.bitmapWidth)
        assertEquals(73, geometry.bitmapHeight)
        assertEquals(1f, geometry.scale, 0f)
    }

    @Test fun invalidGeometryCannotBePublished() {
        assertThrows(IllegalArgumentException::class.java) { BackdropGeometry(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { BackdropGeometry(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { BackdropGeometry(1, 1, originX = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { BackdropGeometry(1, 1, downsampleFactor = 3) }
    }
}
