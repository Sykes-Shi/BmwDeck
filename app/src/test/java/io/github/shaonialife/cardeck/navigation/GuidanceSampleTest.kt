package io.github.shaonialife.cardeck.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceSampleTest {
    @Test fun extractsOnlyDistanceNextToManeuver() {
        assertEquals(GuidanceSample.Hint("右转", 250), GuidanceSample.parse(listOf("剩余 18 公里，前方250米后右转")))
        assertEquals(GuidanceSample.Hint("左转", 1200), GuidanceSample.parse(listOf("1.2公里后左转")))
    }

    @Test fun doesNotConfuseTripDistanceWithManeuverDistance() {
        assertEquals(GuidanceSample.Hint("左转", null), GuidanceSample.parse(listOf("剩余 10 公里", "左转")))
        assertEquals(GuidanceSample.Hint("右转", null), GuidanceSample.parse(listOf("剩余 10 公里，右转")))
    }

    @Test fun readsCommonBaiduStyleInstructionsWithoutExportingRoadNames() {
        assertEquals(GuidanceSample.Hint("右转", 617), GuidanceSample.parse(listOf("前方617米后，请右转进入示例路")))
        assertEquals(GuidanceSample.Hint("左转", null), GuidanceSample.parse(listOf("下个路口左转驶入示例路")))
        assertEquals(GuidanceSample.Hint("左转", 120), GuidanceSample.parse(listOf("左转", "120米后左转")))
        assertEquals(GuidanceSample.Hint("左转", null), GuidanceSample.parse(listOf("左转", "120米后右转")))
    }

    @Test fun ignoresRestrictionsAndGenericOrLaneMessages() {
        for (text in listOf("禁止左转", "请勿调头", "禁止在下个路口右转", "导航运行中", "进入左转车道", "右转专用车道", "导航已结束")) {
            assertNull(GuidanceSample.parse(listOf(text)))
        }
    }

    @Test fun enteringRoadRequiresAdjacentDistanceAndDoesNotInferTurnOrRetainRoadName() {
        assertEquals(GuidanceSample.Hint("驶入道路", 300), GuidanceSample.parse(listOf("300米后进入示例路")))
        assertEquals(GuidanceSample.Hint("驶入道路", 300), GuidanceSample.parse(listOf("前方300米后驶入匝道")))
        assertNull(GuidanceSample.parse(listOf("进入示例路")))
        assertNull(GuidanceSample.parse(listOf("300米后进入休息区")))
        assertNull(GuidanceSample.parse(listOf("剩余 300 米，进入示例路")))
    }

    private fun notice(vararg texts: String, navigation: Boolean = false, ongoing: Boolean = false, summary: Boolean = false) =
        GuidanceSample.Notice(navigation, ongoing, summary, texts.toList())

    @Test fun explainsEmptySnapshotsAndDoesNotExportGroupText() {
        assertEquals("no_baidu_notifications", GuidanceSample.inspect(emptyList()).state)
        assertEquals("group_summaries_only", GuidanceSample.inspect(listOf(notice("200米后右转", summary = true))).state)
        assertEquals("no_standard_text", GuidanceSample.inspect(listOf(notice(navigation = true))).state)
        assertEquals("no_matching_guidance", GuidanceSample.inspect(listOf(notice("有新消息"))).state)
        assertEquals("guidance_without_recognized_instruction", GuidanceSample.inspect(listOf(notice("正在导航", ongoing = true))).state)
    }

    @Test fun readsUntaggedGuidanceWithEvidenceButIgnoresGenericWordsAndCompletedTrips() {
        assertNull(GuidanceSample.inspect(listOf(notice("右转"))).match)
        assertNull(GuidanceSample.inspect(listOf(notice("导航已结束", "120米后左转", navigation = true))).match)
        val plain = GuidanceSample.inspect(listOf(notice("300米后进入示例路"))).match
        assertEquals(GuidanceSample.Evidence.ROUTE_TEXT_ONLY, plain?.evidence)
        assertEquals(GuidanceSample.Hint("驶入道路", 300), plain?.hint)
        val ongoing = GuidanceSample.inspect(listOf(notice("正在导航", "左转", ongoing = true))).match
        assertEquals(GuidanceSample.Evidence.ONGOING_ROUTE_TEXT, ongoing?.evidence)
    }

    @Test fun prioritizesCategoryAndDoesNotMixDifferentNotificationFields() {
        val scan = GuidanceSample.inspect(listOf(notice("200米后左转"), notice("右转", navigation = true)))
        assertEquals(GuidanceSample.Evidence.NAVIGATION_CATEGORY, scan.match?.evidence)
        assertEquals(GuidanceSample.Hint("右转", null), scan.match?.hint)
        assertEquals(2, scan.candidates)
    }
}
