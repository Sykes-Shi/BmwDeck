package io.github.shaonialife.cardeck.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionAudioSettingsTest {
    @Test fun firstInstallAndUpgradesAskBeforeChoosing() {
        assertEquals(ConnectionAudioSettings(), ConnectionAudioSettings.decode(null, false))
        assertEquals(ConnectionAudioSettings(), ConnectionAudioSettings.decode("unknown", true))
    }

    @Test fun forgettingOrMutingAlsoForgetsTheWholeScreenRequest() {
        assertEquals(ConnectionAudioSettings(), ConnectionAudioSettings.decode("ask", true))
        assertEquals(ConnectionAudioSettings(ConnectionSound.SILENT), ConnectionAudioSettings.decode("silent", true))
    }

    @Test fun rememberingSoundDoesNotAutomaticallySelectWholeScreen() {
        assertEquals(ConnectionAudioSettings(ConnectionSound.PLAY), ConnectionAudioSettings.decode("play", false))
        assertEquals(ConnectionAudioSettings(ConnectionSound.PLAY, true), ConnectionAudioSettings.decode("play", true))
    }
}
