package io.github.shaonialife.cardeck.transport

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2LinkChannelPrivacyTest {
    @Test
    fun incomingTrafficLogsLengthWithoutPayload() {
        val pair = InMemoryStreamPair()
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val received = CountDownLatch(1)
        val channel = Iap2LinkChannel(pair.sideA) { line ->
            lines.add(line)
            if (line.startsWith("LINK raw rx:")) received.countDown()
        }
        try {
            val sensitive = byteArrayOf(0x51, 0x72, 0x36, 0x79, 0x50, 0x53, 0x4b, 0x21)
            pair.sideB.write(sensitive)
            assertTrue("missing receive summary", received.await(3, TimeUnit.SECONDS))
            val snapshot = synchronized(lines) { lines.toList() }
            assertTrue(snapshot.any { it == "LINK raw rx: bytes=8" })
            assertFalse(snapshot.any { it.contains("51 72 36 79 50 53 4b 21", ignoreCase = true) })
            assertFalse(snapshot.any { it.contains("Qr6yPSK!") })
        } finally {
            channel.close()
            pair.sideB.close()
        }
    }
}
