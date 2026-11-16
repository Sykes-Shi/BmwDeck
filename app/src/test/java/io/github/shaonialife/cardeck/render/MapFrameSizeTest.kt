package io.github.shaonialife.cardeck.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MapFrameSizeTest {
    @Test fun keepsSplitMapAtItsNativeSize() {
        val tile = MapFrameSize(431, 492)
        assertSame(tile, tile.exportSize())
    }

    @Test fun scalesWideAndTallFramesProportionallyWithoutCropping() {
        assertEquals(MapFrameSize(1280, 480), MapFrameSize(1920, 720).exportSize())
        assertEquals(MapFrameSize(480, 1280), MapFrameSize(720, 1920).exportSize())
        assertEquals(MapFrameSize(1280, 479), MapFrameSize(1923, 720).exportSize())
    }

    @Test fun thinImagesRetainAtLeastOnePixelOnTheirShortSide() {
        assertEquals(MapFrameSize(1, 1280), MapFrameSize(1, 3200).exportSize())
    }
}
