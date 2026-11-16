package io.github.shaonialife.cardeck.iap2

/**
 * Standalone iAP2 control-message encoder for one subscribed route-guidance component.
 * Each result is one CSM frame, without a link/session header. There is no transport or telemetry.
 *
 * Wire field types checked against luka-dev/mib2q-carplay-rgi (91e301d), rgd_tlv.h and rgd_tlv.c.
 * Only encode guidance observed in the map; snapshots can omit unknown route totals, roads and lanes.
 */
internal class RouteGuidanceEncoder(private val componentId: Int) {
    init {
        require(componentId in 0..0xffff) { "componentId must fit u16" }
    }

    enum class Turn(val wireValue: Int) {
        LEFT(1),
        RIGHT(2),
        STRAIGHT(3),
    }

    /** Send only the requested source fields. Capability describes the selected source, not active navigation. */
    fun sourceDeclaration(name: String?, supportsGuidance: Boolean?): ByteArray {
        require(name != null || supportsGuidance != null) { "no requested source fields" }
        val nameField = name?.let { Iap2Csm.string(0x0013, it) } ?: byteArrayOf()
        val capability = supportsGuidance?.let { Iap2Csm.u8(0x0014, if (it) 1 else 0) } ?: byteArrayOf()
        return frame(0x5201, nameField + capability)
    }

    /** Index belongs to the maneuver; retain it as the distance counts down. */
    fun maneuver(maneuverIndex: Int, turn: Turn): ByteArray {
        require(maneuverIndex in 0..0xffff) { "maneuverIndex must fit u16" }
        return frame(
            0x5202,
            Iap2Csm.u16(0x0001, maneuverIndex) + Iap2Csm.u8(0x0003, turn.wireValue),
        )
    }

    /** Describes the current actionable maneuver; it makes no assertion about later route steps. */
    fun routeSet(maneuverIndex: Int, meters: Int): ByteArray {
        require(maneuverIndex in 0..0xffff) { "maneuverIndex must fit u16" }
        return frame(
            0x5201,
            Iap2Csm.u8(0x0001, 1) + distanceField(meters) + Iap2Csm.u16List(0x000d, intArrayOf(maneuverIndex)),
        )
    }

    /** A route delta in metres to the next maneuver. Omitted fields retain their previous values. */
    fun distance(meters: Int): ByteArray = frame(0x5201, distanceField(meters))

    /** Explicit navigation end. A missing input sample is not an end event. */
    fun endRoute(): ByteArray =
        frame(
            0x5201,
            Iap2Csm.u8(0x0001, 0) + Iap2Csm.u16List(0x000d, intArrayOf()) + Iap2Csm.u16(0x000e, 0),
        )

    private fun distanceField(meters: Int): ByteArray {
        require(meters >= 0) { "distance must be non-negative" }
        return Iap2Csm.u32(0x000a, meters.toLong())
    }

    private fun frame(messageId: Int, fields: ByteArray): ByteArray =
        Iap2Csm.encode(messageId, Iap2Csm.u16(0x0000, componentId) + fields)
}
