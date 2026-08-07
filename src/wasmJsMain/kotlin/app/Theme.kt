package app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Interim palette for the not-yet-shared game screen. Dies when the game screen moves to :ui
 *  (whose theme package then takes over). */
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

@Composable
fun ActionButton(label: String, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .border(1.dp, if (enabled) color else DG.TextDim, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .widthIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) color else DG.TextDim, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
