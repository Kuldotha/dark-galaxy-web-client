import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import app.GameScreen
import app.HomeScreen
import chain.ErSession
import chain.WebChainGateway
import com.interstellargames.darkgalaxy.core.events.Bus
import com.interstellargames.darkgalaxy.core.game.LobbySystem
import com.interstellargames.darkgalaxy.core.game.SessionSnapshot

/**
 * Web entry point — a thin shell: bring the walletless session online, start the shared
 * systems from :core against the browser gateway, and render screens that only ever talk
 * to the Bus.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "Dark Galaxy", canvasElementId = "ComposeTarget") {
        App()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Game(val gameId: Long) : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(Unit) {
        Bus.await(SessionSnapshot(address = null, ready = false, error = null))
        try { ErSession.ensure() } catch (_: Throwable) { /* reflected in the snapshot below */ }
        Bus.await(SessionSnapshot(ErSession.address, ErSession.isReady, ErSession.authError))
        LobbySystem(WebChainGateway, Bus.scope).start()
    }

    MaterialTheme {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(onOpenGame = { screen = Screen.Game(it) })
            is Screen.Game -> GameScreen(s.gameId, onBack = { screen = Screen.Home })
        }
    }
}
