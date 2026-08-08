import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import com.interstellargames.darkgalaxy.ui.screens.connect.ConnectScreen
import com.interstellargames.darkgalaxy.ui.screens.onboarding.TutorialScreen
import com.interstellargames.darkgalaxy.ui.screens.onboarding.TutorialSeen
import com.interstellargames.darkgalaxy.ui.screens.settings.SettingsScreen
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.interstellargames.darkgalaxy.ui.screens.home.HomeScreen
import com.interstellargames.darkgalaxy.ui.screens.lobby.LobbyScreen
import com.interstellargames.darkgalaxy.ui.theme.BgLetterbox
import com.interstellargames.darkgalaxy.ui.theme.BorderSubtle
import com.interstellargames.darkgalaxy.ui.theme.DarkGalaxyTheme
import kotlinx.coroutines.launch

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
    data object Landing : Screen
    data object Tutorial : Screen
    data object Home : Screen
    data object Lobby : Screen
    data object Settings : Screen
    data class Waiting(val gameId: Long) : Screen
    data class Game(val gameId: Long) : Screen
}

@Composable
private fun App() {
    var screen by remember {
        mutableStateOf<Screen>(
            when {
                !ErSession.hasIdentity -> Screen.Landing
                TutorialSeen.seen() -> Screen.Home
                else -> Screen.Tutorial
            }
        )
    }
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var lobbyStarted by remember { mutableStateOf(false) }

    suspend fun boot() {
        Bus.await(SessionSnapshot(ErSession.address, ErSession.isReady, ErSession.authError))
        if (!lobbyStarted) {
            lobbyStarted = true
            LobbySystem(WebChainGateway, Bus.scope).start()
        }
    }

    // A previously chosen identity resumes silently; no choice yet → the landing shows.
    LaunchedEffect(Unit) {
        Bus.await(SessionSnapshot(address = null, ready = false, error = null))
        if (ErSession.hasIdentity) {
            try { ErSession.ensure() } catch (_: Throwable) { /* reflected in the snapshot */ }
            boot()
        }
    }

    fun enter(via: suspend () -> Unit) {
        if (connecting) return
        connecting = true
        scope.launch {
            try {
                via()
                boot()
                screen = if (TutorialSeen.seen()) Screen.Home else Screen.Tutorial
            } catch (_: Throwable) { /* stay on the landing; wallet popups can be dismissed */ }
            finally { connecting = false }
        }
    }

    DarkGalaxyTheme {
        when (val s = screen) {
            is Screen.Landing -> PhoneFrame(forcePhone = true) {
                ConnectScreen(
                    isConnecting = connecting,
                    onEnterWalletless = { enter { ErSession.enterWalletless() } },
                    onConnectWallet = if (ErSession.walletSupported) {
                        { enter { ErSession.connectWallet() } }
                    } else null,
                )
            }
            is Screen.Tutorial -> PhoneFrame {
                TutorialScreen(onDone = {
                    TutorialSeen.markSeen()
                    screen = Screen.Home
                })
            }
            is Screen.Settings -> PhoneFrame {
                SettingsScreen(
                    walletAddress = ErSession.walletAddress ?: ErSession.address,
                    toggles = emptyList(),           // notifications are an Android concern
                    onBack = { screen = Screen.Home },
                    onReplayTutorial = { screen = Screen.Tutorial },
                    onSignOut = {
                        // Drops the CHOICE, never the walletless seed (that IS the account);
                        // a wallet identity re-derives by signing again.
                        ErSession.logout()
                        lobbyStarted = false
                        Bus.emit(SessionSnapshot(address = null, ready = false, error = null))
                        screen = Screen.Landing
                    },
                )
            }
            is Screen.Home -> PhoneFrame {
                HomeScreen(
                    onOpenGame = { screen = Screen.Game(it) },
                    onOpenStatus = { screen = Screen.Waiting(it) },
                    onOpenFinished = { screen = Screen.Game(it) },
                    onOpenLobby = { screen = Screen.Lobby },
                    onOpenSettings = { screen = Screen.Settings },
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
                        onOpenSettings = { screen = Screen.Settings },
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
private fun PhoneFrame(forcePhone: Boolean = false, content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(BgLetterbox),
        contentAlignment = Alignment.Center,
    ) {
        // Narrow windows get the designed phone strip; wide ones relax to a 1:1 cap —
        // wider content, but never actually widescreen. The landing keeps the phone shape
        // everywhere (its art is composed for it).
        val aspect = if (!forcePhone && maxWidth >= 700.dp) 1f else 390f / 844f
        Box(
            Modifier
                .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                .clipToBounds()   // nothing a screen draws may escape into the letterbox
                .border(1.dp, BorderSubtle),
        ) {
            content()
        }
    }
}
