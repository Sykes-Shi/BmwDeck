package io.github.shaonialife.cardeck.bt

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingProfileCleanupTest {
    @Test
    fun preservedConnectionIsNeverDisconnected() {
        var calls = 0
        PairingProfileCleanup.run({ false }, { calls++ }, { true })
        assertEquals(0, calls)
    }

    @Test
    fun normalCleanupRetriesOnce() {
        var calls = 0
        PairingProfileCleanup.run({ true }, { calls++ }, { true })
        assertEquals(2, calls)
    }

    @Test
    fun policyOrTargetChangeDuringWaitCancelsRetry() {
        var calls = 0
        var allowed = true
        PairingProfileCleanup.run({ allowed }, { calls++ }, { allowed = false; true })
        assertEquals(1, calls)
    }

    @Test
    fun interruptedWaitCancelsRetry() {
        var calls = 0
        PairingProfileCleanup.run({ true }, { calls++ }, { false })
        assertEquals(1, calls)
    }
}
