package io.github.shaonialife.cardeck.input

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompactInputScreenTest {
    private fun mode(resource: Int, type: Int, borrowId: String? = null) =
        mapOf("resources" to listOf(mapOf("resourceID" to resource, "transferType" to type, "borrowID" to borrowId)))

    @Test
    fun queuedInputExpiresAcrossNativeScreenChangesAndSeparateAudioDoesNotSuspendIt() {
        val screen = CompactInputScreen()
        assertNull(screen.revision)
        screen.initialModes(true)
        val initial = screen.revision
        assertNotNull(initial)
        screen.observe(mode(2, 3, "call"))
        org.junit.Assert.assertEquals(initial, screen.revision)
        screen.observe(mode(1, 3, "first"))
        assertNull(screen.revision)
        screen.observe(mode(1, 4, "different"))
        assertNull(screen.revision)
        screen.observe(mode(1, 4, "first"))
        assertNotNull(screen.revision)
        assertNotEquals(initial, screen.revision)
        screen.observe(mode(1, 1))
        assertNull(screen.revision)
        screen.observe(mode(1, 2))
        assertNotNull(screen.revision)
    }

    @Test
    fun anEarlyBorrowIsNotOverriddenByTheStartupAcknowledgement() {
        val screen = CompactInputScreen()
        screen.observe(mode(1, 3, "first"))
        screen.initialModes(true)
        assertNull(screen.revision)
        screen.observe(mode(1, 4, "first"))
        assertNotNull(screen.revision)
    }
}
