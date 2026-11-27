package io.github.shaonialife.cardeck.input

import io.github.shaonialife.cardeck.input.TouchReports.Contact

/** Android receives one pointer. An idle HID slot cannot release another slot's pointer. */
internal class TouchContactOwner {
    private var last: Contact? = null
    private val ignoredFingers = mutableSetOf<Int>()
    val slot: Int? get() = last?.slot

    fun accept(report: Contact): Contact? {
        val previous = last
        if (report.slot in ignoredFingers) {
            if (!report.down) ignoredFingers.remove(report.slot)
            return null
        }
        if (previous != null && report.slot != previous.slot) {
            if (report.down) ignoredFingers.add(report.slot)
            return null
        }
        if (report.down) {
            last = report
            return report
        }
        if (previous == null) return null
        last = null
        // Some absolute touchscreens clear X/Y in the first report with Tip Switch off.
        return if (report.xPixels == 0 && report.yPixels == 0) previous.copy(down = false) else report
    }

    fun clear() {
        last = null
        ignoredFingers.clear()
    }
}
