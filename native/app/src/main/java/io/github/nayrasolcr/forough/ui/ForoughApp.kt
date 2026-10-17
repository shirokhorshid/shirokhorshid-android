package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.BuildConfig
import io.github.nayrasolcr.forough.CrashLog
import io.github.nayrasolcr.forough.EngineBridge
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class Screen { MAIN, SCANNER, SETTINGS, LOGS, ABOUT }

/**
 * ForoughApp — the Android shell of the desktop window: header bar with
 * the same tool buttons (scanner, speed test, diagnostics, logs, settings,
 * about) over the connection panel + stats panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForoughApp(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onLanguageChanged: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { SettingsStore(context.applicationContext) }

    val stats by ForoughBus.stats.collectAsState()
    val running by ForoughBus.running.collectAsState()

    // Toasts from the engine — the desktop Adw.Toast parity.
    LaunchedEffect(Unit) {
        ForoughBus.toast.collectLatest { message ->
            if (message != null) {
                snackbar.showSnackbar(message)
                ForoughBus.consumeToast()
            }
        }
    }

    val diagCopiedText = stringResource(R.string.diagnostics_copied)
    val speedStartedText = stringResource(R.string.speed_test_running)
    val speedUnavailableText = stringResource(R.string.speed_test_unavailable)
    val cdnUpdatedText = stringResource(R.string.cdn_edges_updated)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.forough_logo_circle),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                },
                actions = {
                    IconButton(onClick = { screen = Screen.SCANNER }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.scanner_title))
                    }
                    IconButton(onClick = {
                        try {
                            val manager = EngineBridge.manager
                            if (manager != null && manager.isRunning) {
                                manager.runSpeedTest(manual = true)
                                scope.launch { snackbar.showSnackbar(speedStartedText) }
                            } else {
                                scope.launch { snackbar.showSnackbar(speedUnavailableText) }
                            }
                        } catch (e: Exception) {
                            ForoughBus.appendLog("speed test error: ${e.message}")
                        }
                    }) {
                        Icon(Icons.Outlined.Speed, contentDescription = stringResource(R.string.speed_test))
                    }
                    IconButton(onClick = {
                        try {
                            val report = EngineBridge.manager?.buildDiagnosticsReport("manual-copy")
                                ?: buildString {
                                    appendLine("Forough Android ${BuildConfig.VERSION_NAME} — tunnel not running")
                                    CrashLog.read(context)?.let { crash ->
                                        appendLine()
                                        appendLine("last crash:")
                                        append(crash)
                                    }
                                }
                            clipboard.setText(AnnotatedString(report))
                            scope.launch { snackbar.showSnackbar(diagCopiedText) }
                        } catch (e: Exception) {
                            ForoughBus.appendLog("diagnostics error: ${e.message}")
                        }
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.diagnostics))
                    }
                    IconButton(onClick = { screen = Screen.SETTINGS }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logs_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Article, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.LOGS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.ABOUT
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // The shell is a NON-scrolling column: each screen owns its own scroll
        // container. A scrollable shell would hand every screen unbounded
        // height constraints, and any lazy list (the logs view) crashes with
        // "Vertically scrollable component was measured with an infinity
        // maximum height constraints" the moment it composes.
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            when (screen) {
                Screen.MAIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ConnectionPanel(
                            stats = stats,
                            running = running,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect
                        )
                        StatsPanel(stats = stats, running = running)
                    }
                }
                Screen.SCANNER -> ScannerScreen(
                    store = store,
                    onToast = { message -> ForoughBus.setToast(message) }
                )
                Screen.SETTINGS -> SettingsScreen(
                    store = store,
                    onSave = { message -> ForoughBus.setToast(message) },
                    onLanguageChanged = onLanguageChanged
                )
                Screen.LOGS -> LogsScreen()
                Screen.ABOUT -> AboutScreen()
            }
        }
    }
}
