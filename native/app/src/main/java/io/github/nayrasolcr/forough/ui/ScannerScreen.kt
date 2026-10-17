package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.scanner.CdnScanner
import io.github.nayrasolcr.forough.tunnel.SettingsStore

/**
 * ScannerScreen — Android port of the desktop ScannerDialog: scope
 * switches, sample/concurrency steppers, custom ranges/SNI, live progress,
 * result list with per-edge TCP/TLS timings and checkboxes, "Use selected"
 * writes the candidates into the CDN fronting setting exactly like the
 * desktop's ips_applied handler.
 */
@Composable
fun ScannerScreen(store: SettingsStore, onToast: (String) -> Unit) {
    var scanCf by remember { mutableStateOf(store.scanCloudflare) }
    var scanFastly by remember { mutableStateOf(store.scanFastly) }
    var scanAkamai by remember { mutableStateOf(store.scanAkamai) }
    var scanCloudfront by remember { mutableStateOf(store.scanCloudfront) }
    var scanBunny by remember { mutableStateOf(store.scanBunny) }
    var sampleCount by remember { mutableIntStateOf(store.scanSampleCount) }
    var concurrency by remember { mutableIntStateOf(store.scanConcurrency) }
    var customCidrs by remember { mutableStateOf(store.scanCustomCidrs) }
    var customSnis by remember { mutableStateOf(store.scanCustomSnis) }

    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var aliveCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var checkedCount by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<CdnScanner.Result>() }
    val checked = remember { mutableStateListOf<String>() }
    val scanner = remember { CdnScanner() }

    // Pre-resolved strings for non-composable callbacks (onClick handlers).
    val scanFinishedFmt = stringResource(R.string.scanner_finished_fmt)
    val scanStoppedText = stringResource(R.string.scan_stopped)
    val selectNeededText = stringResource(R.string.scanner_select_needed)
    val edgesAppliedFmt = stringResource(R.string.scanner_edges_applied_fmt)

    DisposableEffect(scanner) {
        onDispose {
            if (scanner.isRunning) scanner.stop()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.scanner_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.scanner_scope),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScopeSwitch(stringResource(R.string.scanner_cloudflare), stringResource(R.string.scanner_cf_desc), scanCf) {
            scanCf = it
            store.scanCloudflare = it
        }
        ScopeSwitch(stringResource(R.string.scanner_fastly), stringResource(R.string.scanner_fastly_desc), scanFastly) {
            scanFastly = it
            store.scanFastly = it
        }
        ScopeSwitch(stringResource(R.string.scanner_akamai), stringResource(R.string.scanner_akamai_desc), scanAkamai) {
            scanAkamai = it
            store.scanAkamai = it
        }
        ScopeSwitch(stringResource(R.string.scanner_cloudfront), stringResource(R.string.scanner_cloudfront_desc), scanCloudfront) {
            scanCloudfront = it
            store.scanCloudfront = it
        }
        ScopeSwitch(stringResource(R.string.scanner_bunny), stringResource(R.string.scanner_bunny_desc), scanBunny) {
            scanBunny = it
            store.scanBunny = it
        }

        Stepper(stringResource(R.string.scanner_sample_size), stringResource(R.string.scanner_sample_desc), sampleCount, 128, 4096, 128) {
            sampleCount = it
            store.scanSampleCount = it
        }
        Stepper(stringResource(R.string.scanner_concurrency), stringResource(R.string.scanner_conc_desc), concurrency, 16, 256, 16) {
            concurrency = it
            store.scanConcurrency = it
        }

        OutlinedTextField(
            value = customCidrs,
            onValueChange = {
                customCidrs = it
                store.scanCustomCidrs = it
            },
            label = { Text(stringResource(R.string.scanner_custom_ranges)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = customSnis,
            onValueChange = {
                customSnis = it
                store.scanCustomSnis = it
            },
            label = { Text(stringResource(R.string.scanner_custom_sni)) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    results.clear()
                    checked.clear()
                    checkedCount = 0
                    progress = 0f
                    aliveCount = 0
                    totalCount = 0
                    scanner.scanCloudflare = scanCf
                    scanner.scanFastly = scanFastly
                    scanner.scanAkamai = scanAkamai
                    scanner.scanCloudfront = scanCloudfront
                    scanner.scanBunny = scanBunny
                    scanner.customCidrs = customCidrs
                    scanner.customSnis = customSnis
                    scanner.sampleCount = sampleCount
                    scanner.concurrency = concurrency
                    try {
                        scanner.start(object : CdnScanner.Listener {
                            override fun onProgress(scanned: Int, total: Int, alive: Int) {
                                progress = if (total > 0) scanned.toFloat() / total else 0f
                                aliveCount = alive
                                totalCount = total
                            }

                            override fun onResult(result: CdnScanner.Result) {
                                results.add(result)
                                if (result.alive) {
                                    checked.add(result.ip)
                                    checkedCount = checked.size
                                }
                            }

                            override fun onFinished(alive: Int, total: Int) {
                                isRunning = false
                                aliveCount = alive
                                totalCount = total
                                onToast(String.format(scanFinishedFmt, alive, total))
                            }
                        })
                        isRunning = true
                    } catch (e: Exception) {
                        onToast(e.message ?: "scan error")
                    }
                },
                enabled = !isRunning
            ) {
                Text(stringResource(R.string.scanner_start))
            }
            OutlinedButton(
                onClick = {
                    scanner.stop()
                    isRunning = false
                    onToast(scanStoppedText)
                },
                enabled = isRunning,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.scanner_stop))
            }
        }

        if (isRunning || totalCount > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${stringResource(R.string.scanner_alive)}: $aliveCount / $totalCount",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    // Same as the desktop ips_applied: selected IPs become the
                    // CDN fronting IP candidates; when the SNI pool is empty the
                    // checked SNIs are stored too.
                    val ips = checked.toList()
                    if (ips.isEmpty()) {
                        onToast(selectNeededText)
                        return@Button
                    }
                    val snis = results
                        .filter { it.ip in checked.toSet() }
                        .map { it.sni }
                        .distinct()
                        .joinToString(" ")
                    val current = store.load()
                    store.save(
                        current.copy(
                            cdnFrontingIpList = ips.joinToString(" "),
                            cdnFrontingSni = if (current.cdnFrontingSni.isBlank()) snis else current.cdnFrontingSni
                        )
                    )
                    onToast(String.format(edgesAppliedFmt, ips.size))
                },
                enabled = checkedCount > 0
            ) {
                Text(stringResource(R.string.scanner_use_selected))
            }
            OutlinedButton(onClick = {
                checked.clear()
                results.filter { it.alive }.forEach { checked.add(it.ip) }
                checkedCount = checked.size
            }) {
                Text(stringResource(R.string.scanner_select_all))
            }
        }

        Text(
            text = stringResource(R.string.scanner_selected_fmt, checkedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card {
            Column {
                // Most recent results on top; capped like the desktop (300 rows).
                val shown = results.takeLast(300).reversed()
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.scanner_no_results),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    shown.forEach { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = result.ip in checked.toSet(),
                                onCheckedChange = { c ->
                                    if (c) checked.add(result.ip) else checked.remove(result.ip)
                                    checkedCount = checked.size
                                },
                                enabled = result.alive
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.ip, fontFamily = FontFamily.Monospace)
                                Text(
                                    "${result.sni} · ${if (result.alive) {
                                        "TCP ${result.tcpMs}ms + TLS ${result.tlsMs}ms"
                                    } else if (result.tcpOnly) {
                                        stringResource(R.string.tls_failed)
                                    } else {
                                        "no route"
                                    }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    title: String,
    subtitle: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            modifier = Modifier.widthIn(min = 8.dp)
        ) { Text("-") }
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(
            onClick = { onChange((value + step).coerceAtMost(max)) }
        ) { Text("+") }
    }
}
