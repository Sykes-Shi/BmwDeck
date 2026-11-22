package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmapDestinationTest {
    @Test
    fun keywordKeepsItsPlaceWithinTheOfficialAmapSearchUri() {
        assertEquals("androidamap://poi?sourceApplication=CarDeck&keywords=%E4%B8%8A%E6%B5%B7%20A%26B&dev=0",
            amapKeywordSearchUri(" 上海 A&B "))
        assertNull(amapKeywordSearchUri("   "))
    }
}
