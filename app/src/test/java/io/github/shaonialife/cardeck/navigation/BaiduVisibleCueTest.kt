package io.github.shaonialife.cardeck.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaiduVisibleCueTest {
    // Synthetic shapes at the reference layout positions. No address or real trip data in the test.
    private fun layout(x: Int, y: Int): Int = when {
        x in 493..499 && y in 16..95 -> 0xff00c8ff.toInt()
        x in 67..83 && y in 40..96 -> -1
        x in 155..201 && y in 40..61 -> -1
        x in 155..207 && y in 95..112 -> -1
        x in 58..113 && y in 172..184 -> -1
        x in 305..337 && y in 172..184 -> -1
        else -> 0xff0d131f.toInt()
    }

    @Test fun recognizesScaledNightLayout() {
        for ((w, h) in listOf(1232 to 688, 960 to 536)) {
            assertTrue(BaiduVisibleCue.matchesNightGuidance(w, h) { x, y -> layout(x * 1232 / w, y * 688 / h) })
        }
    }

    @Test fun wideMapFollowsTheDialScaleInsteadOfStretchingThePanel() {
        for ((w, h, anchor) in listOf(Triple(1280, 526, 369), Triple(960, 394, 277))) {
            fun wide(x: Int, y: Int): Int {
                val dx = x * 449 / anchor
                val dy = y * 449 / anchor
                if (dx in 449..459 && dy in 30..100) return 0xff00c8ff.toInt()
                return layout(dx, dy)
            }
            assertTrue(BaiduVisibleCue.matchesNightGuidance(w, h, ::wide))
            assertFalse(BaiduVisibleCue.matchesNightGuidance(w, h) { x, y ->
                if (x * 449 / anchor in 40..135 && y * 449 / anchor in 20..127) 0 else wide(x, y)
            })
            assertFalse(BaiduVisibleCue.matchesNightGuidance(w, h) { x, y ->
                if (x * 449 / anchor in 420..550 && y * 449 / anchor in 0..115) 0 else wide(x, y)
            })
        }
    }

    @Test fun rejectsDayMapBlankFrameAndUnsupportedAspectRatio() {
        assertFalse(BaiduVisibleCue.matchesNightGuidance(1232, 688) { x, y -> if (y > 220) -1 else layout(x, y) })
        assertFalse(BaiduVisibleCue.matchesNightGuidance(1232, 688) { _, _ -> 0 })
        assertFalse(BaiduVisibleCue.matchesNightGuidance(688, 1232, ::layout))
    }

    @Test fun rejectsMissingArrowDistanceOrTripSummary() {
        for (region in listOf(42..130 to 20..127, 146..333 to 22..80, 286..405 to 163..191, 450..547 to 9..109)) {
            assertFalse(BaiduVisibleCue.matchesNightGuidance(1232, 688) { x, y ->
                if (x in region.first && y in region.second) 0 else layout(x, y)
            })
        }
    }

    // Geometric fixture for the arrival header and feedback buttons; deliberately excludes map details.
    private fun arrival(x: Int, y: Int): Int = when {
        x in 250..383 && y in 155..174 -> 0xffb8e6ff.toInt()
        x in 41..141 && y in 175..195 && x % 10 < 4 -> 0xff141d24.toInt()
        x in 147..249 && y in 175..195 && x % 12 < 7 -> 0xff387cff.toInt()
        x in 41..177 && y in 232..252 && x % 14 < 4 -> 0xff141d24.toInt()
        y in 230..249 && (x in 210..221 || x in 302..315 || x in 390..397) -> 0xffffcc70.toInt()
        else -> -1
    }

    @Test fun recognizesDayArrivalAndDoesNotOfferACue() {
        for ((w, h) in listOf(1232 to 688, 960 to 536)) {
            val presentation = { x: Int, y: Int -> arrival(x * 1232 / w, y * 688 / h) }
            assertTrue(BaiduVisibleCue.matchesDayArrival(w, h, presentation))
            assertFalse(BaiduVisibleCue.matchesNightGuidance(w, h, presentation))
        }
    }

    @Test fun doesNotReportArrivalForANightTurnOrAPageWithoutItsFeedbackRow() {
        assertFalse(BaiduVisibleCue.matchesDayArrival(1232, 688, ::layout))
        assertFalse(BaiduVisibleCue.matchesDayArrival(1232, 688) { x, y -> if (y > 222) -1 else arrival(x, y) })
        assertFalse(BaiduVisibleCue.matchesDayArrival(1232, 688) { x, y -> if (y < 220) -1 else arrival(x, y) })
    }
}
