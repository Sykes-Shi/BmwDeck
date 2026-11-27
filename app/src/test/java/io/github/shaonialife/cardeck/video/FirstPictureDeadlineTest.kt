package io.github.shaonialife.cardeck.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPictureDeadlineTest {
    @Test fun hotspotAndEncoderPreparationDoNotConsumeTheFirstPictureBudget() {
        assertFalse(VideoSender.firstPictureOverdue(30_000L, 4_000L, 39_999L))
        assertTrue(VideoSender.firstPictureOverdue(30_000L, 4_000L, 40_000L))
    }

    @Test fun aNewScreenGrantAfterNativeActivityGetsAFullWindow() {
        assertFalse(VideoSender.firstPictureOverdue(0, null, 45_000L))
        assertFalse(VideoSender.firstPictureOverdue(0, 45_000L, 54_999L))
        assertTrue(VideoSender.firstPictureOverdue(0, 45_000L, 55_000L))
    }
}
