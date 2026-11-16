package io.github.shaonialife.cardeck.navigation

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaiduCardReaderTest {
    // Cropped reference cards contain only the arrow and a single line. JVM test assets only.
    private data class Sample(val width: Int, val height: Int, val rgb: IntArray) {
        fun fill(left: Int, top: Int, w: Int, h: Int, color: Int) {
            for (row in top until top + h) rgb.fill(color, row * width + left, row * width + left + w)
        }
    }

    private fun sample(name: String): Sample {
        val resource = checkNotNull(javaClass.getResourceAsStream("/navigation/$name.rgb.gz"))
        return DataInputStream(GZIPInputStream(resource).buffered()).use { input ->
            val width = input.readUnsignedShort()
            val height = input.readUnsignedShort()
            val pixels = IntArray(width * height) {
                (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            }
            Sample(width, height, pixels)
        }
    }

    private fun read(image: Sample) =
        BaiduCardReader.read(image.width, image.height) { x, y -> image.rgb[y * image.width + x] }

    @Test fun readsBothHorizontalDirectionsWithAnExplicitMetreUnit() {
        assertEquals(BaiduCardReader.Reading("左转", 104), read(sample("left-metres")))
        assertEquals(BaiduCardReader.Reading("右转", 91), read(sample("right-metres")))
    }

    @Test fun immediateOrIncompleteInstructionsReturnUnknown() {
        assertNull(read(sample("now")))
        for ((left, top, width, height) in listOf(listOf(0, 0, 96, 106), listOf(203, 10, 36, 44))) {
            val im = sample("left-metres")
            im.fill(left, top, width, height, 0)
            assertNull(read(im))
        }
    }

    @Test fun refusesExtraDecimalMarkAndUnknownStrokePatterns() {
        val decimal = sample("left-metres")
        decimal.fill(135, 44, 3, 4, 0xffffff)
        assertNull(read(decimal))

        val corrupt = sample("left-metres")
        corrupt.fill(143, 15, 25, 37, 0xffffff)
        assertNull(read(corrupt))
    }
}
