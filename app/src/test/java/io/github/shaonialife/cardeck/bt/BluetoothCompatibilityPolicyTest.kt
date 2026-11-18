package io.github.shaonialife.cardeck.bt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothCompatibilityPolicyTest {
    @Test
    fun defaultKeepsPreviousProfileDisconnectBehavior() {
        val policy = BluetoothCompatibilityPolicy()
        assertFalse(policy.keepSystemBluetooth)
        assertTrue(policy.disconnectProfiles)
        assertFalse(policy.probeSelectedDevice)
    }

    @Test
    fun explicitCompatibilityModePreservesProfilesAndProbesTarget() {
        val policy = BluetoothCompatibilityPolicy(keepSystemBluetooth = true)
        assertTrue(policy.keepSystemBluetooth)
        assertFalse(policy.disconnectProfiles)
        assertTrue(policy.probeSelectedDevice)
    }
}
