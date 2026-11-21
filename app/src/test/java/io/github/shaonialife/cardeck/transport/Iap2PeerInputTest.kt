package io.github.shaonialife.cardeck.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Iap2PeerInputTest {
    @Test
    fun localHandshakeTrafficDoesNotCountAsPeerResponse() {
        val pair = InMemoryStreamPair()
        pair.sideB.use { peer ->
            Iap2LinkChannel(pair.sideA).use { channel ->
                assertFalse(channel.awaitPeerInput(50L))
                peer.write(byteArrayOf(1))
                assertTrue(channel.awaitPeerInput(2_000L))
            }
        }
    }

    @Test
    fun closingTheChannelWakesTheWaitingConnection() {
        val pair = InMemoryStreamPair()
        pair.sideB.use {
            val channel = Iap2LinkChannel(pair.sideA)
            val result = FutureTask { channel.awaitPeerInput(30_000L) }
            thread(isDaemon = true) { result.run() }
            channel.close()
            assertFalse(result.get(2, TimeUnit.SECONDS))
        }
    }
}
