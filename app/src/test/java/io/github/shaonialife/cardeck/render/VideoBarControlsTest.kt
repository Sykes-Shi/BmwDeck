package io.github.shaonialife.cardeck.render

import io.github.shaonialife.cardeck.render.VideoBarControls.Action
import io.github.shaonialife.cardeck.render.VideoBarControls.Axis
import org.junit.Assert.*
import org.junit.Test

class VideoBarControlsTest {
    private val wide = 1752f / 720f
    private fun frame(first: Int = 13, last: Int = 82, top: Int = 0, bottom: Int = 47, color: Int = 0x548cbe) =
        IntArray(96 * 48) { i -> if (i % 96 in first..last && i / 96 in top..bottom) color else 0 }

    @Test fun detectsActualSidesForWideAndFourByThreePicture() {
        for ((left, right) in listOf(13 to 82, 22 to 73)) {
            val bars = VideoBarControls.detect(frame(left, right), wide)!!
            assertEquals(Axis.SIDES, bars.axis)
            assertTrue(bars.firstEnd * 96 < left)
            assertTrue(bars.secondStart * 96 > right + 1)
            val buttons = VideoBarControls.buttons(bars, 168, 1920, 720, true)
            assertEquals(setOf(Action.BACK, Action.DESKTOP, Action.PAUSE), buttons.map { it.action }.toSet())
            for (button in buttons) {
                assertTrue(button.left >= 168 && button.right <= 1920)
                assertTrue(button.right <= 168 + bars.firstEnd * 1752 || button.left >= 168 + bars.secondStart * 1752)
            }
        }
    }

    @Test fun wideFilmAndPortraitDisplayGetHorizontalBarsWithinAppHeight() {
        for ((aspect, top) in listOf(16f / 9 to 7, 720f / 1080 to 15)) {
            val bars = VideoBarControls.detect(frame(0, 95, top, 47 - top), aspect)!!
            assertEquals(Axis.TOP_BOTTOM, bars.axis)
            val h = 1080
            val w = (aspect * h).toInt()
            val buttons = VideoBarControls.buttons(bars, 0, w, h, false)
            assertEquals(listOf(Action.BACK, Action.DESKTOP), buttons.map { it.action })
            for (b in buttons) {
                assertTrue(b.left >= 0 && b.right <= w)
                assertTrue(b.top >= 0 && b.bottom <= h)
                assertTrue(b.bottom <= h * bars.firstEnd || b.top >= h * bars.secondStart)
                assertTrue(b.contains((b.left + b.right) / 2, (b.top + b.bottom) / 2))
                assertFalse(b.contains(b.right, b.top))
            }
        }
    }

    @Test fun rejectsFullFrameBlackMenuThumbnailAsymmetryAndExistingBarContent() {
        assertNull(VideoBarControls.detect(frame(0, 95), wide))
        assertNull(VideoBarControls.detect(IntArray(96 * 48), wide))
        assertNull(VideoBarControls.detect(frame(top = 9, bottom = 39), wide))
        assertNull(VideoBarControls.detect(frame(25, 82), wide))
        val page = frame()
        page[3 + 4 * 96] = -1
        assertNull(VideoBarControls.detect(page, wide))
        val small = VideoBarControls.Bars(Axis.SIDES, .02f, .98f)
        assertTrue(VideoBarControls.buttons(small, 168, 1920, 720, true).isEmpty())
        val horizontal = VideoBarControls.Bars(Axis.TOP_BOTTOM, .06f, .94f)
        assertTrue(VideoBarControls.buttons(horizontal, 168, 1920, 720, true).isEmpty())
    }

    @Test fun requiresSustainedMotionAndDoesNotContinueOnPauseOrStaticFrames() {
        val tracker = VideoBarControls.Motion()
        val a = frame()
        val b = frame(color = 0x903857)
        val bars = VideoBarControls.detect(a, wide)!!
        fun seen(f: IntArray, time: Long, playing: Boolean? = null) = tracker.playing(f, bars, time, playing)
        assertFalse(seen(a, 0, true))
        assertFalse(seen(a, 450, true))
        assertFalse(seen(b, 900, true))
        assertTrue(seen(a, 1350, true))
        assertFalse(seen(b, 1500, false))
        assertFalse(seen(a, 1950))
        assertFalse(seen(b, 2400))
        assertTrue(seen(a, 2850))
        assertFalse(seen(a, 4300))
        tracker.reset()
        assertFalse(seen(b, 4750))
    }

    @Test fun consumesOnlyGesturesStartingOnTheShownButtonAndCancelsStaleTargets() {
        val target = VideoBarControls.Target(7, 1, 4, false, "com.youku.phone/.Player", 43)
        val button = VideoBarControls.Button(Action.BACK, 10f, 10f, 50f, 50f)
        val hit = VideoBarControls.Hit(target, button)
        val touch = VideoBarControls.Gesture()
        val tapped = mutableListOf<VideoBarControls.Hit>()
        fun event(down: Boolean, at: VideoBarControls.Hit?, app: Boolean = false, slot: Int = 0) =
            touch.handle(slot, down, at, app) { tapped.add(it) }
        assertFalse(event(true, null))
        assertFalse(event(true, hit, app = true))
        assertTrue(event(true, hit))
        assertTrue(event(true, null))
        assertTrue(event(false, hit))
        assertTrue(tapped.isEmpty())
        assertTrue(event(true, hit))
        assertTrue(event(false, hit.copy(target = target.copy(revision = 2))))
        assertTrue(tapped.isEmpty())
        assertTrue(event(true, hit))
        assertTrue(event(true, hit, slot = 1))
        assertTrue(event(false, hit))
        assertTrue(tapped.isEmpty())
        assertTrue(event(true, hit))
        assertTrue(event(false, hit))
        assertEquals(listOf(hit), tapped)
    }
}
