import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.interstellargames.darkgalaxy.ui.screens.game.GameScreen
import com.interstellargames.darkgalaxy.ui.screens.status.StatusScreen
import chain.ErSession
import chain.WebChainGateway
import com.interstellargames.darkgalaxy.core.events.Bus
import androidx.compose.runtime.DisposableEffect
import com.interstellargames.darkgalaxy.core.game.GameSystem
import com.interstellargames.darkgalaxy.core.game.LobbySystem
import com.interstellargames.darkgalaxy.core.game.SessionSnapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.interstellargames.darkgalaxy.ui.screens.home.HomeScreen
import com.interstellargames.darkgalaxy.ui.screens.lobby.LobbyScreen
import com.interstellargames.darkgalaxy.ui.theme.BgLetterbox
import com.interstellargames.darkgalaxy.ui.theme.BorderSubtle
import com.interstellargames.darkgalaxy.ui.theme.DarkGalaxyTheme

/**
 * Web entry point — a thin shell: bring the walletless session online, start the shared
 * systems from :core against the browser gateway, and render the shared screens from :ui.
 * Only the game screen (and this waiting-room stub) are still web-local, until they port.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "Dark Galaxy", canvasElementId = "ComposeTarget") {
        App()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Lobby : Screen
    data class Waiting(val gameId: Long) : Screen
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

    DarkGalaxyTheme {
        when (val s = screen) {
            is Screen.Home -> PhoneFrame {
                HomeScreen(
                    onOpenGame = { screen = Screen.Game(it) },
                    onOpenStatus = { screen = Screen.Waiting(it) },
                    onOpenFinished = { /* results screen not on web yet */ },
                    onOpenLobby = { screen = Screen.Lobby },
                    onOpenSettings = { /* no settings on web yet */ },
                )
            }
            is Screen.Lobby -> PhoneFrame {
                LobbyScreen(
                    onBack = { screen = Screen.Home },
                    onEnterGame = { screen = Screen.Waiting(it) },
                    onCreateGame = { /* needs a funded L1 wallet — not on web yet */ },
                    canCreate = false,
                )
            }
            is Screen.Waiting -> PhoneFrame {
                StatusScreen(
                    gameId = s.gameId,
                    onBack = { screen = Screen.Home },
                    onEnterGame = { screen = Screen.Game(it) },
                )
            }
            is Screen.Game -> {
                // The shell owns system lifecycles: the shared screen only presents packets,
                // so the game's poll/intent system must run while the screen is open.
                DisposableEffect(s.gameId) {
                    val system = GameSystem(WebChainGateway, Bus.scope, s.gameId)
                    system.start()
                    onDispose { system.stop() }
                }
                PhoneFrame {
                    GameScreen(
                        gameId = s.gameId,
                        onExit = { screen = Screen.Home },
                        // No finished/results screen on web yet — return to the list when a game ends.
                        onFinished = { screen = Screen.Home },
                    )
                }
            }
        }
    }
}

/**
 * The screens are phone-designed (one UI, two platforms), so the browser presents them in a
 * phone-proportioned frame: full height, mobile aspect ratio, letterboxed on the sides. On a
 * phone browser the frame IS the viewport, so it just fills the screen.
 */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(BgLetterbox),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .aspectRatio(390f / 844f, matchHeightConstraintsFirst = true)
                .border(1.dp, BorderSubtle),
        ) {
            content()
        }
    }
}
