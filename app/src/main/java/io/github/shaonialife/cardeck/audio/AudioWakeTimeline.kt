package io.github.shaonialife.cardeck.audio

/** The BMW playhead keeps ticking while the phone wakes. Allow queued AAC to catch up
 * before accounting for samples that were actually lost during the interruption. */
internal class AudioWakeTimeline(private val sampleRate: Int) {
    private var wakeNs = -1L
    private var wakeEpoch: Long? = null
    private var lastPacketNs = -1L
    private var lastTimestamp = 0L
    private var lastEpoch: Long? = null
    private var beforeGapNs = -1L
    private var beforeGapTimestamp = 0L
    private var gapEndNs = -1L

    @Synchronized fun woke(nowNs: Long, epoch: Long?) {
        wakeNs = nowNs
        wakeEpoch = epoch
    }

    /** Called by the single RTP writer just before sealing a real packet. A different
     * grant has its own resume policy and must never inherit the duration of a call. */
    @Synchronized fun missingSamples(nowNs: Long, timestamp: Long, epoch: Long): Long {
        if (epoch != lastEpoch || nowNs < lastPacketNs) {
            beforeGapNs = -1L
        } else if (lastPacketNs >= 0 && nowNs - lastPacketNs >= GAP_NS) {
            if (beforeGapNs < 0 || nowNs - gapEndNs > WAKE_WINDOW_NS) {
                beforeGapNs = lastPacketNs
                beforeGapTimestamp = lastTimestamp
            }
            gapEndNs = nowNs
        }
        lastPacketNs = nowNs
        lastEpoch = epoch
        lastTimestamp = timestamp
        if (beforeGapNs < 0) return 0
        val sinceGap = nowNs - gapEndNs
        if (sinceGap > WAKE_WINDOW_NS) {
            beforeGapNs = -1L
            return 0
        }
        if (sinceGap < SETTLE_NS || wakeEpoch != epoch || wakeNs < 0 ||
            wakeNs !in (beforeGapNs - WAKE_WINDOW_NS)..(gapEndNs + WAKE_WINDOW_NS) ||
            nowNs - wakeNs > WAKE_WINDOW_NS
        ) return 0

        val expectedSamples = (nowNs - beforeGapNs) / 1_000_000 * sampleRate / 1_000
        val missing = expectedSamples - (timestamp - beforeGapTimestamp)
        beforeGapNs = -1L
        wakeEpoch = null
        if (missing < sampleRate / 4) return 0
        lastTimestamp += missing
        return missing
    }

    private companion object {
        const val GAP_NS = 600_000_000L
        const val SETTLE_NS = 500_000_000L
        const val WAKE_WINDOW_NS = 8_000_000_000L
    }
}
