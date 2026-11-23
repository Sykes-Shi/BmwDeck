package io.github.shaonialife.cardeck.input

/**
 * Tracks whether compact HID input can be sent to our display. This observes ownership
 * messages only; the existing AirPlay mode declarations and recovery policy stay separate.
 */
internal class CompactInputScreen {
    private val loans = mutableSetOf<String>()
    private var uncertain = false
    private var nativeOwner = false
    private var accepted = false
    private var epoch = 0L
    @Volatile var revision: Long? = null
        private set

    @Synchronized
    fun initialModes(accepted: Boolean) {
        this.accepted = accepted
        publish()
    }

    @Synchronized
    fun observe(params: Map<*, *>?) {
        val resources = params?.get("resources") as? List<*> ?: return
        val screen = resources.mapNotNull { it as? Map<*, *> }
            .filter { (it["resourceID"] as? Number)?.toLong() == 1L }
        if (screen.isEmpty()) return
        if (screen.size > 1) {
            uncertain = true
            publish()
            return
        }
        val row = screen.single()
        val id = (row["borrowID"] as? String)?.takeIf { it.length in 1..128 }
        when ((row["transferType"] as? Number)?.toLong()) {
            1L -> nativeOwner = true
            2L -> nativeOwner = false
            3L -> if (id == null || (loans.size >= 8 && id !in loans)) uncertain = true else loans.add(id)
            4L -> if (id != null) loans.remove(id)
            else -> uncertain = true
        }
        publish()
    }

    private fun publish() {
        epoch++
        revision = epoch.takeIf { accepted && !uncertain && !nativeOwner && loans.isEmpty() }
    }
}
