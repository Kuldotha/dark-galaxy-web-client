package app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interstellargames.darkgalaxy.core.chain.ChainConfig
import com.interstellargames.darkgalaxy.core.events.Bus
import com.interstellargames.darkgalaxy.core.events.IntentFailed
import com.interstellargames.darkgalaxy.core.game.LeaveGame
import com.interstellargames.darkgalaxy.core.game.LobbySnapshot
import com.interstellargames.darkgalaxy.core.game.SessionSnapshot
import com.interstellargames.darkgalaxy.core.game.StartGame
import com.interstellargames.darkgalaxy.ui.Portrait
import kotlinx.coroutines.launch

/**
 * Web-local waiting room for a joined game that hasn't started — roster, leave, start.
 * Dies when the Android StatusScreen ports into :ui; auto-forwards once the game is running.
 */
@Composable
fun WaitingScreen(gameId: Long, onBack: () -> Unit, onGameStarted: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val lobby by Bus.latest<LobbySnapshot>().collectAsState()
    val session by Bus.latest<SessionSnapshot>().collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { Bus.on<IntentFailed>().collect { error = it.message } }

    val game = lobby?.games?.firstOrNull { it.gameId == gameId }

    // The lobby flips to ACTIVE when anyone starts it — follow along.
    LaunchedEffect(game?.state) {
        if (game != null && game.state >= ChainConfig.STATE_ACTIVE) onGameStarted(gameId)
    }

    fun act(intent: Any, then: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { Bus.await(intent); then() } catch (_: Throwable) { /* IntentFailed above */ }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(DG.BgDeep).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("← BACK", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { onBack() })
            Text("Galaxy ${gameId + 1} — waiting room", color = DG.Accent, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        }
        error?.let {
            Text(it, color = DG.Danger, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { error = null })
        }
        if (game == null) {
            Text("locating game…", color = DG.TextMuted, fontFamily = FontFamily.Monospace)
            return@Column
        }

        Text("${game.playerCount}/${ChainConfig.MAX_PLAYERS} commanders assembled", color = DG.Text,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            game.players.forEachIndexed { slot, p ->
                if (p.isEmpty()) return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, DG.ownerColor(slot), CircleShape)) {
                        Portrait(seed = p, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        if (p == session?.address) "${shortAddr(p)} (you)" else shortAddr(p),
                        color = if (p == session?.address) DG.Accent else DG.Text,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (game.playerCount >= 2 || game.state == ChainConfig.STATE_INITIALIZED) {
                ActionButton(if (busy) "…" else "START GAME", DG.Ok, { act(StartGame(gameId)) }, enabled = !busy)
            }
            ActionButton(if (busy) "…" else "LEAVE", DG.Danger, { act(LeaveGame(gameId)) { onBack() } }, enabled = !busy)
        }
        Text("the game starts for everyone once any joined player starts it",
            color = DG.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
