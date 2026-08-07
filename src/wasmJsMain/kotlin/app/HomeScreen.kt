package app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interstellargames.darkgalaxy.core.chain.ChainConfig
import com.interstellargames.darkgalaxy.core.chain.GameLobby
import com.interstellargames.darkgalaxy.core.events.Bus
import com.interstellargames.darkgalaxy.core.events.IntentFailed
import com.interstellargames.darkgalaxy.core.game.JoinGame
import com.interstellargames.darkgalaxy.core.game.LeaveGame
import com.interstellargames.darkgalaxy.core.game.LobbySnapshot
import com.interstellargames.darkgalaxy.core.game.SessionSnapshot
import com.interstellargames.darkgalaxy.core.game.StartGame
import kotlinx.coroutines.launch

/** Pure bus subscriber: renders the latest [LobbySnapshot], throws roster intents back. */
@Composable
fun HomeScreen(onOpenGame: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val lobby by Bus.latest<LobbySnapshot>().collectAsState()
    val session by Bus.latest<SessionSnapshot>().collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Central failure surface — any handler anywhere that throws lands here as a packet.
    LaunchedEffect(Unit) {
        Bus.on<IntentFailed>().collect { error = it.message }
    }

    fun act(id: Long, intent: Any) {
        if (id in pending) return
        pending = pending + id
        scope.launch {
            try { Bus.await(intent) } catch (_: Throwable) { /* IntentFailed collected above */ }
            finally { pending = pending - id }
        }
    }

    val me = session?.address

    Column(Modifier.fillMaxSize().background(DG.BgDeep)) {
        Row(
            Modifier.fillMaxWidth().background(DG.BgPanel).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DARK GALAXY", color = DG.Accent, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            when {
                session?.ready == true -> Text("cmdr ${shortAddr(me)}", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                session?.error != null -> Text("ER offline — read-only", color = DG.Danger, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                else -> Text("connecting…", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        error?.let { msg ->
            Text(
                msg, color = DG.Danger, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A0F16))
                    .clickable { error = null }.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        val games = lobby?.games
        when {
            games == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("scanning the galaxy…", color = DG.TextMuted, fontFamily = FontFamily.Monospace)
            }
            games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("no games on chain yet", color = DG.TextMuted, fontFamily = FontFamily.Monospace)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(games.sortedByDescending { it.gameId }, key = { it.gameId }) { g ->
                    GameRow(
                        game = g,
                        joined = me != null && g.players.any { it == me },
                        busy = g.gameId in pending,
                        onJoin = { act(g.gameId, JoinGame(g.gameId)) },
                        onLeave = { act(g.gameId, LeaveGame(g.gameId)) },
                        onStart = { act(g.gameId, StartGame(g.gameId)) },
                        onOpen = { onOpenGame(g.gameId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(
    game: GameLobby,
    joined: Boolean,
    busy: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    val stateLabel = when (game.state) {
        ChainConfig.STATE_LOBBY -> "LOBBY"
        ChainConfig.STATE_INITIALIZED -> "READY"
        ChainConfig.STATE_ACTIVE -> "ACTIVE"
        ChainConfig.STATE_FINISHED -> "FINISHED"
        else -> "?"
    }
    val stateColor = when (game.state) {
        ChainConfig.STATE_ACTIVE -> DG.Ok
        ChainConfig.STATE_FINISHED -> DG.TextMuted
        else -> DG.Accent
    }

    Row(
        Modifier.fillMaxWidth()
            .background(DG.BgPanel, RoundedCornerShape(8.dp))
            .border(1.dp, DG.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Galaxy ${game.gameId + 1}", color = DG.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stateLabel, color = stateColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.border(1.dp, stateColor, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (joined) Text("JOINED", color = DG.Ok, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                "${game.playerCount}/${ChainConfig.MAX_PLAYERS} players · tick ${(game.tickSeconds / 60).coerceAtLeast(1)}m · host ${shortAddr(game.creator)}",
                color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
        }

        if (busy) {
            Text("…", color = DG.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        } else when {
            game.state == ChainConfig.STATE_LOBBY && !joined && game.playerCount < ChainConfig.MAX_PLAYERS ->
                ActionButton("JOIN", DG.Accent, onJoin)
            game.state == ChainConfig.STATE_LOBBY && joined -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("LEAVE", DG.Danger, onLeave)
                if (game.playerCount >= 2) ActionButton("START", DG.Ok, onStart)
            }
            game.state == ChainConfig.STATE_INITIALIZED && joined -> ActionButton("START", DG.Ok, onStart)
            game.state == ChainConfig.STATE_ACTIVE -> ActionButton(if (joined) "PLAY" else "VIEW", DG.Accent, onOpen)
            else -> {}
        }
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
