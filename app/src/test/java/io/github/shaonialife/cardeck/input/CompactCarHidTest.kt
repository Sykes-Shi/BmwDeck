package io.github.shaonialife.cardeck.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactCarHidTest {
    // Standards-based fixture, not a capture from an iDrive 6 vehicle.
    private val knob = hex("""
        05 01 09 08 a1 01
        05 09 09 01 15 00 25 01 75 01 95 01 81 02
        05 0c 0a 24 02 81 02
        75 06 81 03
        05 01 09 38 15 81 25 7f 75 08 81 06 c0
    """)

    @Test
    fun compactKnobPreservesRotationAndDoesNotRepeatHeldButtons() {
        val decoder = requireNotNull(CompactCarHid.fromDescriptor(knob))
        val trace = listOf("01 00", "01 02", "01 ff", "00 00", "02 00", "02 00", "00 00", "01 00")
        assertEquals(listOf(
            CarControllerInput(CarControllerAction.CONFIRM), CarControllerInput(CarControllerAction.NEXT, 2),
            CarControllerInput(CarControllerAction.PREVIOUS), CarControllerInput(CarControllerAction.BACK),
            CarControllerInput(CarControllerAction.CONFIRM),
        ), trace.flatMap { requireNotNull(decoder.decode(hex(it))).navigation })
    }

    @Test
    fun interpretsConsumerSelectorsAfterTheReportIdAndFromTheLogicalMinimum() {
        val media = hex("""
            05 0c 09 01 a1 01 85 07
            15 01 25 03 09 cd 09 b5 09 b6 75 08 95 01 81 40 c0
        """)
        val decoder = requireNotNull(CompactCarHid.fromDescriptor(media))
        assertNull(decoder.decode(hex("06 02")))
        assertEquals(listOf(0xcd), decoder.decode(hex("07 01"))?.media)
        assertEquals(listOf(0xb5), decoder.decode(hex("07 02"))?.media)
        assertEquals(emptyList<Int>(), decoder.decode(hex("07 02"))?.media)
        assertEquals(emptyList<Int>(), decoder.decode(hex("07 00"))?.media)
        assertEquals(listOf(0xb5), decoder.decode(hex("07 02"))?.media)
        assertEquals(listOf(0xb6), decoder.decode(hex("07 03"))?.media)
    }

    @Test
    fun sameLengthTouchAndTelephonyReportsAreNotMapped() {
        assertNull(CompactCarHid.fromDescriptor(knob.copyOf().apply { this[1] = 0x0d }))
        assertNull(CompactCarHid.fromDescriptor(hex("05 0b 09 01 a1 01 15 00 25 01 09 20 75 10 95 01 81 02 c0")))
        assertNull(CompactCarHid.fromDescriptor(knob.copyOf(knob.size - 1)))
        assertNull(CompactCarHid.fromDescriptor(knob.copyOf().apply { this[lastIndex - 1] = 0x02 }
            .copyOfRange(24, knob.size))) // incomplete collection cannot be guessed from its payload
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
