package io.github.shaonialife.cardeck.launcher

import io.github.shaonialife.cardeck.media.CarVideoSources
import io.github.shaonialife.cardeck.render.VideoBarControls

/** A click belongs to the exact foreground task shown when the dock button was rendered. */
internal object YoukuDockBack {
    fun supports(pkg: String) = pkg == CarVideoSources.YOUKU || pkg == CarVideoSources.CHEYU
    enum class Result { STALE, DELIVERED, REJECTED }

    fun send(
        target: VideoBarControls.Target,
        current: () -> Boolean,
        foreground: () -> VideoBarControls.Target?,
        dispatch: (Int) -> Boolean,
    ): Result {
        if (!supports(target.pkg) || target.display <= 0 || !current()) return Result.STALE
        if (foreground() != target || !current()) return Result.STALE
        return if (dispatch(target.display)) Result.DELIVERED else Result.REJECTED
    }
}
