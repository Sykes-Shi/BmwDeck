package io.github.shaonialife.cardeck.navigation

import io.github.shaonialife.cardeck.iap2.Iap2Csm
import io.github.shaonialife.cardeck.iap2.RouteGuidanceEncoder

/** One explicit, short preview of a confirmed map cue on an already subscribed receiver. */
internal class ParkedGuidanceSession(
    private val send: (ByteArray) -> Boolean,
    private val log: (String) -> Unit,
    private val flush: () -> Unit = {},
) {
    private data class Subscription(val component: Int, val name: Boolean, val support: Boolean)
    private data class Preview(val source: Subscription, val clearAt: Long, val onEnded: () -> Unit)

    private var subscription: Subscription? = null
    private var active: Preview? = null

    fun canShow(): Boolean = subscription != null && active == null

    fun subscribe(body: ByteArray) {
        val fields = Iap2Csm.parseParams(body)
        val component = fields.singleOrNull { it.id == 0 }?.value
        if (fields.sumOf { it.value.size + 4 } != body.size || component?.size != 2) return
        val id = ((component[0].toInt() and 255) shl 8) or (component[1].toInt() and 255)
        if (subscription?.component != id) cancel()
        subscription = Subscription(id, fields.any { it.id == 1 && it.value.isEmpty() },
            fields.any { it.id == 2 && it.value.isEmpty() })
        log("CLUSTER: 已收到导航订阅 component=$id")
    }

    fun unsubscribe(body: ByteArray) {
        val component = Iap2Csm.parseParams(body).singleOrNull { it.id == 0 }?.value
        if (component?.size == 2) {
            val id = ((component[0].toInt() and 255) shl 8) or (component[1].toInt() and 255)
            if (id != subscription?.component) return
        }
        reset()
        log("CLUSTER: 车机已停止导航订阅")
    }

    fun show(turnLeft: Boolean, meters: Int, now: Long, onEnded: () -> Unit = {}): Boolean {
        val sub = subscription ?: return false
        if (active != null || meters !in 0..9999) return false
        val encoder = RouteGuidanceEncoder(sub.component)
        val turn = if (turnLeft) RouteGuidanceEncoder.Turn.LEFT else RouteGuidanceEncoder.Turn.RIGHT
        try {
            if (sub.name || sub.support) {
                val source = encoder.sourceDeclaration("CarDeck".takeIf { sub.name }, true.takeIf { sub.support })
                if (!send(source)) return false
            }
            if (!send(encoder.routeSet(0, meters)) || !send(encoder.maneuver(0, turn))) {
                clearSource(sub)
                return false
            }
            flush()
        } catch (error: Exception) {
            log("CLUSTER: 停车提示提交异常 ${error.javaClass.simpleName}")
            clearSource(sub)
            return false
        }
        active = Preview(sub, now + 6_000L, onEnded)
        log("CLUSTER: 停车提示已提交 component=${sub.component} 方向=${if (turnLeft) "左" else "右"} 距离=${meters}m")
        return true
    }

    fun tick(now: Long) {
        val deadline = active?.clearAt ?: return
        if (now >= deadline) cancel()
    }

    fun cancel() {
        val preview = active ?: return
        active = null
        try {
            clearSource(preview.source)
        } finally {
            preview.onEnded()
        }
    }

    private fun clearSource(sub: Subscription) {
        try {
            val encoder = RouteGuidanceEncoder(sub.component)
            val ended = send(encoder.endRoute())
            val withdrawn = !sub.support || send(encoder.sourceDeclaration(null, false))
            flush()
            log("CLUSTER: 停车提示结束 component=${sub.component} 清除已提交=$ended 来源撤回已提交=$withdrawn")
        } catch (error: Exception) {
            log("CLUSTER: 停车提示清除异常 ${error.javaClass.simpleName}")
        }
    }

    fun reset() {
        val preview = active
        active = null
        subscription = null
        preview?.onEnded?.invoke()
    }
}
