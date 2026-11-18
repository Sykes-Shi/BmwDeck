package io.github.shaonialife.cardeck.bt

import java.util.UUID

/** A failed or incomplete SDP query is never evidence that a service is absent. */
internal object BluetoothConnectionDiagnostics {
    enum class SdpOutcome { PRESENT, NOT_FOUND, TIMEOUT, PERMISSION_DENIED, UNAVAILABLE, CANCELLED }

    fun fromUuids(uuids: List<UUID>?, expected: UUID): SdpOutcome = when {
        uuids == null -> SdpOutcome.UNAVAILABLE
        expected in uuids -> SdpOutcome.PRESENT
        else -> SdpOutcome.NOT_FOUND
    }

    fun canAttempt(explicitTarget: Boolean, outcome: SdpOutcome): Boolean =
        outcome != SdpOutcome.CANCELLED && (explicitTarget || outcome == SdpOutcome.PRESENT)

    fun shouldCache(outcome: SdpOutcome): Boolean = outcome == SdpOutcome.PRESENT

    fun targetStillAllowed(startedExplicit: Boolean, targetPresent: Boolean, targetMatches: Boolean): Boolean =
        if (targetPresent) targetMatches else !startedExplicit

    fun shouldFallback(active: Boolean, interrupted: Boolean): Boolean = active && !interrupted

    fun ownsSession(current: Any?, owner: Any): Boolean = current === owner

    /** An invalid socket only terminates the worker when its start was cancelled. */
    fun retryAfterInvalidSocket(running: Boolean, sameGeneration: Boolean): Boolean = running && sameGeneration

    /** Bounded retry cooling, not a permanent negative SDP cache. */
    class ProbeBackoff(private val intervalMs: Long = 30_000, private val maxDevices: Int = 64) {
        private val retries = LinkedHashMap<String, Long>()
        fun shouldProbe(key: String, now: Long): Boolean = now >= (retries[key] ?: Long.MIN_VALUE)
        fun note(key: String, now: Long, outcome: SdpOutcome) {
            if (outcome == SdpOutcome.PRESENT) {
                retries.remove(key)
            } else if (outcome != SdpOutcome.CANCELLED) {
                retries.remove(key)
                retries[key] = now + intervalMs
                while (retries.size > maxDevices) retries.remove(retries.keys.first())
            }
        }
    }

    fun profileResult(remaining: List<String>, unknown: Boolean): String = when {
        remaining.isNotEmpty() -> "部分或全部仍在线"
        unknown -> "断开结果无法确认"
        else -> "确认已断开"
    }

    /** Never emit platform exception messages, which can contain identifiers. */
    fun errorCategory(error: Throwable): String {
        if (error is SecurityException) return "permission"
        val message = error.message.orEmpty().lowercase(java.util.Locale.ROOT)
        return when {
            "service discovery failed" in message || "sdp failed" in message -> "service_discovery_failed"
            "timed out" in message || "timeout" in message -> "timeout"
            "read failed" in message || "socket closed" in message -> "read_failed_or_closed"
            "permission" in message || "not allowed" in message -> "permission"
            else -> "other"
        }
    }
}
