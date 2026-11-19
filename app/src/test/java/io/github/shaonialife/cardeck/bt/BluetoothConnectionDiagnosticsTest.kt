package io.github.shaonialife.cardeck.bt

import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothConnectionDiagnosticsTest {
    private val expected = UUID.fromString("00000000-deca-fade-deca-deafdecacaff")
    private val other = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")
    private val outcomes = BluetoothConnectionDiagnostics.SdpOutcome.entries

    @Test fun distinguishesPresentAbsentAndUnavailable() {
        assertEquals(BluetoothConnectionDiagnostics.SdpOutcome.PRESENT, BluetoothConnectionDiagnostics.fromUuids(listOf(other, expected), expected))
        assertEquals(BluetoothConnectionDiagnostics.SdpOutcome.NOT_FOUND, BluetoothConnectionDiagnostics.fromUuids(listOf(other), expected))
        assertEquals(BluetoothConnectionDiagnostics.SdpOutcome.UNAVAILABLE, BluetoothConnectionDiagnostics.fromUuids(null, expected))
    }

    @Test fun explicitTargetIsNeverBlockedByProbeOutcome() {
        outcomes.forEach { assertEquals(it != BluetoothConnectionDiagnostics.SdpOutcome.CANCELLED, BluetoothConnectionDiagnostics.canAttempt(true, it)) }
        outcomes.forEach { assertEquals(it == BluetoothConnectionDiagnostics.SdpOutcome.PRESENT, BluetoothConnectionDiagnostics.canAttempt(false, it)) }
    }

    @Test fun onlyConfirmedPresenceIsCached() {
        outcomes.forEach { assertEquals(it == BluetoothConnectionDiagnostics.SdpOutcome.PRESENT, BluetoothConnectionDiagnostics.shouldCache(it)) }
    }

    @Test fun changedTargetCannotConnectPreviousCandidate() {
        assertTrue(BluetoothConnectionDiagnostics.targetStillAllowed(false, false, false))
        assertTrue(BluetoothConnectionDiagnostics.targetStillAllowed(false, true, true))
        assertFalse(BluetoothConnectionDiagnostics.targetStillAllowed(false, true, false))
        assertFalse(BluetoothConnectionDiagnostics.targetStillAllowed(true, false, false))
        assertTrue(BluetoothConnectionDiagnostics.targetStillAllowed(true, true, true))
        assertFalse(BluetoothConnectionDiagnostics.targetStillAllowed(true, true, false))
    }

    @Test fun unknownProfileStateIsNotReportedAsDisconnected() {
        assertEquals("确认已断开", BluetoothConnectionDiagnostics.profileResult(emptyList(), false))
        assertEquals("断开结果无法确认", BluetoothConnectionDiagnostics.profileResult(emptyList(), true))
        assertEquals("部分或全部仍在线", BluetoothConnectionDiagnostics.profileResult(listOf("HFP"), true))
    }

    @Test fun fallbackRequiresCurrentAndUninterruptedAttempt() {
        assertTrue(BluetoothConnectionDiagnostics.shouldFallback(true, false))
        assertFalse(BluetoothConnectionDiagnostics.shouldFallback(false, false))
        assertFalse(BluetoothConnectionDiagnostics.shouldFallback(true, true))
        assertFalse(BluetoothConnectionDiagnostics.shouldFallback(false, true))
    }

    @Test fun oldSessionCannotClearNewSession() {
        val old = Any()
        val current = Any()
        assertFalse(BluetoothConnectionDiagnostics.ownsSession(current, old))
        assertTrue(BluetoothConnectionDiagnostics.ownsSession(current, current))
    }

    @Test fun invalidSocketRetriesOnlyWhileSameGenerationIsRunning() {
        assertTrue(BluetoothConnectionDiagnostics.retryAfterInvalidSocket(true, true))
        assertFalse(BluetoothConnectionDiagnostics.retryAfterInvalidSocket(false, true))
        assertFalse(BluetoothConnectionDiagnostics.retryAfterInvalidSocket(true, false))
        assertFalse(BluetoothConnectionDiagnostics.retryAfterInvalidSocket(false, false))
    }

    @Test fun negativeProbeIsBoundedAndExpires() {
        val backoff = BluetoothConnectionDiagnostics.ProbeBackoff(intervalMs = 30_000, maxDevices = 2)
        assertTrue(backoff.shouldProbe("a", 1_000))
        backoff.note("a", 1_000, BluetoothConnectionDiagnostics.SdpOutcome.TIMEOUT)
        assertFalse(backoff.shouldProbe("a", 30_999))
        assertTrue(backoff.shouldProbe("a", 31_000))
        backoff.note("b", 2_000, BluetoothConnectionDiagnostics.SdpOutcome.NOT_FOUND)
        backoff.note("c", 3_000, BluetoothConnectionDiagnostics.SdpOutcome.PERMISSION_DENIED)
        assertTrue(backoff.shouldProbe("a", 3_000))
        backoff.note("b", 3_000, BluetoothConnectionDiagnostics.SdpOutcome.PRESENT)
        assertTrue(backoff.shouldProbe("b", 3_000))
    }

    @Test fun errorsAreMappedWithoutReturningMessages() {
        assertEquals("service_discovery_failed", BluetoothConnectionDiagnostics.errorCategory(IOException("Service discovery failed for AA:BB:CC:DD:EE:FF")))
        assertEquals("read_failed_or_closed", BluetoothConnectionDiagnostics.errorCategory(IOException("read failed, socket might closed")))
        assertEquals("read_failed_or_closed_or_timeout", BluetoothConnectionDiagnostics.errorCategory(IOException("read failed, socket might closed or timeout, read ret: -1")))
        assertEquals("timeout", BluetoothConnectionDiagnostics.errorCategory(IOException("operation timed out")))
        assertEquals("permission", BluetoothConnectionDiagnostics.errorCategory(SecurityException("private details")))
        val category = BluetoothConnectionDiagnostics.errorCategory(IOException("private message"))
        assertEquals("other", category)
        assertFalse(category.contains("private"))
    }

    @Test fun selectedProbeIsBoundedAndSpacedFromEachAttempt() {
        val schedule = BluetoothConnectionDiagnostics.SelectedSdpSchedule()
        assertEquals(1, schedule.next("a", 1_000, true, true))
        assertNull(schedule.next("a", 20_999, true, true))
        assertEquals(2, schedule.next("a", 21_000, true, true))
        assertNull(schedule.next("a", 40_999, true, true))
        assertEquals(3, schedule.next("a", 41_000, true, true))
        assertNull(schedule.next("a", 100_000, true, true))
    }

    @Test fun selectedProbeStopsOnPresenceAndResetsForTargetAndCancellation() {
        val schedule = BluetoothConnectionDiagnostics.SelectedSdpSchedule()
        assertEquals(1, schedule.next("a", 0, true, true))
        schedule.record("a", BluetoothConnectionDiagnostics.SdpOutcome.PRESENT)
        assertNull(schedule.next("a", 20_000, true, true))
        assertEquals(1, schedule.next("b", 20_000, true, true))
        schedule.record("a", BluetoothConnectionDiagnostics.SdpOutcome.PRESENT)
        assertEquals(2, schedule.next("b", 40_000, true, true))
        assertNull(schedule.next("b", 60_000, true, false))
        assertEquals(1, schedule.next("b", 60_000, true, true))
        assertNull(schedule.next("b", 80_000, false, true))
        assertEquals(1, schedule.next("b", 80_000, true, true))
        assertNull(schedule.next(null, 100_000, true, true))
        assertEquals(1, schedule.next("b", 100_000, true, true))
    }

    class ClassicSupported { var called = -1; fun fetchUuids(transport: Int): Boolean { called = transport; return true } }
    class ClassicRejected { fun fetchUuids(transport: Int): Boolean = transport < 0 }
    class ClassicMissing
    class ClassicPermissionDenied { fun fetchUuids(transport: Int): Boolean { throw SecurityException("private $transport") } }
    class ClassicError { fun fetchUuids(transport: Int): Boolean { throw IllegalStateException("private $transport") } }
    class ClassicUnknown { fun fetchUuids(transport: Int): String? = if (transport < 0) "unexpected" else null }

    @Test fun classicTransportIsOnlyRequestedOnSecondSelectedProbeOnApi37() {
        val legacy = BluetoothConnectionDiagnostics.SdpTransport.LEGACY
        val bredr = BluetoothConnectionDiagnostics.SdpTransport.BREDR
        assertEquals(legacy, BluetoothConnectionDiagnostics.selectedProbeTransport(36, 2))
        assertEquals(legacy, BluetoothConnectionDiagnostics.selectedProbeTransport(37, 1))
        assertEquals(bredr, BluetoothConnectionDiagnostics.selectedProbeTransport(37, 2))
        assertEquals(legacy, BluetoothConnectionDiagnostics.selectedProbeTransport(37, 3))
    }

    @Test fun reflectiveClassicRequestReportsOutcomesWithoutRetrying() {
        val device = ClassicSupported()
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.STARTED, BluetoothConnectionDiagnostics.requestBrEdr(device, 1))
        assertEquals(1, device.called)
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.NOT_STARTED, BluetoothConnectionDiagnostics.requestBrEdr(ClassicRejected(), 1))
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.METHOD_MISSING, BluetoothConnectionDiagnostics.requestBrEdr(ClassicMissing(), 1))
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.PERMISSION_DENIED, BluetoothConnectionDiagnostics.requestBrEdr(ClassicPermissionDenied(), 1))
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.INVOCATION_FAILED, BluetoothConnectionDiagnostics.requestBrEdr(ClassicError(), 1))
        assertEquals(BluetoothConnectionDiagnostics.SdpStart.RESULT_UNKNOWN, BluetoothConnectionDiagnostics.requestBrEdr(ClassicUnknown(), 1))
    }
}
