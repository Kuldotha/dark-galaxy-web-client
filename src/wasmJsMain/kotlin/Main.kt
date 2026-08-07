import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.CanvasBasedWindow
import com.interstellargames.darkgalaxy.core.chain.Address
import com.interstellargames.darkgalaxy.core.chain.Base58
import com.interstellargames.darkgalaxy.core.chain.findProgramAddress

/**
 * Web entry point.
 *
 * Compose Multiplatform renders the whole app into one canvas, so this is deliberately the ONLY
 * web-specific UI code — everything real will come from the shared :ui module once it is
 * extracted. Anything that accumulates here is UI that would have to be written twice, which is
 * exactly what this project exists to avoid.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "Dark Galaxy", canvasElementId = "ComposeTarget") {
        Scaffold()
    }
}

private val BgDeep = Color(0xFF05070F)
private val Cyan = Color(0xFF63E6FF)
private val TextMuted = Color(0xFF7A8699)

@Composable
private fun Scaffold() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(BgDeep).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "DARK GALAXY",
                color = Cyan,
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
            )
            // Proof the shared module really is shared: this address and its decode come from
            // :core — the same file the Android app links against, running here as Wasm.
            val programId = Address("A9yqzJhNrrdbgz7DnPKAfpC5W3DiGHwLWds2bwJypNXo")
            Text(
                "program ${programId.base58.take(8)}… decodes to ${Base58.decode(programId.base58).size} bytes",
                color = Cyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            // Surfacing the PDA seam on screen: the Karma harness swallows failure messages,
            // so the page itself is the fastest debugger. Shows the derived address, or the
            // exception text if the JS interop is broken.
            val pda = runCatching {
                findProgramAddress(listOf("reserve".encodeToByteArray()), programId).base58
            }
            Text(
                if (pda.isSuccess) "reserve PDA ${pda.getOrNull()}"
                else "PDA FAILED: ${pda.exceptionOrNull()?.message ?: pda.exceptionOrNull()}",
                color = if (pda.isSuccess) Cyan else Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "web build — shared :core linked",
                color = TextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
