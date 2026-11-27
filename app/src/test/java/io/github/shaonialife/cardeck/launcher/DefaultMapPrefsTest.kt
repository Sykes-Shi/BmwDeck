package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMapPrefsTest {
    @Test fun legacyHandsetAndThirdPartyMapComponentsCannotOpenOnTheCar() {
        for (pkg in listOf("com.baidu.BaiduMap", "com.autonavi.amapauto", "com.autonavi.minimap", "com.tencent.map")) {
            assertTrue(BaiduCruise.isExcludedCarMap(pkg))
            assertTrue(BaiduCruise.isExcludedCarMap("$pkg/.Main"))
        }
        assertTrue(BaiduCruise.isExcludedCarMap("test.map", android.content.pm.ApplicationInfo.CATEGORY_MAPS))
        assertFalse(BaiduCruise.isExcludedCarMap(BaiduCruise.PACKAGE, android.content.pm.ApplicationInfo.CATEGORY_MAPS))
        assertFalse(BaiduCruise.isExcludedCarMap("com.netease.cloudmusic"))
    }
}
