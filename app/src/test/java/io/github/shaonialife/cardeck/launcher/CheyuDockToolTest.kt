package io.github.shaonialife.cardeck.launcher

import io.github.shaonialife.cardeck.input.CarControllerAction
import io.github.shaonialife.cardeck.render.VideoBarControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheyuDockToolTest {
    @Test fun nextSwipesUpAndPreviousStartsBelowTheTabsInBothOrientations() {
        for ((w, h) in listOf(1600 to 660, 660 to 1600, 721 to 369)) {
            val next = requireNotNull(CheyuDockTool.NEXT.stroke(w, h))
            assertTrue(next.toY in 0 until next.fromY && next.fromY < h)
            assertEquals(w / 2, next.x)
            val previous = requireNotNull(CheyuDockTool.PREVIOUS.stroke(w, h))
            assertEquals(next.x, previous.x)
            assertTrue(previous.fromY in (h / 3)..(h / 2))
            assertTrue(previous.toY in (h * 9 / 10)..(h - 1))
        }
        assertEquals(CheyuDockTool.Stroke(915, 280, 650), CheyuDockTool.PREVIOUS.stroke(1830, 720))
        assertEquals(CheyuDockTool.Stroke(915, 539, 180), CheyuDockTool.NEXT.stroke(1830, 720))
        assertNull(CheyuDockTool.POINTER.stroke(1600, 660))
        assertNull(CheyuDockTool.NEXT.stroke(0, 660))
    }

    @Test fun stickStaysInTheAppPaneAndTheWheelDoesNotDisplaceThePointer() {
        val target = VideoBarControls.Target(1, 5, 31, false, "com.bytedance.byteautoservice3/.Main", 2)
        val point = CarAppPoint(target, 2, 2)
        assertEquals(0, point.move(CarControllerAction.LEFT, 8, 1600, 660).x)
        assertEquals(659, point.copy(y = 655).move(CarControllerAction.DOWN, 8, 1600, 660).y)
        assertEquals(point, point.move(CarControllerAction.NEXT, 1, 1600, 660))
    }
}
