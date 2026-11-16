package io.github.shaonialife.cardeck.navigation

import io.github.shaonialife.cardeck.iap2.RouteGuidanceEncoder
import org.json.JSONObject

/** A standalone replay fixture for one snapshot. Its component and index belong to this file. */
internal object GuidanceFramePreview {
    fun from(maneuver: String, meters: Int?): JSONObject? {
        if (meters == null || meters !in 0..100_000) return null
        val turn = when (maneuver) {
            "左转", "向左转", "往左转", "左转弯", "向左转弯", "往左转弯" -> RouteGuidanceEncoder.Turn.LEFT
            "右转", "向右转", "往右转", "右转弯", "向右转弯", "往右转弯" -> RouteGuidanceEncoder.Turn.RIGHT
            "直行" -> RouteGuidanceEncoder.Turn.STRAIGHT
            else -> return null
        }
        val fixture = RouteGuidanceEncoder(1)
        return JSONObject()
            .put("sentToVehicle", false)
            .put("fixtureComponentId", 1)
            .put("fixtureManeuverIndex", 0)
            .put("maneuverFrameHex", hex(fixture.maneuver(0, turn)))
            .put("routeFrameHex", hex(fixture.routeSet(0, meters)))
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
}
