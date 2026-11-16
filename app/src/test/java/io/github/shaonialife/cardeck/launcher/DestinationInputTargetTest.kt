package io.github.shaonialife.cardeck.launcher

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DestinationInputTargetTest {
    @Test fun laterSessionsPagesPhoneNavigationChangesAndScreenLoansInvalidatePhoneEntry() {
        val search = DestinationInputTarget(118, 8, 1, 5, 0)
        assertTrue(search.matches(118, 8, 1, 5, 0))
        assertFalse(search.matches(0, 8, 1, 5, 0))
        assertFalse(search.matches(118, 9, 1, 5, 0))
        assertFalse(search.matches(118, 8, 2, 5, 0))
        assertFalse(search.matches(118, 8, 1, 6, 0))
        assertFalse(search.matches(118, 8, 1, 5, null))
    }

    @Test fun keywordIsOneEncodedParameterIncludingChineseAndShellPunctuation() {
        val query = "人民广场 A+B & 1#2's"
        val uri = URI(baiduKeywordSearchUri("  $query  ")!!)
        assertEquals("baidumap", uri.scheme)
        assertEquals("map", uri.host)
        assertEquals("/place/search", uri.path)
        val params = uri.rawQuery.split('&')
        assertEquals(2, params.size)
        assertEquals(query, URLDecoder.decode(params[0].removePrefix("query="), "UTF-8"))
        assertEquals("src=andr.shaonialife.cardeck", params[1])
        assertNull(baiduKeywordSearchUri("  "))
        assertNull(baiduKeywordSearchUri("字".repeat(129)))
    }

    @Test fun diagnosticWorkIsBoundedAndNotRepeatedForEveryKey() {
        val target = DestinationInputTarget(118, 8, 1, 5, 0)
        assertTrue(target.claimKeyboardProbe(0))
        assertFalse(target.claimKeyboardProbe(500))
        assertTrue(target.claimKeyboardProbe(3500))
        assertTrue(target.claimKeyboardProbe(7000))
        assertFalse(target.claimKeyboardProbe(12000))
    }
}
