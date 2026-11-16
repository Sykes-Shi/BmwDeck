package io.github.shaonialife.cardeck.render

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class MapClipTest {
    @Test fun fiftySlotsCoverFiveSecondsWithoutEncodingPastTheEnd() {
        assertEquals(50, MapClipTiming.frameCount)
        assertEquals(0L, MapClipTiming.pts(0))
        assertEquals(4_900_000L, MapClipTiming.pts(49))
        assertEquals(49, MapClipTiming.slot(4_999_999))
        assertEquals(50, MapClipTiming.slot(5_000_000))
        assertEquals(50, MapClipTiming.slot(8_000_000))
    }

    @Test fun lateSamplingSkipsMissedSlotsWithoutShorteningContainerDuration() {
        val slots = listOf(0L, 100_500L, 410_000L, 4_920_000L).map(MapClipTiming::slot)
        assertEquals(listOf(0, 1, 4, 49), slots)
        assertEquals(listOf(0L, 100_000L, 400_000L, 4_900_000L), slots.map(MapClipTiming::pts))
        assertEquals(5_000_000L, MapClipTiming.DURATION_US)
    }

    @Test fun convertsRedToLimitedRangeYuvAndRespectsNonZeroOffsetsAndPadding() {
        val pixels = MapClipPixels(4, 2)
        pixels.argb.fill(0xffff0000.toInt())
        pixels.convert()
        val y = ByteBuffer.allocate(12).apply { position(1) }
        pixels.writePlane(0, y, 6, 1)
        assertEquals(listOf(0, 82, 82, 82, 82, 0, 0, 82, 82, 82, 82, 0), bytes(y))
        val chroma = ByteBuffer.allocate(4)
        pixels.writePlane(1, chroma, 4, 2)
        chroma.position(1)
        pixels.writePlane(2, chroma, 4, 2)
        assertEquals(listOf(90, 240, 90, 240), bytes(chroma))
    }

    @Test fun grayscaleStaysNeutralAndYPlaneUsesTheFullFrame() {
        val pixels = MapClipPixels(2, 2)
        pixels.argb[1] = -1
        pixels.argb[3] = -1
        pixels.convert()
        val y = ByteBuffer.allocate(4)
        pixels.writePlane(0, y, 2, 1)
        assertEquals(listOf(16, 235, 16, 235), bytes(y))
        for (index in 1..2) {
            val chroma = ByteBuffer.allocate(1)
            pixels.writePlane(index, chroma, 1, 1)
            assertEquals(listOf(128), bytes(chroma))
        }
    }

    private fun bytes(buffer: ByteBuffer): List<Int> = buffer.array().map { it.toInt() and 255 }
}
