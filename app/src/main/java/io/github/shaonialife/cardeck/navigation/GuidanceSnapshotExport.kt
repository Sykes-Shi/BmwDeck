package io.github.shaonialife.cardeck.navigation

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.widget.Toast
import io.github.shaonialife.cardeck.SessionController
import io.github.shaonialife.cardeck.launcher.BaiduCruise
import io.github.shaonialife.cardeck.media.MediaListenerService
import io.github.shaonialife.cardeck.ui.DarkMessageDialog
import io.github.shaonialife.cardeck.util.Bg
import io.github.shaonialife.cardeck.util.ClusterSampleFile
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** 只在用户点击时读当前活动通知。导出动作与距离，不包含通知正文、地址或目的地。 */
internal object GuidanceSnapshotExport {
    private val exporting = AtomicBoolean()

    private data class ExportResult(val message: String, val hasHint: Boolean)
    private class ListenerUnavailable(message: String) : IllegalStateException(message)

    fun saveVisiblePreview(context: Context, reading: BaiduCardReader.Reading?, imagePath: String, capturedAt: Instant): String {
        if (reading == null) {
            SessionController.appendLog("GUIDANCE: 地图卡已保存，未读出明确的转向和米数")
            return "已保存转向卡图片。当前字形或动作未识别。\n\n$imagePath"
        }
        val hint = "${reading.distanceMeters} 米后${reading.maneuver}"
        val saved = runCatching {
            val body = JSONObject()
                .put("sampledAt", capturedAt.toString())
                .put("source", "baidu_visible_turn_card")
                .put("imageFile", imagePath.substringAfterLast('/'))
                .put("maneuver", reading.maneuver)
                .put("distanceToManeuverMeters", reading.distanceMeters)
                .put("localReplay", GuidanceFramePreview.from(reading.maneuver, reading.distanceMeters))
            ClusterSampleFile.save(context, "CarDeck-guidance", "json", "application/json") {
                it.write(body.toString(2).toByteArray(Charsets.UTF_8))
            }
        }
        SessionController.appendLog("GUIDANCE: 地图卡读出 $hint，仅本地回放，json=${saved.isSuccess}")
        val paths = saved.fold({ "$imagePath\n$it" }, { "$imagePath\n回放文件保存失败" })
        return "手机从地图读到：$hint\n\n$paths"
    }

    fun prompt(activity: Activity, captureVisible: (() -> Unit)? = null) {
        if (!exporting.compareAndSet(false, true)) return
        Bg.run("guidance-sample") {
            val result = runCatching { export(activity.applicationContext) }
            activity.runOnUiThread {
                exporting.set(false)
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                val unavailable = result.exceptionOrNull() as? ListenerUnavailable
                val snapshot = result.getOrNull() ?: unavailable?.let { ExportResult(it.message.orEmpty(), false) }
                if (snapshot != null) {
                    val actions = buildList {
                        if (captureVisible != null) {
                            val emphasis = if (snapshot.hasHint) DarkMessageDialog.Emphasis.TEXT else DarkMessageDialog.Emphasis.PRIMARY
                            add(DarkMessageDialog.Action("从地图取提示", emphasis) { captureVisible() })
                        }
                        add(DarkMessageDialog.Action("知道了") {})
                    }
                    DarkMessageDialog.show(activity, "百度导航提示", snapshot.message, actions)
                } else {
                    Toast.makeText(activity, result.exceptionOrNull()?.localizedMessage ?: "导出失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun export(context: Context): ExportResult {
        val listener = MediaListenerService.guidanceReader
            ?: throw ListenerUnavailable("通知监听未连接。可在手机设置中检查 CarDeck 的通知使用权，或从中控地图取提示。")
        val active = try {
            listener.activeNotifications ?: throw ListenerUnavailable("通知监听正在重新连接，请稍后重试。")
        } catch (_: SecurityException) {
            throw ListenerUnavailable("读取通知需要 CarDeck 的通知使用权。")
        }
        val baidu = active.filter { it.packageName == BaiduCruise.PACKAGE && it.user == android.os.Process.myUserHandle() }
        val notices = baidu.sortedByDescending { it.postTime }.map { item ->
            val n = item.notification
            val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            val extras = n.extras
            val fields = if (summary || extras == null) emptyList() else buildList {
                for (key in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                    Notification.EXTRA_TITLE_BIG, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT)) {
                    extras.getCharSequence(key)?.let { add(it.take(512).toString()) }
                }
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().take(8).filterNotNull().forEach {
                    add(it.take(512).toString())
                }
            }
            GuidanceSample.Notice(n.category == Notification.CATEGORY_NAVIGATION, item.isOngoing, summary, fields)
        }
        val scan = GuidanceSample.inspect(notices)
        val hint = scan.match?.hint
        if (MediaListenerService.guidanceReader !== listener) throw ListenerUnavailable("通知监听刚刚断开，请重试。")
        val body = JSONObject()
            .put("sampledAt", Instant.now().toString())
            .put("source", "baidu_android_notification")
            .put("baiduNotifications", baidu.size)
            .put("guidanceCandidates", scan.candidates)
            .put("state", scan.state)
            .put("matchEvidence", scan.match?.evidence?.name ?: JSONObject.NULL)
            .put("navigationCategoryCount", notices.count { it.navigation })
            .put("ongoingCount", notices.count { it.ongoing })
            .put("groupSummaryCount", notices.count { it.groupSummary })
            .put("notificationsWithText", notices.count { it.texts.any(String::isNotBlank) })
            .put("maneuver", hint?.maneuver ?: JSONObject.NULL)
            .put("distanceToManeuverMeters", hint?.meters ?: JSONObject.NULL)
        if (hint != null) GuidanceFramePreview.from(hint.maneuver, hint.meters)?.let { body.put("localReplay", it) }
        val file = ClusterSampleFile.save(context, "CarDeck-guidance", "json", "application/json") {
            it.write(body.toString(2).toByteArray(Charsets.UTF_8))
        }
        SessionController.appendLog("GUIDANCE: 通知采样 state=${scan.state} baidu=${baidu.size} candidates=${scan.candidates}")
        val summary = when (scan.state) {
            "matched" -> {
                val label = if (scan.match?.evidence == GuidanceSample.Evidence.NAVIGATION_CATEGORY) "导航通知" else "按通知文字识别"
                listOfNotNull(label, hint?.meters?.let { "${it} 米" }, hint?.maneuver).joinToString(" · ")
            }
            "no_baidu_notifications" -> "通知监听已连接，当前百度地图活动通知为 0 条。"
            "group_summaries_only" -> "百度地图只有分组汇总通知，未找到单独的引导通知。"
            "no_standard_text" -> "读到百度地图通知，系统提供的标准文字字段为空。"
            "guidance_without_recognized_instruction" -> "发现百度导航相关通知，当前文字中未识别出明确的路口动作。"
            else -> "读到 ${notices.size} 条百度地图通知，未匹配到导航引导。"
        }
        return ExportResult("$summary\n\n已保存到 $file", hint != null)
    }
}
