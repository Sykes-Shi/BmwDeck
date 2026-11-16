package io.github.shaonialife.cardeck.iap2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteGuidanceEncoderTest {
    private val encoder = RouteGuidanceEncoder(1)

    @Test
    fun sourceRepliesContainOnlyRequestedFieldsAndNeverAssertRouteActivity() {
        assertArrayEquals(hex("4040 001d 5201 0006 0000 0001 000c 0013 4361724465636b00 0005 0014 01"),
            encoder.sourceDeclaration("CarDeck", true))
        assertArrayEquals(hex("4040 0018 5201 0006 0000 0001 000c 0013 e799bee5baa64100"),
            encoder.sourceDeclaration("百度A", null))
        assertArrayEquals(hex("4040 0011 5201 0006 0000 0001 0005 0014 00"),
            encoder.sourceDeclaration(null, false))
        assertThrows(IllegalArgumentException::class.java) { encoder.sourceDeclaration(null, null) }
    }

    @Test
    fun leftAt104MetersHasMatchingActionIndexAndBigEndianDistance() {
        val maneuver = encoder.maneuver(0x0123, RouteGuidanceEncoder.Turn.LEFT)
        val route = encoder.routeSet(0x0123, 104)
        // Manually constructed vectors, not output from Iap2Csm.encode or a BMW capture.
        assertArrayEquals(hex("4040 0017 5202 0006 0000 0001 0006 0001 0123 0005 0003 01"), maneuver)
        assertArrayEquals(hex("4040 001f 5201 0006 0000 0001 0005 0001 01 0008 000a 00000068 0006 000d 0123"), route)
        val decoder = Iap2Csm.Parser()
        val frames = decoder.offer(maneuver.copyOfRange(0, 13)) + decoder.offer(maneuver.copyOfRange(13, maneuver.size) + route)
        assertEquals(listOf(0x5202, 0x5201), frames.map { it.messageId })
        val actionIndex = Iap2Csm.parseParams(frames[0].body).single { it.id == 1 }.asU16List()
        val currentIndices = Iap2Csm.parseParams(frames[1].body).single { it.id == 13 }.asU16List()
        assertEquals(actionIndex, currentIndices)
    }

    @Test
    fun nextRightAndStraightHaveDistinctWireActionsAndDoNotRelabelTheDistanceField() {
        assertArrayEquals(hex("4040 0017 5202 0006 0000 0001 0006 0001 0124 0005 0003 02"),
            encoder.maneuver(0x0124, RouteGuidanceEncoder.Turn.RIGHT))
        assertArrayEquals(hex("4040 0017 5202 0006 0000 0001 0006 0001 0125 0005 0003 03"),
            encoder.maneuver(0x0125, RouteGuidanceEncoder.Turn.STRAIGHT))
        assertArrayEquals(hex("4040 0014 5201 0006 0000 0001 0008 000a 0000004a"), encoder.distance(74))
        assertArrayEquals(hex("4040 0014 5201 0006 0000 0001 0008 000a 00000000"), encoder.distance(0))
    }

    @Test
    fun validExplicitEndClearsTheListAndTotalWithoutLeavingAnActiveTurn() {
        assertArrayEquals(hex("4040 001b 5201 0006 0000 0001 0005 0001 00 0004 000d 0006 000e 0000"), encoder.endRoute())
    }

    @Test
    fun rejectsTruncationAndSupportsMultiByteComponentAndDistance() {
        assertThrows(IllegalArgumentException::class.java) { RouteGuidanceEncoder(65536) }
        assertThrows(IllegalArgumentException::class.java) { encoder.maneuver(-1, RouteGuidanceEncoder.Turn.LEFT) }
        assertThrows(IllegalArgumentException::class.java) { encoder.routeSet(65536, 104) }
        assertThrows(IllegalArgumentException::class.java) { encoder.distance(-1) }
        assertArrayEquals(hex("4040 0014 5201 0006 0000 a13c 0008 000a 00010002"), RouteGuidanceEncoder(0xa13c).distance(65538))
    }

    private fun hex(value: String): ByteArray = value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
