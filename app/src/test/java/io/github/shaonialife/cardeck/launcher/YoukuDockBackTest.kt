package io.github.shaonialife.cardeck.launcher

import io.github.shaonialife.cardeck.render.VideoBarControls
import org.junit.Assert.assertEquals
import org.junit.Test

class YoukuDockBackTest {
    private val target = VideoBarControls.Target(1, 7, 24, true, "com.youku.phone/.Player", 12)

    @Test fun oneDisplayScopedKeyEvenWhenDispatchRejects() {
        for (accepted in listOf(true, false)) {
            val displays = mutableListOf<Int>()
            val result = YoukuDockBack.send(target, { true }, { target }) { display ->
                displays.add(display)
                accepted
            }
            assertEquals(listOf(24), displays)
            assertEquals(if (accepted) YoukuDockBack.Result.DELIVERED else YoukuDockBack.Result.REJECTED, result)
        }
    }

    @Test fun differentTaskDisplaySceneOrForegroundNeverReceivesBack() {
        for (foreground in listOf(null, target.copy(task = 13), target.copy(display = 0), target.copy(revision = 8),
                target.copy(component = "another/.Main"), target.copy(component = "com.youku.phone/.Other"))) {
            assertEquals(YoukuDockBack.Result.STALE,
                YoukuDockBack.send(target, { true }, { foreground }) { error("Unexpected dispatch") })
        }
        var valid = true
        assertEquals(YoukuDockBack.Result.STALE,
            YoukuDockBack.send(target, { valid }, { valid = false; target }) { error("Changed while checking") })
    }

    @Test fun cheyuBackUsesOnlyTheStillVisibleCarTask() {
        val cheyu = target.copy(component = "com.bytedance.byteautoservice3/com.bytedance.auto.lite.apps.framework.ui.MainActivity")
        assertEquals(YoukuDockBack.Result.DELIVERED, YoukuDockBack.send(cheyu, { true }, { cheyu }) { it == 24 })
        assertEquals(YoukuDockBack.Result.STALE, YoukuDockBack.send(cheyu, { true }, { target }) { error("Wrong player") })
    }

    @Test fun refusesPhysicalPhoneAndOtherApplicationsEvenIfStillForeground() {
        for (wrong in listOf(target.copy(display = 0), target.copy(component = "com.ukids.client.phone/.Home"))) {
            assertEquals(YoukuDockBack.Result.STALE, YoukuDockBack.send(wrong, { true },
                { error("No foreground check expected") }) { error("Unexpected dispatch") })
        }
    }
}
