package io.github.shaonialife.cardeck.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationSessionPolicyTest {
    private val map = "com.baidu.naviauto"
    private val video = "com.youku.phone"

    @Test fun excludedMapOverridesNeverRestartHandsetApps() {
        val excluded = setOf("com.baidu.BaiduMap", "com.autonavi.minimap", "com.autonavi.amapauto", "com.tencent.map")
        val policy = OrientationSessionPolicy()
        for (token in listOf(policy.token(), policy.begin(), policy.end())) {
            for (pkg in excluded) {
                assertFalse(policy.permits(token, pkg))
                assertFalse(policy.permits(token, "$pkg/.Main"))
            }
        }
        val candidates = policy.resetCandidates(policy.token(), excluded + video, legacyEnabled = excluded + map, recoverLegacy = true)
        assertEquals(setOf(map, video), candidates)
    }

    @Test
    fun disconnectInvalidatesQueuedApplyAndDelayedProbe() {
        val policy = OrientationSessionPolicy()
        val session = policy.begin()
        val cleanup = policy.end()
        assertFalse(policy.permits(session, video))
        assertFalse(policy.isActive(session))
        assertNull(policy.sessionToken())
        assertTrue(policy.permits(cleanup, video))
    }

    @Test
    fun repeatedDetachDoesNotInvalidateAlreadyQueuedCleanup() {
        val policy = OrientationSessionPolicy()
        policy.begin()
        val cleanup = policy.end()
        assertEquals(cleanup, policy.end())
        assertEquals(setOf(video), policy.resetCandidates(cleanup, setOf(video)))
    }

    @Test
    fun oldCleanupCannotDisableNewSessionOverrides() {
        val policy = OrientationSessionPolicy()
        policy.begin()
        val cleanup = policy.end()
        val current = policy.begin()
        assertTrue(policy.permits(current, video))
        assertFalse(policy.permits(cleanup, video))
        assertTrue(policy.resetCandidates(cleanup, setOf(map, video)).isEmpty())
    }

    @Test
    fun secondCompatCommandIsRejectedAfterGenerationChanges() {
        val policy = OrientationSessionPolicy()
        val old = policy.begin()
        assertTrue(policy.permits(old, map))
        policy.begin()
        assertFalse(policy.permits(old, map))
        assertFalse(policy.isActive(old))
    }

    @Test
    fun cleanupUsesAttemptedPackagesNotPreferenceMembership() {
        val policy = OrientationSessionPolicy()
        policy.begin()
        val cleanup = policy.end()
        assertEquals(setOf(video), policy.resetCandidates(cleanup, setOf(video)))
        assertFalse(map in policy.resetCandidates(cleanup, setOf(video)))
        assertTrue(policy.resetCandidates(cleanup, emptySet()).isEmpty())
    }

    @Test
    fun staleIdlePreferenceWriteCannotAffectNewConnection() {
        val policy = OrientationSessionPolicy()
        val settingsAction = policy.token()
        policy.begin()
        assertFalse(policy.permits(settingsAction, video))
        assertFalse(policy.isCurrent(settingsAction))
    }

    @Test
    fun resetReturnsIndependentBoundedSelection() {
        val policy = OrientationSessionPolicy()
        val cleanup = policy.end()
        val persisted = linkedSetOf(video)
        val candidates = policy.resetCandidates(cleanup, persisted)
        persisted.add(map)
        assertEquals(setOf(video), candidates)
    }

    @Test
    fun legacyPreferenceRecoveryRequiresExplicitResidualScreenPath() {
        val policy = OrientationSessionPolicy()
        val cleanup = policy.end()
        assertTrue(policy.resetCandidates(cleanup, emptySet(), legacyEnabled = setOf(video)).isEmpty())
        assertEquals(
            setOf(video),
            policy.resetCandidates(cleanup, emptySet(), legacyEnabled = setOf(video), recoverLegacy = true),
        )
    }

    @Test
    fun legacyRecoveryCannotEndAnActiveOrientationSession() {
        val policy = OrientationSessionPolicy()
        val session = policy.begin()
        assertNull(policy.idleRecoveryToken())
        assertTrue(policy.isActive(session))
        assertTrue(policy.permits(session, map))
    }

    @Test
    fun idleRecoveryTokenBecomesInvalidWhenAConnectionStarts() {
        val policy = OrientationSessionPolicy()
        val recovery = policy.idleRecoveryToken()!!
        assertTrue(policy.permits(recovery, video))
        policy.begin()
        assertFalse(policy.permits(recovery, video))
        assertTrue(policy.resetCandidates(recovery, setOf(video), recoverLegacy = true).isEmpty())
    }
}
