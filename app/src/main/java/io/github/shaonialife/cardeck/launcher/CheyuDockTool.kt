package io.github.shaonialife.cardeck.launcher

/** A vertical gesture on the native 车鱼视听 feed, in coordinates of the projected display. */
internal enum class CheyuDockTool(val label: String, val description: String) {
    PREVIOUS("上一条", "车鱼视听，向下滑动"),
    NEXT("下一条", "车鱼视听，向上滑动"),
    POINTER("指针", "用旋钮点选车鱼视听原版界面");

    data class Stroke(val x: Int, val fromY: Int, val toY: Int)

    fun stroke(width: Int, height: Int): Stroke? {
        if (width < 2 || height < 4 || this == POINTER) return null
        val x = width / 2
        if (this == NEXT) return Stroke(x, height - 1 - height / 4, height / 4)
        // 车鱼 3.20.1: downward swipes starting near the tabs do not change the feed.
        // Use the working 280 → 650 vertical range on the 720p reference player.
        return Stroke(x, (height.toLong() * 280 / 720).toInt(), (height.toLong() * 650 / 720).toInt())
    }
}
