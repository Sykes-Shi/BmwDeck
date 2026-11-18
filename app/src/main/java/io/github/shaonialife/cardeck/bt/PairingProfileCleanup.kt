package io.github.shaonialife.cardeck.bt

/** Run the pairing cleanup only while the current policy and target still allow it. */
internal object PairingProfileCleanup {
    fun run(allowed: () -> Boolean, disconnect: () -> Unit, pause: () -> Boolean) {
        if (!allowed()) return
        disconnect()
        if (!allowed() || !pause() || !allowed()) return
        disconnect()
    }
}
