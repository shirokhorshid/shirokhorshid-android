package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.ProtocolMode
import io.github.nayrasolcr.forough.tunnel.Region
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.tunnel.TunnelOptions

/**
 * SettingsScreen — Android port of the desktop SettingsDialog: the same
 * connection options (protocol mode, egress region, beast mode, timeout,
 * LAN sharing, upstream proxy, conduit compartment, CDN fronting
 * IP/SNI, OSL URLs) plus the interface language switch.
 *
 * Desktop-only rows (sing-box TUN, system proxy, Fake-IP experiment,
 * binary paths) have no meaning on Android — the VpnService is the
 * whole-device routing — and are intentionally absent.
 */
@Composable
fun SettingsScreen(store: SettingsStore, onSave: (String) -> Unit, onLanguageChanged: () -> Unit) {
    var options by remember { mutableStateOf(store.load()) }
    val savedText = stringResource(R.string.settings_saved)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.settings_connection),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            stringResource(R.string.settings_connection_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ----- Protocol mode -----
        DropdownRow(
            label = stringResource(R.string.settings_protocol),
            value = when (options.protocol) {
                ProtocolMode.AUTO -> stringResource(R.string.mode_auto)
                ProtocolMode.CONDUIT -> stringResource(R.string.mode_conduit)
                ProtocolMode.DIRECT -> stringResource(R.string.mode_direct)
                ProtocolMode.CDN_FRONTING -> stringResource(R.string.mode_cdn)
            },
            items = listOf(
                stringResource(R.string.mode_auto),
                stringResource(R.string.mode_conduit),
                stringResource(R.string.mode_direct),
                stringResource(R.string.mode_cdn)
            )
        ) { index ->
            options = options.copy(
                protocol = when (index) {
                    1 -> ProtocolMode.CONDUIT
                    2 -> ProtocolMode.DIRECT
                    3 -> ProtocolMode.CDN_FRONTING
                    else -> ProtocolMode.AUTO
                }
            )
        }

        // ----- Egress region -----
        val regionLabel = if (options.region.isBlank()) {
            stringResource(R.string.region_any)
        } else {
            val region = Region.ALL.firstOrNull { it.code == options.region }
            "${region?.flagEmoji() ?: ""} ${region?.code ?: options.region} - ${region?.name ?: ""}"
        }
        DropdownRow(
            label = stringResource(R.string.settings_region),
            value = regionLabel,
            items = listOf(
                stringResource(R.string.region_any)
            ) + Region.ALL.map { "${it.flagEmoji()} ${it.code} - ${it.name}" }
        ) { index ->
            options = options.copy(
                region = if (index == 0) "" else Region.ALL[index - 1].code
            )
        }

        SwitchRow(
            stringResource(R.string.settings_beast),
            stringResource(R.string.settings_beast_desc),
            options.enableBeastMode
        ) { options = options.copy(enableBeastMode = it) }

        SwitchRow(
            stringResource(R.string.settings_timeout),
            stringResource(R.string.settings_timeout_desc),
            options.enableTimeout
        ) { options = options.copy(enableTimeout = it) }

        SwitchRow(
            stringResource(R.string.settings_lan),
            stringResource(R.string.settings_lan_desc),
            options.enableLan
        ) { options = options.copy(enableLan = it) }

        TextFieldRow(
            stringResource(R.string.settings_upstream),
            stringResource(R.string.settings_upstream_hint),
            options.upstreamProxyUrl
        ) { options = options.copy(upstreamProxyUrl = it) }

        TextFieldRow(
            stringResource(R.string.settings_compartment),
            stringResource(R.string.settings_compartment_desc),
            options.inproxyCompartmentId
        ) { options = options.copy(inproxyCompartmentId = it) }

        Text(
            stringResource(R.string.settings_cdn_group),
            style = MaterialTheme.typography.titleMedium
        )
        TextFieldRow(
            stringResource(R.string.settings_cdn_ips),
            stringResource(R.string.settings_cdn_ips_desc),
            options.cdnFrontingIpList
        ) { options = options.copy(cdnFrontingIpList = it) }
        TextFieldRow(
            stringResource(R.string.settings_cdn_sni),
            stringResource(R.string.settings_cdn_sni_desc),
            options.cdnFrontingSni
        ) { options = options.copy(cdnFrontingSni = it) }

        Text(
            stringResource(R.string.settings_advanced),
            style = MaterialTheme.typography.titleMedium
        )
        TextFieldRow(
            stringResource(R.string.settings_osl),
            stringResource(R.string.settings_osl_desc),
            options.oslUrls
        ) { options = options.copy(oslUrls = it) }

        // ----- Language (Interface group) -----
        Text(
            stringResource(R.string.settings_interface),
            style = MaterialTheme.typography.titleMedium
        )
        val langLabels = listOf(
            stringResource(R.string.lang_system),
            stringResource(R.string.lang_fa),
            stringResource(R.string.lang_en)
        )
        val currentLangIndex = when (store.language) {
            "fa" -> 1
            "en" -> 2
            else -> 0
        }
        DropdownRow(
            label = stringResource(R.string.settings_language),
            value = langLabels[currentLangIndex],
            items = langLabels
        ) { index ->
            store.language = when (index) {
                1 -> "fa"
                2 -> "en"
                else -> "system"
            }
            onLanguageChanged()
        }

        Button(
            onClick = {
                store.save(options)
                onSave(savedText)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_save))
        }

        Card {
            Text(
                stringResource(R.string.settings_note),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun TextFieldRow(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(label: String, value: String, items: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
