package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.PortableWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.tunnel.Region
import io.github.nayrasolcr.forough.tunnel.TunnelStats
import io.github.nayrasolcr.forough.ui.theme.AdwAccent
import io.github.nayrasolcr.forough.ui.theme.AdwDestructive
import io.github.nayrasolcr.forough.ui.theme.GraphRx
import io.github.nayrasolcr.forough.ui.theme.GraphTx
import kotlinx.coroutines.delay

/**
 * ConnectionPanel — Android port of the desktop widget of the same name:
 * the 96px circular connect button (suggested blue → destructive red),
 * status title, IP line with flag, routing line, and the live 60-second
 * traffic graph drawn as two filled sparklines with identical colors.
 */
@Composable
fun ConnectionPanel(
    stats: TunnelStats,
    running: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val window = 60
    val rxHistory = remember { mutableStateListOf<Double>().apply { repeat(window) { add(0.0) } } }
    val txHistory = remember { mutableStateListOf<Double>().apply { repeat(window) { add(0.0) } } }
    var lastRx by remember { mutableStateOf(0L) }
    var lastTx by remember { mutableStateOf(0L) }
    var haveLast by remember { mutableStateOf(false) }
    var trafficLabel by remember { mutableStateOf("") }

    // Sample once per second while running — same cadence as the desktop.
    // Reads live values from the state bus so the 1s loop never works with
    // a stale captured stats object.
    LaunchedEffect(running) {
        if (!running) {
            haveLast = false
            trafficLabel = ""
            for (i in 0 until window) {
                rxHistory[i] = 0.0
                txHistory[i] = 0.0
            }
            return@LaunchedEffect
        }
        while (true) {
            val current = ForoughBus.stats.value
            val rx = current.totalDownBytes
            val tx = current.totalUpBytes
            if (haveLast) {
                val down = (rx - lastRx).coerceAtLeast(0).toDouble()
                val up = (tx - lastTx).coerceAtLeast(0).toDouble()
                pushSample(rxHistory, down)
                pushSample(txHistory, up)
                trafficLabel = "↓ ${NetTools.humanSpeed(down.toLong())}/s   ↑ ${NetTools.humanSpeed(up.toLong())}/s"
            }
            lastRx = rx
            lastTx = tx
            haveLast = true
            delay(1_000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Circular connect button — 96dp like the desktop, suggested-action
        // blue when idle, destructive-action red when running.
        Button(
            onClick = { if (running) onDisconnect() else onConnect() },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) AdwDestructive else AdwAccent,
                contentColor = Color.White
            ),
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                imageVector = if (running) Icons.Outlined.PortableWifiOff else Icons.Filled.VpnKey,
                contentDescription = if (running) {
                    stringResource(R.string.disconnect)
                } else stringResource(R.string.connect),
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = statusLabel(stats, running),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = ipLabel(stats),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (running) stats.routingStatus else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = trafficLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrafficGraph(rxHistory.toList(), txHistory.toList())
    }
}

@Composable
private fun TrafficGraph(rxHistory: List<Double>, txHistory: List<Double>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp)
    ) {
        // Background like the desktop graph (grey 12%).
        drawRect(Color(0x80, 0x80, 0x80, 31))
        drawSeries(rxHistory, GraphRx)
        drawSeries(txHistory, GraphTx)
    }
}

private fun DrawScope.drawSeries(history: List<Double>, color: Color) {
    if (history.isEmpty()) return
    var max = 1.0
    for (v in history) if (v > max) max = v

    val step = size.width / (history.size - 1)
    val path = Path()
    path.moveTo(0f, size.height)
    for (i in history.indices) {
        val y = (size.height - ((history[i] / max) * (size.height - 4)).toFloat() - 2f)
            .coerceIn(0f, size.height)
        path.lineTo(i * step, y)
    }
    path.lineTo(size.width, size.height)
    path.close()

    drawPath(path, color.copy(alpha = 0.35f))
    drawPath(path, color.copy(alpha = 0.95f), style = Stroke(width = 1.5.dp.toPx()))
}

private fun pushSample(history: MutableList<Double>, value: Double) {
    for (i in 0 until history.size - 1) {
        history[i] = history[i + 1]
    }
    history[history.size - 1] = value
}

@Composable
private fun statusLabel(stats: TunnelStats, running: Boolean): String {
    if (!running) return stringResource(R.string.status_ready)
    return when (stats.status) {
        "connected" -> stringResource(R.string.status_connected_in, stats.connectDurationMs / 1000.0)
        "connecting" -> stringResource(R.string.status_connecting)
        "reconnecting" -> stringResource(R.string.status_reconnecting)
        "disconnecting", "exiting" -> stringResource(R.string.status_disconnecting)
        else -> stringResource(R.string.status_ready)
    }
}

private fun ipLabel(stats: TunnelStats): String {
    if (stats.status != "connected") return ""
    val flag = Region.flagForCode(stats.egressRegion)
    return when {
        stats.publicIp != "-" && stats.publicIp.isNotEmpty() -> "$flag ${stats.publicIp}"
        flag.isNotEmpty() -> "$flag ${stats.egressRegion}"
        else -> stats.egressRegion
    }
}
