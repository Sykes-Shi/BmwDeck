package io.github.shaonialife.cardeck.input

/**
 * A compact input report is interpreted only when the receiver has described its layout.
 * USB HID 1.11 section 6.2.2: report IDs precede the payload, fields are packed low bit
 * first, and Array values select usages starting at the logical minimum.
 */
internal class CompactCarHid private constructor(
    private val layouts: Map<Int, List<Field>>,
) {
    data class Presses(val navigation: List<CarControllerInput>, val media: List<Int>)

    private val heldNavigation = mutableMapOf<Int, Set<CarControllerAction>>()
    private val heldMedia = mutableMapOf<Int, Set<Int>>()

    /** Null means that this is not a valid report for one of the supported layouts. */
    fun decode(report: ByteArray): Presses? {
        if (report.size != 2) return null
        val usesId = 0 !in layouts
        val id = if (usesId) report[0].toInt() and 0xff else 0
        val fields = layouts[id] ?: return null
        val payload = if (usesId) report[1].toInt() and 0xff
            else (report[0].toInt() and 0xff) or ((report[1].toInt() and 0xff) shl 8)
        val navigation = linkedSetOf<CarControllerAction>()
        val relative = mutableListOf<CarControllerInput>()
        val media = linkedSetOf<Int>()
        for (field in fields) {
            val bits = (payload ushr field.offset) and ((1 shl field.size) - 1)
            val value = if (field.min < 0) signed(bits, field.size) else bits
            if (value !in field.min..field.max) {
                if (field.nullable) continue
                return null
            }
            val usage = if (field.array) field.usages.at(value - field.min) else field.usage
            if (usage == null) continue
            when (usage) {
                WHEEL, DIAL -> if (value != 0) {
                    val direction = if (value > 0) CarControllerAction.NEXT else CarControllerAction.PREVIOUS
                    relative.add(CarControllerInput(direction, kotlin.math.abs(value)))
                }
                X, Y -> if (value == field.min || value == field.max) {
                    navigation.add(if (usage == X) {
                        if (value < 0) CarControllerAction.LEFT else CarControllerAction.RIGHT
                    } else {
                        if (value < 0) CarControllerAction.UP else CarControllerAction.DOWN
                    })
                }
                else -> if (field.array || value == 1) {
                    direction(usage)?.let(navigation::add)
                    if (usage in MEDIA) media.add(usage and 0xffff)
                }
            }
        }
        val newNavigation = navigation - heldNavigation.put(id, navigation).orEmpty()
        val newMedia = media - heldMedia.put(id, media).orEmpty()
        return Presses(relative + newNavigation.map(::CarControllerInput), newMedia.toList())
    }

    private data class Globals(
        var page: Int = 0, var min: Int = 0, var max: Int = 0,
        var size: Int = 0, var count: Int = 0, var id: Int = 0,
    )

    private data class Usages(val values: List<Int>, val first: Int?, val last: Int?) {
        fun at(index: Int): Int? {
            if (index < 0) return null
            if (first != null && last != null && values.isEmpty()) {
                return (first + index).takeIf { it <= last }
            }
            return values.getOrNull(index)
        }

        fun contains(usage: Int): Boolean =
            usage in values || (values.isEmpty() && first != null && last != null && usage in first..last)
    }

    private data class Field(
        val offset: Int, val size: Int, val min: Int, val max: Int, val nullable: Boolean,
        val array: Boolean, val usage: Int?, val usages: Usages,
    )

    companion object {
        private const val X = 0x00010030
        private const val Y = 0x00010031
        private const val DIAL = 0x00010037
        private const val WHEEL = 0x00010038
        private val MEDIA = setOf(0x000c00b0, 0x000c00b1, 0x000c00b5, 0x000c00b6, 0x000c00cd)
        private val APPLICATIONS = setOf(0x00010004, 0x00010005, 0x00010008, 0x000c0001)

        private fun direction(usage: Int): CarControllerAction? = when (usage) {
            0x00090001 -> CarControllerAction.CONFIRM
            0x000c0224 -> CarControllerAction.BACK
            0x00010090 -> CarControllerAction.UP
            0x00010091 -> CarControllerAction.DOWN
            0x00010092 -> CarControllerAction.RIGHT
            0x00010093 -> CarControllerAction.LEFT
            else -> null
        }

        private fun signed(value: Int, bits: Int): Int = (value shl (32 - bits)) shr (32 - bits)

        private fun supportsVariable(usage: Int, g: Globals, relative: Boolean): Boolean = when {
            usage == WHEEL || usage == DIAL -> relative && g.min < 0 && g.max > 0
            usage == X || usage == Y -> !relative && g.min < 0 && g.max > 0
            else -> !relative && g.min == 0 && g.max == 1 && (direction(usage) != null || usage in MEDIA)
        }

        fun fromDescriptor(descriptor: ByteArray): CompactCarHid? {
            if (descriptor.isEmpty() || descriptor.size > 512) return null
            var globals = Globals()
            val stack = ArrayDeque<Globals>()
            val offsets = mutableMapOf<Int, Int>()
            val fields = mutableMapOf<Int, MutableList<Field>>()
            val usageList = mutableListOf<Int>()
            var usageMin: Int? = null
            var usageMax: Int? = null
            var depth = 0
            var supportedApplication = false
            var index = 0
            while (index < descriptor.size) {
                val item = descriptor[index++].toInt() and 0xff
                if (item == 0xfe) return null
                val size = (item and 3).let { if (it == 3) 4 else it }
                if (index + size > descriptor.size) return null
                var value = 0
                repeat(size) { value = value or ((descriptor[index++].toInt() and 0xff) shl (8 * it)) }
                fun usage() = if (size == 4) value else (globals.page shl 16) or value
                when (item and 0xfc) {
                    0x04 -> globals.page = value
                    0x14 -> if (size != 0) globals.min = signed(value, size * 8)
                    0x24 -> if (size != 0) globals.max = if (globals.min < 0) signed(value, size * 8) else value
                    0x74 -> globals.size = value
                    0x94 -> globals.count = value
                    0x84 -> if (value in 1..255) globals.id = value else return null
                    0xa4 -> stack.addLast(globals.copy())
                    0xb4 -> globals = stack.removeLastOrNull() ?: return null
                    0x08 -> usageList.add(usage())
                    0x18 -> usageMin = usage()
                    0x28 -> usageMax = usage()
                    0xa0 -> {
                        if (depth == 0) supportedApplication = value == 1 && usageList.singleOrNull() in APPLICATIONS
                        depth++
                    }
                    0xc0 -> {
                        if (depth == 0) return null
                        if (--depth == 0) supportedApplication = false
                    }
                    0x80 -> {
                        val g = globals
                        if (depth == 0 || g.size !in 1..128 || g.count !in 1..128 || g.min > g.max) return null
                        val offset = offsets[g.id] ?: 0
                        offsets[g.id] = offset + g.size * g.count
                        if (!supportedApplication || (value and 1) != 0 || offsets.getValue(g.id) > 16) {
                            usageList.clear()
                            usageMin = null
                            usageMax = null
                            continue
                        }
                        // Delimiters and mixed/scattered ranges are not interpreted as button maps.
                        val first = usageMin
                        val last = usageMax
                        if ((first == null) != (last == null) ||
                            (first != null && last != null && (first > last || first ushr 16 != last ushr 16 || usageList.isNotEmpty()))
                        ) return null
                        val usages = Usages(usageList.toList(), first, last)
                        val array = (value and 2) == 0
                        val relative = (value and 4) != 0
                        val nullable = (value and 0x40) != 0
                        repeat(g.count) { part ->
                            // HID Variable repeats the final explicit Usage when the report has more fields.
                            val semantic = usages.at(part) ?: usages.values.lastOrNull() ?: last
                            val valid = if (array) !relative &&
                                (MEDIA.any(usages::contains) || usages.contains(0x000c0224) || usages.contains(0x00090001))
                            else semantic != null && supportsVariable(semantic, g, relative)
                            if (valid) fields.getOrPut(g.id, ::mutableListOf).add(
                                Field(offset + part * g.size, g.size, g.min, g.max, nullable, array, semantic, usages),
                            )
                        }
                    }
                    0xa8 -> return null // HID Delimiter defines alternate sets.
                }
                if (item and 0x0c == 0) {
                    usageList.clear()
                    usageMin = null
                    usageMax = null
                }
            }
            if (depth != 0 || stack.isNotEmpty() || (0 in offsets && offsets.size > 1)) return null
            val layouts = fields.filterKeys { id ->
                val bytes = (offsets.getValue(id) + 7) / 8 + if (id == 0) 0 else 1
                bytes == 2
            }
            return layouts.takeIf { it.isNotEmpty() }?.let(::CompactCarHid)
        }
    }
}
