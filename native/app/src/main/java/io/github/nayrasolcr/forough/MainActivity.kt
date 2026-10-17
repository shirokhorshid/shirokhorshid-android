package io.github.nayrasolcr.forough

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.nayrasolcr.forough.service.ForoughVpnService
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import io.github.nayrasolcr.forough.ui.ForoughApp
import io.github.nayrasolcr.forough.ui.theme.ForoughTheme

class MainActivity : ComponentActivity() {

    private var pendingConnect = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingConnect) {
            pendingConnect = false
            sendConnectIntent()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        val pref = SettingsStore(newBase).language
        super.attachBaseContext(LocaleHelper.wrap(newBase, pref))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            ForoughTheme {
                ForoughApp(
                    onConnect = { requestVpnAndConnect() },
                    onDisconnect = { sendDisconnectIntent() },
                    onLanguageChanged = { recreate() }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestVpnAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingConnect = true
            vpnPermissionLauncher.launch(intent)
        } else {
            sendConnectIntent()
        }
    }

    private fun sendConnectIntent() {
        val intent = Intent(this, ForoughVpnService::class.java)
            .setAction(ForoughVpnService.ACTION_CONNECT)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendDisconnectIntent() {
        val intent = Intent(this, ForoughVpnService::class.java)
            .setAction(ForoughVpnService.ACTION_DISCONNECT)
        ContextCompat.startForegroundService(this, intent)
    }
}
