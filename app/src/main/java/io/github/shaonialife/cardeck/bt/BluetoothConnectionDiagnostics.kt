package io.github.shaonialife.cardeck.bt

import android.bluetooth.BluetoothSocketException
import android.os.Build
import androidx.annotation.RequiresApi
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

    /** UUID list comes from BluetoothDevice.getUuids(), which is a possibly stale cache. */
    fun cachedServices(uuids: List<UUID>?, expected: UUID): String =
        if (uuids == null) "count=unknown iap2=unknown" else
            "count=${uuids.size} iap2=${expected in uuids}"

    /** Read only a platform-defined integer, never log the exception message. */
    fun socketErrorCode(error: Throwable, sdkInt: Int = Build.VERSION.SDK_INT): Int? {
        // The runtime guard is deliberately explicit: the injected value alone cannot protect
        // Android 12/13 from resolving the API-34 BluetoothSocketException type.
        if (Build.VERSION.SDK_INT < 34) return null
        if (sdkInt < 34) return null
        return try {
            SocketErrorApi34.read(error)
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    /** Fixed labels mirror platform codes; a generic failure is not a diagnosis. */
    fun socketErrorCodeLabel(code: Int?): String = when (code) {
        null -> "unavailable"
        0 -> "unspecified"
        1 -> "l2cap_unknown"
        2 -> "l2cap_acl_failure"
        3 -> "l2cap_client_security_failure"
        4 -> "l2cap_insufficient_authentication"
        5 -> "l2cap_insufficient_authorization"
        6 -> "l2cap_insufficient_encryption_key_size"
        7 -> "l2cap_insufficient_encryption"
        8 -> "l2cap_invalid_source_cid"
        9 -> "l2cap_source_cid_already_allocated"
        10 -> "l2cap_unacceptable_parameters"
        11 -> "l2cap_invalid_parameters"
        12 -> "l2cap_no_resources"
        13 -> "l2cap_no_psm_available"
        14 -> "l2cap_timeout"
        15 -> "bluetooth_off"
        16 -> "socket_manager_failure"
        17 -> "socket_closed"
        18 -> "generic_socket_connection_failure"
        19 -> "null_device"
        20 -> "rpc_failure"
        21 -> "unix_file_socket_creation_failure"
        else -> "unrecognized"
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

/** Isolated to avoid resolving API-34 types on Android 12 and 13. */
@RequiresApi(34)
private object SocketErrorApi34 {
    fun read(error: Throwable): Int? = (error as? BluetoothSocketException)?.errorCode
}
