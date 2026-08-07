package app

import androidx.compose.ui.graphics.Color

/** Shared palette — kept close to the Android theme so the two clients read as one game. */
object DG {
    val BgDeep = Color(0xFF05070F)
    val BgPanel = Color(0xFF0A0F1E)
    val Border = Color(0xFF1A2337)
    val Accent = Color(0xFF63E6FF)
    val AccentDim = Color(0xFF1A5566)
    val Text = Color(0xFFB8C8D8)
    val TextMuted = Color(0xFF7A8699)
    val TextDim = Color(0xFF445566)
    val Danger = Color(0xFFFF6B6B)
    val Ok = Color(0xFF52E08A)
    val Unowned = Color(0xFF55617A)

    // One colour per roster slot, consistent for every viewer (matching the slot-keyed scheme).
    val SlotColors = listOf(
        Color(0xFF45C8FF), Color(0xFFFF5A5A), Color(0xFFFFB020), Color(0xFFB36BFF),
        Color(0xFF52E08A), Color(0xFFFF6BC1), Color(0xFFE8E36B), Color(0xFF9E8CFF),
    )

    fun ownerColor(owner: Int): Color = SlotColors.getOrNull(owner) ?: Unowned
}

fun shortAddr(a: String?): String =
    if (a == null || a.length < 8) a ?: "" else "${a.take(4)}…${a.takeLast(4)}"

fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val m = totalMinutes % 60
    val totalHours = totalMinutes / 60
    val h = totalHours % 24
    val d = totalHours / 24
    return when {
        totalHours >= 24 -> "${d}d ${h}h"
        totalMinutes >= 60 -> "${h}h ${m}m ${s}s"
        totalSeconds >= 60 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
