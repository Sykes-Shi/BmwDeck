package io.github.shaonialife.cardeck.bt

import java.util.UUID
import java.lang.reflect.InvocationTargetException

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

    /** Per-start, per-selected-target diagnostic budget; never used for automatic candidates. */
    class SelectedSdpSchedule(private val intervalMs: Long = 20_000, private val maxAttempts: Int = 3) {
        private var target: String? = null
        private var attempts = 0
        private var nextAt = 0L
        private var found = false

        fun clear() {
            target = null
            attempts = 0
            nextAt = 0L
            found = false
        }

        fun next(key: String?, now: Long, enabled: Boolean, running: Boolean): Int? {
            if (!running || !enabled || key == null) {
                clear()
                return null
            }
            if (target != key) {
                clear()
                target = key
            }
            if (found || attempts >= maxAttempts || (attempts > 0 && now < nextAt)) return null
            attempts += 1
            nextAt = now + intervalMs
            return attempts
        }

        fun record(key: String, outcome: SdpOutcome) {
            if (target == key && outcome == SdpOutcome.PRESENT) found = true
        }
    }

    enum class SdpTransport { LEGACY, BREDR }
    enum class SdpStart { STARTED, NOT_STARTED, METHOD_MISSING, PERMISSION_DENIED, INVOCATION_FAILED, RESULT_UNKNOWN }

    /** API 37 adds public fetchUuids(int); only the second selected-device diagnostic requests BR/EDR. */
    fun selectedProbeTransport(sdk: Int, probe: Int): SdpTransport =
        if (sdk >= 37 && probe == 2) SdpTransport.BREDR else SdpTransport.LEGACY

    /** No fallback after invocation: a failing or ambiguous call could already have initiated discovery. */
    fun requestBrEdr(device: Any, transport: Int): SdpStart {
        val method = try {
            device.javaClass.getMethod("fetchUuids", Int::class.javaPrimitiveType)
        } catch (_: NoSuchMethodException) {
            return SdpStart.METHOD_MISSING
        } catch (_: SecurityException) {
            return SdpStart.PERMISSION_DENIED
        }
        return try {
            when (method.invoke(device, transport)) {
                true -> SdpStart.STARTED
                false -> SdpStart.NOT_STARTED
                else -> SdpStart.RESULT_UNKNOWN
            }
        } catch (error: InvocationTargetException) {
            if (error.targetException is SecurityException) SdpStart.PERMISSION_DENIED else SdpStart.INVOCATION_FAILED
        } catch (_: SecurityException) {
            SdpStart.PERMISSION_DENIED
        } catch (_: ReflectiveOperationException) {
            SdpStart.INVOCATION_FAILED
        } catch (_: RuntimeException) {
            SdpStart.INVOCATION_FAILED
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
        val mentionsReadOrClose = "read failed" in message || "socket closed" in message || "socket might closed" in message
        val mentionsTimeout = "timed out" in message || "timeout" in message
        return when {
            "service discovery failed" in message || "sdp failed" in message -> "service_discovery_failed"
            mentionsReadOrClose && mentionsTimeout -> "read_failed_or_closed_or_timeout"
            mentionsTimeout -> "timeout"
            mentionsReadOrClose -> "read_failed_or_closed"
            "permission" in message || "not allowed" in message -> "permission"
            else -> "other"
        }
    }
}
