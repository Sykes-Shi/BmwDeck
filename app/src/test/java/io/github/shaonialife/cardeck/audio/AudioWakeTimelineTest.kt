package io.github.shaonialife.cardeck.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWakeTimelineTest {
    private val timeline = AudioWakeTimeline(48_000)
    private fun packet(ms: Long, ts: Long, epoch: Long = 0) = timeline.missingSamples(ms * 1_000_000, ts, epoch)

    @Test fun observedUnlockGapOnlySkipsMissingSamplesAfterTheEncoderDrains() {
        assertEquals(0L, packet(1_000, 100_000))
        timeline.woke(1_300_000_000, 0)
        assertEquals(0L, packet(2_227, 101_024))
        // Model the field trace: some queued frames arrived in a burst afterward.
        for (i in 1..45) assertEquals(0L, packet(2_227 + i * 10L, 101_024 + i * 1024L))
        val next = 101_024 + 46 * 1024L
        val missing = packet(2_727, next)
        assertEquals(1_727 * 48 - (next - 100_000), missing)
        assertEquals(0L, packet(2_750, next + missing + 1024))
    }

    @Test fun delayedWakeBroadcastCanStillMatchTheGap() {
        packet(500, 20_000)
        assertEquals(0L, packet(1_750, 21_024))
        timeline.woke(1_900_000_000, 0)
        assertEquals(0L, packet(1_950, 30_240))
        assertEquals(84_000L - 35_840, packet(2_250, 55_840))
    }

    @Test fun queuedAudioThatFullyCatchesUpKeepsItsOriginalTimestamps() {
        packet(1_000, 0)
        timeline.woke(1_100_000_000, 0)
        packet(2_226, 1_024)
        for (i in 1..80) assertEquals(0L, packet(2_226 + i * 6L, 1_024 + i * 1_024L))
        assertEquals(0L, packet(2_726, 82_944))
    }

    @Test fun noWakeOrAnOldWakeDoesNotRebaseAnotherGap() {
        packet(1_000, 0)
        packet(2_227, 1_024)
        assertEquals(0L, packet(2_727, 25_600))
        timeline.woke(3_000_000_000, 0)
        for (ms in 3_000L..12_000L step 200) packet(ms, ms * 48)
        packet(13_225, 577_024)
        assertEquals(0L, packet(13_725, 601_600))
    }

    @Test fun callReturnAndNewSenderCannotInheritPreviousWakeGap() {
        packet(1_000, 0)
        timeline.woke(1_100_000_000, 0)
        packet(2_227, 1_024)
        assertEquals(0L, packet(2_800, 2_048, epoch = 2))
        timeline.woke(3_000_000_000, null)
        assertEquals(0L, packet(4_050, 3_072, epoch = 2))
        assertEquals(0L, packet(4_550, 27_648, epoch = 2))
        val newTimeline = AudioWakeTimeline(48_000)
        newTimeline.woke(4_000_000_000, 0)
        assertEquals(0L, newTimeline.missingSamples(8_000_000_000, 0, 0))
    }

    @Test fun wakeCorrectionAdvancesTimeWithoutSkippingWireSequenceOrNonce() {
        val cursor = AudioRtpCursor(1_024)
        cursor.advance(0)
        val initial = cursor.syncPosition()
        cursor.advance(0, 34_560)
        val resumed = cursor.syncPosition()
        assertEquals(initial.segment + 1, resumed.segment)
        assertEquals(36_608L, resumed.timestamp)
        assertEquals(2L, cursor.nonce)
        assertEquals(2, cursor.sequence)
        cursor.advance(0)
        assertEquals(resumed.segment, cursor.syncPosition().segment)
        cursor.advance(2)
        assertEquals(resumed.segment + 1, cursor.syncPosition().segment)
    }
}
