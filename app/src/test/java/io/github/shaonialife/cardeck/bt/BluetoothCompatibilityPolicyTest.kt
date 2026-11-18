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

    @Test
    fun savedPreferenceOverridesVariantDefault() {
        assertTrue(BluetoothCompatibilityPrefs.effectiveValue(null, true))
        assertFalse(BluetoothCompatibilityPrefs.effectiveValue(null, false))
        assertFalse(BluetoothCompatibilityPrefs.effectiveValue(false, true))
        assertTrue(BluetoothCompatibilityPrefs.effectiveValue(true, false))
    }

    @Test
    fun postBondDisconnectRequiresOriginalTargetAndLiveBond() {
        val ordinary = BluetoothCompatibilityPolicy(keepSystemBluetooth = false)
        val preserve = BluetoothCompatibilityPolicy(keepSystemBluetooth = true)
        assertTrue(ordinary.allowsPostBondDisconnect(targetMatches = true, isBonded = true, interrupted = false))
        assertFalse(preserve.allowsPostBondDisconnect(targetMatches = true, isBonded = true, interrupted = false))
        assertFalse(ordinary.allowsPostBondDisconnect(targetMatches = false, isBonded = true, interrupted = false))
        assertFalse(ordinary.allowsPostBondDisconnect(targetMatches = true, isBonded = false, interrupted = false))
        assertFalse(ordinary.allowsPostBondDisconnect(targetMatches = true, isBonded = true, interrupted = true))
    }

    @Test
    fun runningSessionAndCurrentPreferenceBothProtectBluetooth() {
        val currentOrdinary = BluetoothCompatibilityPolicy(keepSystemBluetooth = false)
        val currentPreserve = BluetoothCompatibilityPolicy(keepSystemBluetooth = true)
        fun allowed(policy: BluetoothCompatibilityPolicy, active: Boolean?) =
            policy.allowsPostBondDisconnect(true, true, false, active)
        assertTrue(allowed(currentOrdinary, null))
        assertTrue(allowed(currentOrdinary, false))
        assertFalse(allowed(currentOrdinary, true))
        assertFalse(allowed(currentPreserve, null))
        assertFalse(allowed(currentPreserve, false))
        assertFalse(allowed(currentPreserve, true))
    }
}
