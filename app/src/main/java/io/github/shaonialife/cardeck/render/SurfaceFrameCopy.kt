package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object SurfaceFrameCopy {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** A failed copy owns the bitmap and recycles it after any pending native writes end. */
    fun copyInto(surface: Surface, bitmap: Bitmap, timeoutNs: Long) {
        val result = CompletableFuture<Int>()
        try {
            PixelCopy.request(surface, bitmap, { status ->
                if (!result.complete(status)) bitmap.recycle()
            }, handler)
        } catch (failure: RuntimeException) {
            bitmap.recycle()
            throw IllegalStateException("视频画面读取失败", failure)
        }
        var delivered = false
        try {
            val status = result.get(timeoutNs, TimeUnit.NANOSECONDS)
            check(status == PixelCopy.SUCCESS) { "视频画面读取失败（系统返回 $status）" }
            delivered = true
        } catch (_: TimeoutException) {
            error("视频画面复制超时，请重试")
        } finally {
            if (!delivered && !result.cancel(false)) bitmap.recycle()
        }
    }
}
