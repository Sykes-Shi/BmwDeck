package io.github.shaonialife.cardeck.navigation

import io.github.shaonialife.cardeck.iap2.Iap2Csm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedGuidanceSessionTest {
    private val frames = mutableListOf<Iap2Csm.Frame>()
    private val flushSizes = mutableListOf<Int>()
    private var rejectMessage = -1
    private val session = ParkedGuidanceSession({ bytes ->
        val frame = Iap2Csm.Parser().offer(bytes).single()
        frames.add(frame)
        frame.messageId != rejectMessage
    }, {}, { flushSizes += frames.size })
    private val start = Iap2Csm.u16(0, 1) + Iap2Csm.param(1, byteArrayOf()) + Iap2Csm.param(2, byteArrayOf())
    private fun field(frame: Int, id: Int) = Iap2Csm.parseParams(frames[frame].body).single { it.id == id }.value.toList()

    @Test
    fun realSubscriptionThenConfirmedLeftThenTimedClear() {
        assertFalse(session.show(true, 104, 0))
        session.subscribe(start)
        assertTrue(frames.isEmpty())
        assertTrue(session.show(true, 104, 10))
        assertFalse(session.show(false, 91, 30))
        assertEquals(listOf(0x5201, 0x5201, 0x5202), frames.map { it.messageId })
        assertEquals(listOf<Byte>(1), field(0, 20))
        assertEquals(listOf<Byte>(0, 0, 0, 104), field(1, 10))
        assertEquals(listOf<Byte>(1), field(2, 3))
        session.subscribe(start)
        session.tick(6009)
        assertEquals(3, frames.size)
        session.tick(6010)
        session.tick(8000)
        assertEquals(5, frames.size)
        assertEquals(listOf<Byte>(0), field(3, 1))
        assertTrue(field(3, 13).isEmpty())
        assertEquals(listOf<Byte>(0), field(4, 20))
    }

    @Test
    fun unsubscribedOrLostConnectionNeverReplaysPreviousCue() {
        session.subscribe(start)
        session.unsubscribe(Iap2Csm.u16(0, 99))
        assertTrue(session.show(false, 91, 10))
        assertEquals(listOf<Byte>(2), field(2, 3))
        session.unsubscribe(Iap2Csm.u16(0, 1))
        session.tick(6010)
        assertEquals(3, frames.size)
        assertFalse(session.show(false, 91, 7000))
        session.subscribe(start)
        session.reset()
        assertFalse(session.show(false, 91, 7100))
    }

    @Test
    fun malformedSubscriptionRejectedAndPartialUpdateWithdrawn() {
        session.subscribe(start + byteArrayOf(7))
        assertFalse(session.show(true, 104, 0))
        session.subscribe(start)
        assertFalse(session.show(true, -1, 0))
        rejectMessage = 0x5202
        assertFalse(session.show(true, 104, 0))
        assertEquals(listOf<Byte>(0), field(3, 1))
        rejectMessage = -1
        assertTrue(session.show(false, 30, 200))
    }

    @Test fun cancellationFlushesBothCleanupFramesBeforeCallingOnlyTheSuccessfulOwner() {
        val completions = mutableListOf<String>()
        assertFalse(session.canShow())
        session.subscribe(start)
        assertTrue(session.canShow())
        assertTrue(session.show(true, 104, 10) {
            assertEquals(listOf(3, 5), flushSizes)
            completions += "first"
        })
        assertFalse(session.canShow())
        assertFalse(session.show(false, 91, 20) { completions += "rejected" })
        session.cancel()
        session.tick(6020)
        session.cancel()
        assertTrue(session.canShow())
        assertEquals(listOf("first"), completions)
        assertEquals(5, frames.size)

        assertTrue(session.show(false, 91, 9000) { completions += "next" })
        session.tick(15_000)
        session.reset()
        assertEquals(listOf("first", "next"), completions)
        assertFalse(session.canShow())
    }

    @Test fun changedComponentFinishesOldPreviewAndDisconnectNeverSendsItsFramesToANewComponent() {
        var ended = 0
        session.subscribe(start)
        assertTrue(session.show(true, 104, 0) { ended++ })
        // A repeated subscription with different requested fields does not discard the original source declaration.
        session.subscribe(Iap2Csm.u16(0, 1))
        session.subscribe(Iap2Csm.u16(0, 2))
        assertEquals(1, ended)
        assertEquals(listOf<Byte>(0, 1), field(3, 0))
        assertEquals(listOf<Byte>(0, 1), field(4, 0))
        assertTrue(session.show(false, 91, 400) { ended++ })
        val queued = frames.size
        session.reset()
        session.subscribe(start)
        session.tick(10000)
        assertEquals(queued, frames.size)
        assertEquals(2, ended)
    }

    @Test fun matchingUnsubscribeEndsActiveOnceAndIncompletePreviewHasNoOwner() {
        var ended = 0
        session.subscribe(start)
        rejectMessage = 0x5202
        assertFalse(session.show(false, 91, 0) { ended++ })
        assertEquals(0, ended)
        assertTrue(session.canShow())
        rejectMessage = -1
        assertTrue(session.show(false, 91, 5) { ended++ })
        val queued = frames.size
        session.unsubscribe(Iap2Csm.u16(0, 99))
        assertEquals(0, ended)
        session.unsubscribe(Iap2Csm.u16(0, 1))
        session.reset()
        session.cancel()
        assertEquals(1, ended)
        assertEquals(queued, frames.size)
        assertFalse(session.canShow())
    }
}
