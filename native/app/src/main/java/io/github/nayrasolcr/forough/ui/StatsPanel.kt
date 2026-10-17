package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.tunnel.TunnelStats
import kotlinx.coroutines.delay

/**
 * StatsPanel — Android port of the desktop stats panel: the same rows
 * (Status, Protocol, Tunnel, Uptime, Proxies, Regions, Public IP, Routing,
 * Beast mode, CDN scan, Traffic) with the same live values.
 */
@Composable
fun StatsPanel(stats: TunnelStats, running: Boolean) {
    var uptimeTick by remember { mutableLongStateOf(0L) }

    // Keep the uptime row live (desktop refreshes stats every 2s).
    LaunchedEffect(running) {
        while (running) {
            uptimeTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uptime = if (stats.startedAtMs > 0 && running) {
                uptimeTick - stats.startedAtMs
            } else 0L

            StatsRow(stringResource(R.string.stats_status), humanStatus(stats.status, running))
            Divider()
            StatsRow(
                stringResource(R.string.stats_protocol),
                if (stats.tunnelProtocol != "-") stats.tunnelProtocol else stats.protocol
            )
            Divider()
            StatsRow(stringResource(R.string.stats_tunnel), stats.tunnelCount.toString())
            Divider()
            StatsRow(stringResource(R.string.stats_uptime), humanUptime(uptime))
            Divider()
            StatsRow(
                stringResource(R.string.stats_proxies),
                "${stats.httpProxy} · ${stats.socksProxy}"
            )
            Divider()
            StatsRow(
                stringResource(R.string.stats_regions),
                "${stats.egressRegion} (${stats.availableRegions})"
            )
            Divider()
            StatsRow(stringResource(R.string.stats_public_ip), stats.publicIp)
            Divider()
            StatsRow(
                stringResource(R.string.stats_routing),
                if (running) (stats.routingStatus.ifEmpty { "-" }) else "-"
            )
            Divider()
            StatsRow(stringResource(R.string.stats_beast), stats.beastMode)
            Divider()
            StatsRow(stringResource(R.string.stats_cdn_scan), stats.cdnScan)
            Divider()
            StatsRow(
                stringResource(R.string.stats_traffic),
                "↓ ${NetTools.humanBytes(stats.totalDownBytes)} · ↑ ${NetTools.humanBytes(stats.totalUpBytes)}"
            )
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun humanStatus(status: String, running: Boolean): String = when {
    !running -> "disconnected"
    status == "connected" -> "connected"
    status == "connecting" -> "connecting"
    status == "reconnecting" -> "reconnecting"
    status == "exiting" -> "exiting"
    else -> status
}

private fun humanUptime(ms: Long): String {
    if (ms <= 0) return "-"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        else -> "%02d:%02d".format(m, s)
    }
}
