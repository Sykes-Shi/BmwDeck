package io.github.shaonialife.cardeck.input

import io.github.shaonialife.cardeck.input.TouchReports.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchContactOwnerTest {
    @Test fun zeroedTipReleaseUsesLastOwnerPositionOnce() {
        val owner = TouchContactOwner()
        val down = TouchReports.parseSingleTouch(byteArrayOf(1, 4, 3, 42, 0)).single()
        val released = TouchReports.parseSingleTouch(byteArrayOf(0, 0, 0, 0, 0)).single()
        owner.accept(down)
        val move = down.copy(xPixels = down.xPixels + 7)
        owner.accept(move)
        assertNull(owner.accept(Contact(1, 42, 18, false)))
        assertEquals(move.copy(down = false), owner.accept(released))
        assertNull(owner.accept(released))
    }

    @Test fun secondFingerCannotStartAReplacementClickWhileStillHeld() {
        val owner = TouchContactOwner()
        val first = Contact(0, 772, 42, true)
        val second = Contact(1, 50, 40, true)
        owner.accept(first)
        assertNull(owner.accept(second))
        assertEquals(first.copy(down = false), owner.accept(first.copy(down = false)))
        assertNull(owner.accept(second))
        assertNull(owner.accept(second.copy(down = false)))
        assertEquals(second, owner.accept(second))
    }

    @Test fun normalReleaseKeepsActualCoordinatesAndResetDropsOrphanRelease() {
        val owner = TouchContactOwner()
        val down = Contact(1, 772, 42, true)
        val release = down.copy(xPixels = 790, yPixels = 55, down = false)
        owner.accept(down)
        assertEquals(release, owner.accept(release))
        owner.accept(down)
        owner.clear()
        assertNull(owner.accept(release))
    }
}
