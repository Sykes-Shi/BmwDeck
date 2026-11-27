package io.github.shaonialife.cardeck.shizuku

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** A short-lived shell-side UI observation. It leaves the user's accessibility services running. */
internal object BaiduReturnOverlay {
    private const val BAIDU = "com.baidu.BaiduMap"
    private const val MAX_NODES = 900

    @Suppress("DEPRECATION")
    private class Snapshot : AutoCloseable {
        val controls = ArrayList<AccessibilityNodeInfo>()
        private val records = ArrayList<BaiduReturnControls.Node>()
        private val until = SystemClock.elapsedRealtime() + 1600
        var incomplete = false
            private set

        fun collect(node: AccessibilityNodeInfo, parent: Int = -1) {
            if (controls.size >= MAX_NODES || SystemClock.elapsedRealtime() >= until) {
                node.recycle()
                incomplete = true
                return
            }
            if (node.packageName != BAIDU) {
                node.recycle()
                return
            }
            val index = controls.size
            controls.add(node)
            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            records.add(BaiduReturnControls.Node(parent, node.viewIdResourceName.orEmpty().substringAfterLast('/'),
                BaiduReturnControls.caption(node.text, node.contentDescription), node.isClickable, node.isVisibleToUser,
                bounds.top, bounds.bottom))
            for (n in 0 until node.childCount) {
                if (incomplete) break
                node.getChild(n)?.let { collect(it, index) }
            }
        }

        fun target(): AccessibilityNodeInfo? = if (incomplete) null else BaiduReturnControls.closeTarget(records)?.let { controls[it] }

        override fun close() { controls.forEach { it.recycle() } }
    }

    @Suppress("DEPRECATION")
    @Synchronized fun dismiss(display: Int, permit: () -> Boolean): String {
        val thread = HandlerThread("baidu-return-window").apply { start() }
        var automation: UiAutomation? = null
        return try {
            // UiAutomation's connection constructor is provided by the framework on the shell user service.
            val connectionType = Class.forName("android.app.IUiAutomationConnection")
            val connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance()
            val client = UiAutomation::class.java.getConstructor(Looper::class.java, connectionType).newInstance(thread.looper, connection)
            automation = client
            UiAutomation::class.java.getMethod("connectWithTimeout", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                .invoke(client, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES, 1200L)
            client.serviceInfo = client.serviceInfo.apply {
                packageNames = arrayOf(BAIDU)
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            // Window subscriptions are populated asynchronously on a freshly connected automation.
            var rootNode: AccessibilityNodeInfo? = null
            var windowId = -1
            val readyUntil = SystemClock.elapsedRealtime() + 440
            do {
                val displays = client.windowsOnAllDisplays
                try {
                    val shown = displays[display].orEmpty().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
                    if (shown != null && shown.displayId == display) {
                        rootNode = shown.root
                        windowId = shown.id
                    }
                } finally {
                    for (i in 0 until displays.size()) displays.valueAt(i).forEach { it.recycle() }
                }
                if (rootNode != null) break
                SystemClock.sleep(65)
            } while (SystemClock.elapsedRealtime() < readyUntil)
            val root = rootNode ?: return "no-window"
            if (root.packageName != BAIDU) { root.recycle(); return "wrong-foreground" }
            Snapshot().use { initial ->
                initial.collect(root)
                if (initial.incomplete) return "incomplete"
                val action = initial.target() ?: return "no-matching-overlay"
                if (!permit() || !root.refresh()) return "stale"
                Snapshot().use { fresh ->
                    fresh.collect(AccessibilityNodeInfo.obtain(root))
                    if (fresh.incomplete) return "incomplete"
                    val node = fresh.target() ?: return "stale"
                    if (action != node || !node.isEnabled || node.windowId != windowId || !permit()) return "stale"
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "dismissed" else "refused"
                }
            }
        } catch (_: Exception) {
            "unavailable"
        } finally {
            automation?.let { runCatching { UiAutomation::class.java.getMethod("disconnect").invoke(it) } }
            thread.quitSafely()
        }
    }
}
