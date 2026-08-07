package app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chain.Bridge
import chain.WebChainGateway
import com.interstellargames.darkgalaxy.core.chain.ChainConfig
import com.interstellargames.darkgalaxy.core.chain.Upgrade
import com.interstellargames.darkgalaxy.core.events.Bus
import com.interstellargames.darkgalaxy.core.events.IntentFailed
import com.interstellargames.darkgalaxy.core.game.BuyCarrier
import com.interstellargames.darkgalaxy.core.game.CarrierInfo
import com.interstellargames.darkgalaxy.core.game.GameSnapshot
import com.interstellargames.darkgalaxy.core.game.GameSystem
import com.interstellargames.darkgalaxy.core.game.SendFleet
import com.interstellargames.darkgalaxy.core.game.SessionSnapshot
import com.interstellargames.darkgalaxy.core.game.SetResearch
import com.interstellargames.darkgalaxy.core.game.StarInfo
import com.interstellargames.darkgalaxy.core.game.TransferShips
import com.interstellargames.darkgalaxy.core.game.UpgradeStar
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

private const val TECH_COUNT = 7
private val TECH_NAMES = listOf("Scanning", "Hyperspace", "Terraforming", "Experimentation", "Weapons", "Banking", "Manufacturing")

// Mirrors upgrade_star.rs: (level + 1) × mult / (effective_resources + 5).
private fun upgradeCost(level: Int, kind: Int, effectiveResources: Int): Int {
    val mult = if (kind == Upgrade.SCIENCE) 5000 else 500
    return (level + 1) * mult / (effectiveResources + 5)
}

/** Local plotting state — pure UI; the chain only hears about it on SEND. */
private data class Plot(val carrierSlot: Int, val originStar: Int, val route: List<Int> = emptyList())

private fun GameSnapshot.star(index: Int): StarInfo? = stars.firstOrNull { it.index == index }

private fun GameSnapshot.nearestStar(x: Float, y: Float): StarInfo? =
    stars.minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }

/** My parked/queued carriers sitting at this star. */
private fun GameSnapshot.carriersAt(starIndex: Int): List<CarrierInfo> =
    carriers.filter { it.mine && it.state != 3 && nearestStar(it.x, it.y)?.index == starIndex }

/** The game screen: owns a [GameSystem]'s lifecycle, renders the latest [GameSnapshot],
 *  and throws gameplay intents back onto the bus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen(gameId: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    DisposableEffect(gameId) {
        val system = GameSystem(WebChainGateway, Bus.scope, gameId)
        system.start()
        onDispose { system.stop() }
    }

    val latest by Bus.latest<GameSnapshot>().collectAsState()
    val snap = latest?.takeIf { it.gameId == gameId }   // sticky packet may be a previous game's

    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedStar by remember { mutableStateOf<Int?>(null) }
    var selectedCarrier by remember { mutableStateOf<Int?>(null) }
    var plot by remember { mutableStateOf<Plot?>(null) }
    var researchOpen by remember { mutableStateOf(false) }
    var nowSec by remember { mutableStateOf(Bridge.nowSec()) }

    LaunchedEffect(Unit) { Bus.on<IntentFailed>().collect { error = it.message } }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1_000); nowSec = Bridge.nowSec() }
    }

    fun act(key: String, intent: Any) {
        if (key in pending) return
        pending = pending + key
        scope.launch {
            try { Bus.await(intent) } catch (_: Throwable) { /* IntentFailed collected above */ }
            finally { pending = pending - key }
        }
    }

    Column(Modifier.fillMaxSize().background(DG.BgDeep)) {
        Hud(gameId, snap, nowSec, researchOpenClick = { researchOpen = true }, onBack)
        error?.let { msg ->
            Text(
                msg, color = DG.Danger, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A0F16))
                    .clickable { error = null }.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (snap != null) StarMap(
                    snap = snap,
                    selectedStar = selectedStar,
                    selectedCarrier = selectedCarrier,
                    plot = plot,
                    onTapStar = { index ->
                        val p = plot
                        if (p != null) plot = plotTap(snap, p, index)
                        else { selectedStar = index; selectedCarrier = null }
                    },
                    onTapCarrier = { slot -> if (plot == null) { selectedCarrier = slot; selectedStar = null } },
                    onTapVoid = { if (plot == null) { selectedStar = null; selectedCarrier = null } },
                )
                plot?.let { p ->
                    PlotBar(
                        p,
                        onSend = {
                            act("fleet:${p.carrierSlot}", SendFleet(gameId, p.carrierSlot, p.route))
                            plot = null
                        },
                        onCancel = { plot = null },
                    )
                }
                if (snap == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("establishing uplink…", color = DG.TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
            SidePanel(
                snap = snap,
                selectedStar = selectedStar,
                selectedCarrier = selectedCarrier,
                pending = pending,
                act = ::act,
                onBeginPlot = { c ->
                    val origin = snap?.nearestStar(c.x, c.y) ?: return@SidePanel
                    plot = Plot(c.slot, origin.index)
                    selectedCarrier = c.slot
                },
                onCloseCarrier = { selectedCarrier = null },
            )
        }
    }
    if (researchOpen && snap != null) {
        ResearchModal(snap, onPick = { branch ->
            act("research", SetResearch(gameId, branch))
            researchOpen = false
        }, onDismiss = { researchOpen = false })
    }
}

/** Append a star to the plotted route if it's within warp range of the current tip. */
private fun plotTap(snap: GameSnapshot, p: Plot, starIndex: Int): Plot {
    if (p.route.size >= 5) return p
    val tip = snap.star(p.route.lastOrNull() ?: p.originStar) ?: return p
    val target = snap.star(starIndex) ?: return p
    if (starIndex == tip.index) return p
    val dx = target.x - tip.x; val dy = target.y - tip.y
    if (sqrt(dx * dx + dy * dy) > snap.warpRange) return p
    return p.copy(route = p.route + starIndex)
}

// ── HUD ──────────────────────────────────────────────────────────────────────

@Composable
private fun Hud(gameId: Long, snap: GameSnapshot?, nowSec: Long, researchOpenClick: () -> Unit, onBack: () -> Unit) {
    val session by Bus.latest<SessionSnapshot>().collectAsState()
    Row(
        Modifier.fillMaxWidth().background(DG.BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("← BACK", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onBack() })
        Text("Galaxy ${gameId + 1}", color = DG.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (snap != null) {
            if (snap.finished) {
                Text(
                    if (snap.winner != ChainConfig.NO_WINNER) "FINISHED — winner: player ${snap.winner + 1}" else "FINISHED",
                    color = DG.Ok, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                )
            } else {
                Text("$ ${snap.cash}", color = DG.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                val cycle = snap.currentTick / snap.ticksPerCycle + 1
                Text("tick ${snap.currentTick} · cycle $cycle", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                val untilTick = (snap.nextTickAt - nowSec).toInt().coerceAtLeast(0)
                Text("next tick ${formatDuration(untilTick)}", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                if (snap.mySlot >= 0) {
                    val target = snap.researchTarget.takeIf { it in 0 until TECH_COUNT }?.let { TECH_NAMES[it] } ?: "none"
                    Text("RESEARCH: $target", color = DG.Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { researchOpenClick() })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (snap != null && snap.mySlot < 0) Text("spectating", color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(shortAddr(session?.address), color = DG.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Map ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StarMap(
    snap: GameSnapshot,
    selectedStar: Int?,
    selectedCarrier: Int?,
    plot: Plot?,
    onTapStar: (Int) -> Unit,
    onTapCarrier: (Int) -> Unit,
    onTapVoid: () -> Unit,
) {
    var camX by remember { mutableStateOf(0f) }
    var camY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(0f) }   // px per world unit; 0 = not fitted yet
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(snap.stars.isNotEmpty(), canvasSize) {
        if (scale == 0f && snap.stars.isNotEmpty() && canvasSize.width > 0) {
            val r = snap.stars.maxOf { maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.y)) }.coerceAtLeast(1f)
            scale = min(canvasSize.width, canvasSize.height) / (2f * r * 1.15f)
        }
    }

    fun toWorld(o: Offset): Offset =
        Offset((o.x - canvasSize.width / 2f) / scale + camX, (o.y - canvasSize.height / 2f) / scale + camY)

    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (scale <= 0f) return@onPointerEvent
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                scale = (scale * if (delta > 0) 0.88f else 1.14f).coerceIn(0.4f, 60f)
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (scale > 0f) { camX -= dragAmount.x / scale; camY -= dragAmount.y / scale }
                }
            }
            .pointerInput(snap.gameId, plot != null) {
                detectTapGestures { offset ->
                    if (scale <= 0f) return@detectTapGestures
                    val w = toWorld(offset)
                    val hitR = 14f / scale
                    val star = snap.nearestStar(w.x, w.y)?.takeIf {
                        val dx = it.x - w.x; val dy = it.y - w.y; sqrt(dx * dx + dy * dy) <= hitR
                    }
                    val carrier = snap.carriers.minByOrNull { (it.x - w.x) * (it.x - w.x) + (it.y - w.y) * (it.y - w.y) }
                        ?.takeIf { val dx = it.x - w.x; val dy = it.y - w.y; sqrt(dx * dx + dy * dy) <= hitR * 0.8f }
                    when {
                        plot != null && star != null -> onTapStar(star.index)
                        plot != null -> {}
                        carrier != null && carrier.mine -> onTapCarrier(carrier.slot)
                        star != null -> onTapStar(star.index)
                        else -> onTapVoid()
                    }
                }
            },
    ) {
        if (scale <= 0f) return@Canvas
        fun sx(wx: Float) = (wx - camX) * scale + size.width / 2f
        fun sy(wy: Float) = (wy - camY) * scale + size.height / 2f

        val starByIndex = snap.stars.associateBy { it.index }

        // Scan fields around my stars — the area in which enemy assets are visible.
        for (s in snap.stars) if (s.owner == snap.mySlot && snap.mySlot >= 0) {
            drawCircle(DG.Accent.copy(alpha = 0.045f), radius = snap.scanRange * scale, center = Offset(sx(s.x), sy(s.y)))
        }

        // Committed routes for my fleets.
        for (c in snap.carriers) if (c.mine && c.route.isNotEmpty() && c.state >= 2) {
            var px = sx(c.x); var py = sy(c.y)
            for (r in c.route) {
                val t = starByIndex[r] ?: continue
                drawLine(DG.Accent.copy(alpha = 0.5f), Offset(px, py), Offset(sx(t.x), sy(t.y)), strokeWidth = 1.5f)
                px = sx(t.x); py = sy(t.y)
            }
        }

        // Plot preview: warp-range ring around the tip and the staged legs.
        plot?.let { p ->
            val tip = starByIndex[p.route.lastOrNull() ?: p.originStar]
            if (tip != null) {
                drawCircle(DG.Ok.copy(alpha = 0.10f), radius = snap.warpRange * scale, center = Offset(sx(tip.x), sy(tip.y)))
                drawCircle(DG.Ok.copy(alpha = 0.5f), radius = snap.warpRange * scale, center = Offset(sx(tip.x), sy(tip.y)),
                    style = Stroke(width = 1f))
            }
            var prev = starByIndex[p.originStar]
            for (r in p.route) {
                val a = prev ?: break
                val b = starByIndex[r] ?: break
                drawLine(DG.Ok, Offset(sx(a.x), sy(a.y)), Offset(sx(b.x), sy(b.y)), strokeWidth = 2f)
                prev = b
            }
        }

        // Stars.
        val showNames = scale > 1.6f
        for (s in snap.stars) {
            val cx = sx(s.x); val cy = sy(s.y)
            if (cx < -60 || cy < -60 || cx > size.width + 60 || cy > size.height + 60) continue
            val owned = s.owner != ChainConfig.NO_WINNER
            val color = if (owned) DG.ownerColor(s.owner) else DG.Unowned
            drawCircle(color, radius = 4.5f, center = Offset(cx, cy))
            if (owned && s.owner == snap.mySlot) {
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 7.5f, center = Offset(cx, cy), style = Stroke(1.2f))
            }
            if (s.gated) drawCircle(DG.Accent, radius = 10f, center = Offset(cx, cy), style = Stroke(0.8f))
            if (s.index == selectedStar) {
                drawCircle(DG.Accent, radius = 12f, center = Offset(cx, cy), style = Stroke(1.5f))
            }
            if (showNames) {
                val label = buildString {
                    append(starName(s.index))
                    if (s.scanned && s.garrison > 0) append(" · ${s.garrison}")
                }
                drawText(
                    measurer, label,
                    topLeft = Offset(cx + 8f, cy - 6f),
                    style = TextStyle(color = if (owned) color else DG.TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }

        // Carriers — triangles at their view positions; parked ones offset beside the ring.
        for (c in snap.carriers) {
            var cx = sx(c.x); var cy = sy(c.y)
            if (c.state < 3) { cx += 8f; cy -= 8f }
            if (cx < -40 || cy < -40 || cx > size.width + 40 || cy > size.height + 40) continue
            val color = if (c.mine) DG.Accent else DG.ownerColor(c.owner)
            val path = Path().apply {
                moveTo(cx, cy - 5f); lineTo(cx - 4f, cy + 4f); lineTo(cx + 4f, cy + 4f); close()
            }
            drawPath(path, color)
            if (c.slot == selectedCarrier && c.mine) {
                drawCircle(color, radius = 9f, center = Offset(cx, cy), style = Stroke(1.2f))
            }
            if (showNames && c.ships > 0) {
                drawText(measurer, "${c.ships}",
                    topLeft = Offset(cx + 6f, cy + 2f),
                    style = TextStyle(color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace))
            }
        }
    }
}

@Composable
private fun PlotBar(plot: Plot, onSend: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(DG.BgPanel.copy(alpha = 0.95f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val names = (listOf(plot.originStar) + plot.route).joinToString(" → ") { starName(it) }
        Text("PLOT: $names", color = DG.Ok, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text("tap stars in range · ${plot.route.size}/5", color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        ActionButton("SEND", DG.Ok, onSend, enabled = plot.route.isNotEmpty())
        ActionButton("CANCEL", DG.Danger, onCancel)
    }
}

// ── Side panel ───────────────────────────────────────────────────────────────

@Composable
private fun SidePanel(
    snap: GameSnapshot?,
    selectedStar: Int?,
    selectedCarrier: Int?,
    pending: Set<String>,
    act: (String, Any) -> Unit,
    onBeginPlot: (CarrierInfo) -> Unit,
    onCloseCarrier: () -> Unit,
) {
    Column(
        Modifier.width(320.dp).fillMaxHeight().background(DG.BgPanel)
            .padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val star = snap?.star(selectedStar ?: -1)
        val carrier = snap?.carriers?.firstOrNull { it.slot == selectedCarrier && it.mine }

        when {
            snap == null -> {}
            carrier != null -> CarrierPanel(snap, carrier, act, onBeginPlot, onCloseCarrier)
            star != null -> StarPanel(snap, star, pending, act, onBeginPlot)
            else -> {
                Text("SELECT A STAR", color = DG.TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(
                    "drag to pan · scroll to zoom · tap a star for details",
                    color = DG.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = DG.Text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StarPanel(
    snap: GameSnapshot,
    star: StarInfo,
    pending: Set<String>,
    act: (String, Any) -> Unit,
    onBeginPlot: (CarrierInfo) -> Unit,
) {
    val mine = star.owner == snap.mySlot && snap.mySlot >= 0
    val ownerLabel = when {
        star.owner == ChainConfig.NO_WINNER -> "unclaimed"
        mine -> "yours"
        else -> "player ${star.owner + 1}"
    }
    Text(starName(star.index).uppercase(), color = DG.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    StatRow("owner", ownerLabel, if (mine) DG.Ok else DG.ownerColor(star.owner))
    if (!star.scanned) {
        Text("out of scan range — no telemetry", color = DG.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        return
    }
    StatRow("resources", "${star.resources}")
    StatRow("garrison", "${star.garrison}")
    if (star.gated) StatRow("warp gate", "online", DG.Accent)

    val terra = snap.researchLevels.getOrNull(2) ?: 0
    val effRes = star.resources + terra * 5

    if (mine) {
        Text("INFRASTRUCTURE", color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        UpgradeRow(snap, star, "economy", star.eco, Upgrade.ECONOMY, effRes, pending, act)
        UpgradeRow(snap, star, "industry", star.ind, Upgrade.INDUSTRY, effRes, pending, act)
        UpgradeRow(snap, star, "science", star.sci, Upgrade.SCIENCE, effRes, pending, act)

        Spacer(Modifier.size(4.dp))
        Text("CARRIERS", color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        for (c in snap.carriersAt(star.index)) CarrierRow(snap, c, star, act, onBeginPlot)
        val buyCost = ChainConfig.BUY_CARRIER_COST
        val buyKey = "buy:${star.index}"
        val canBuy = snap.cash >= buyCost && buyKey !in pending
        ActionButton(
            if (buyKey in pending) "BUYING…" else "BUY CARRIER — $$buyCost",
            if (canBuy) DG.Accent else DG.TextDim,
            { act(buyKey, BuyCarrier(snap.gameId, star.index)) },
            enabled = canBuy,
        )
    } else {
        StatRow("economy", "${star.eco}")
        StatRow("industry", "${star.ind}")
        StatRow("science", "${star.sci}")
    }
}

@Composable
private fun UpgradeRow(
    snap: GameSnapshot,
    star: StarInfo,
    label: String,
    level: Int,
    kind: Int,
    effRes: Int,
    pending: Set<String>,
    act: (String, Any) -> Unit,
) {
    val cost = upgradeCost(level, kind, effRes)
    val key = "up:$kind:${star.index}"
    val busy = key in pending
    val can = snap.cash >= cost && !busy
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label $level", color = DG.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        ActionButton(if (busy) "…" else "↑ $$cost", if (can) DG.Ok else DG.TextDim,
            { act(key, UpgradeStar(snap.gameId, star.index, kind)) }, enabled = can)
    }
}

@Composable
private fun CarrierRow(
    snap: GameSnapshot,
    c: CarrierInfo,
    star: StarInfo,
    act: (String, Any) -> Unit,
    onBeginPlot: (CarrierInfo) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, DG.Border, RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(carrierName(c.slot), color = DG.Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("${c.ships} ships", color = DG.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        if (c.state == 2) Text("departing → ${c.route.firstOrNull()?.let { starName(it) } ?: "?"}",
            color = DG.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton("+10", DG.Text, { act("xfer:${c.slot}", TransferShips(snap.gameId, c.slot, minOf(10, star.garrison))) }, enabled = star.garrison > 0)
            ActionButton("-10", DG.Text, { act("xfer:${c.slot}", TransferShips(snap.gameId, c.slot, -minOf(10, c.ships))) }, enabled = c.ships > 0)
            ActionButton("ALL", DG.Text, { act("xfer:${c.slot}", TransferShips(snap.gameId, c.slot, star.garrison)) }, enabled = star.garrison > 0)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton("PLOT ROUTE", DG.Ok, { onBeginPlot(c) })
            if (c.state == 2) ActionButton("CANCEL", DG.Danger, { act("fleet:${c.slot}", SendFleet(snap.gameId, c.slot, emptyList())) })
        }
    }
}

@Composable
private fun CarrierPanel(
    snap: GameSnapshot,
    c: CarrierInfo,
    act: (String, Any) -> Unit,
    onBeginPlot: (CarrierInfo) -> Unit,
    onClose: () -> Unit,
) {
    Text(carrierName(c.slot).uppercase(), color = DG.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    StatRow("ships", "${c.ships}")
    StatRow("status", when (c.state) { 1 -> "parked"; 2 -> "departing"; else -> "in transit" })
    if (c.route.isNotEmpty()) {
        StatRow("route", c.route.joinToString(" → ") { starName(it) })
    }
    if (c.state == 2) ActionButton("CANCEL ORDER", DG.Danger, { act("fleet:${c.slot}", SendFleet(snap.gameId, c.slot, emptyList())) })
    if (c.state != 3) ActionButton("PLOT ROUTE", DG.Ok, { onBeginPlot(c) })
    ActionButton("CLOSE", DG.TextMuted, onClose)
}

// ── Research modal ───────────────────────────────────────────────────────────

@Composable
private fun ResearchModal(snap: GameSnapshot, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(420.dp).background(DG.BgPanel, RoundedCornerShape(10.dp))
                .border(1.dp, DG.Border, RoundedCornerShape(10.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("RESEARCH", color = DG.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            for (i in 0 until TECH_COUNT) {
                val level = snap.researchLevels.getOrNull(i) ?: 0
                val points = snap.researchPoints.getOrNull(i) ?: 0
                val cost = (level + 1) * 144   // mirrors advance_tick
                val active = snap.researchTarget == i
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (active) DG.AccentDim.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { onPick(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${TECH_NAMES[i]} $level", color = if (active) DG.Accent else DG.Text,
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text("$points / $cost", color = DG.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Text("tap a branch to direct your scientists", color = DG.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
