package io.github.shaonialife.cardeck.render

import org.junit.Assert.*
import org.junit.Test

class KidsVideoBackdropTest {
    private val aspect = 1752f / 720f
    private val picture = 0xff548cbe.toInt()
    private fun frame(first: Int = 13, last: Int = 82, top: Int = 0, bottom: Int = 47) =
        IntArray(96 * 48) { i -> if (i % 96 in first..last && i / 96 in top..bottom) picture else 0xff000000.toInt() }

    @Test fun detectsFullHeightVideoAndLeavesTheOriginalPictureUntouched() {
        val bands = KidsVideoBackdrop.detect(frame(), aspect)!!
        assertTrue(bands.left * 96 < 13)
        assertTrue(bands.right * 96 > 83)
        assertEquals(picture and 0xffffff, bands.leftColor)
        assertEquals(picture and 0xffffff, bands.rightColor)
    }

    @Test fun rejectsFullWidthScreensMenusWithSideControlsAndEmbeddedThumbnails() {
        assertNull(KidsVideoBackdrop.detect(frame(0, 95), aspect))
        assertNull(KidsVideoBackdrop.detect(frame(top = 9, bottom = 39), aspect))
        assertNull(KidsVideoBackdrop.detect(frame(25, 82), aspect))
        val controls = frame()
        for (y in 12..18) for (x in 3..7) controls[y * 96 + x] = -1
        assertNull(KidsVideoBackdrop.detect(controls, aspect))
    }

    @Test fun aStaticOrPausedSurfaceDoesNotKeepTheMotionGateOpen() {
        val tracker = KidsVideoBackdrop.Motion()
        val image = frame()
        assertFalse(tracker.isMoving(image, 0))
        assertFalse(tracker.isMoving(image, 700))
        val changed = IntArray(image.size) { image[it] xor 0x404040 }
        assertTrue(tracker.isMoving(changed, 1400))
        assertTrue(tracker.isMoving(changed, 2100))
        assertFalse(tracker.isMoving(changed, 3500))
        tracker.reset()
        assertFalse(tracker.isMoving(image, 4200))
    }
}
