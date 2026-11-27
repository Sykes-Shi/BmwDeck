package io.github.shaonialife.cardeck.launcher

/** Parse only the foreground activity on the relevant display. */
internal object CarAppForeground {
    // Keep display boundaries and the first resumed record even when it belongs to another app.
    const val ACTIVITY_FILTER = "grep -E 'DUMP TIMEOUT|^[[:space:]]*(Display([[:space:]]+#[0-9]+|:[[:space:]]*mDisplayId=)|topResumedActivity|mResumedActivity|Resumed:|ResumedActivity:|ActivityTaskSupervisor state:)'"
    private val displayHeader = Regex("""^Display(?:\s+#|:\s*mDisplayId=)(\d+)\b""")
    private val resumed = Regex("""^(?:topResumedActivity|mResumedActivity|Resumed)\s*[:=]\s*""")
    private val record = Regex(
        """^ActivityRecord\{([0-9a-fA-F]+)\s+(?:u\d+\s+|\d+:)([\w.]+)/([\w.$]+)(?:/u\d+)?\s+t(\d+)\b[^}]*\}""",
    )
    data class Foreground(val component: String, val task: Int)

    private fun activity(raw: String): Foreground? {
        val match = record.find(raw) ?: return null
        val (_, pkg, name, task) = match.destructured
        if (raw.contains("finishing", ignoreCase = true)) return null
        return Foreground("$pkg/" + if (name.startsWith('.')) pkg + name else name, task.toIntOrNull() ?: return null)
    }

    fun foregroundOnDisplay(activityDump: String?, display: Int): Foreground? =
        topOnDisplay(activityDump, display)?.takeIf { it.component.substringBefore('/') in io.github.shaonialife.cardeck.render.VideoBarControls.packages }

    fun topOnDisplay(activityDump: String?, display: Int): Foreground? {
        if (display <= 0) return null
        return resumedActivities(activityDump)[display]
    }

    private fun resumedActivities(activityDump: String?): Map<Int, Foreground?> {
        val placements = mutableMapOf<Int, Foreground?>()
        var display: Int? = null
        for (raw in activityDump.orEmpty().lineSequence()) {
            val line = raw.trim()
            val header = displayHeader.find(line)
            if (header != null) {
                display = header.groupValues[1].toIntOrNull()
                continue
            }
            // The trailing system-wide summary is not scoped to the preceding display.
            if (line.startsWith("ResumedActivity:") || line.startsWith("ActivityTaskSupervisor state:")) display = null
            val current = display ?: continue
            val field = resumed.find(line) ?: continue
            // Tasks are dumped top-first. Never promote a lower or historical task.
            if (current !in placements) placements[current] = activity(line.substring(field.range.last + 1))
        }
        return placements
    }
}
