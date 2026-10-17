package io.github.nayrasolcr.forough.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.MainActivity
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.ForoughTunnelManager
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Whole-device VPN service — the Android counterpart of the desktop client's
 * sing-box TUN layer. Runs the ForoughTunnelManager engine and keeps a
 * foreground notification with the live status (connect / disconnect
 * actions, like the desktop's header controls).
 */
class ForoughVpnService : VpnService() {

    private var manager: ForoughTunnelManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        manager = ForoughTunnelManager(applicationContext)

        scope.launch {
            ForoughBus.stats.collect { stats ->
                if (instance != null) {
                    updateNotification(stats.status, stats.tunnelProtocol, stats.egressRegion)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                manager?.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startInForeground(getString(R.string.notif_connecting))
                val options = SettingsStore(applicationContext).load()
                manager?.start(options)
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        // The user (or system) revoked VPN consent — tear down honestly,
        // like the desktop client's fallback_disconnect.
        ForoughBus.appendLog("VPN permission revoked — tunnel stopped")
        manager?.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String, protocol: String, region: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText(status), protocol, region))
    }

    private fun buildNotification(text: String): Notification = buildNotification(text, "-", "-")

    private fun buildNotification(text: String, protocol: String, region: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ForoughVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (region != "-" && region.isNotBlank()) {
            "$text · $region · $protocol"
        } else text

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forough)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notif_disconnect), disconnectIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun statusText(status: String): String = when (status) {
        "connected" -> getString(R.string.notif_connected)
        "connecting" -> getString(R.string.notif_connecting)
        "reconnecting" -> getString(R.string.notif_reconnecting)
        "exiting" -> getString(R.string.notif_disconnecting)
        else -> getString(R.string.notif_disconnected)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notif_channel_desc)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_CONNECT = "io.github.nayrasolcr.forough.CONNECT"
        const val ACTION_DISCONNECT = "io.github.nayrasolcr.forough.DISCONNECT"
        private const val CHANNEL_ID = "forough_tunnel"
        private const val NOTIFICATION_ID = 1001

        @Volatile var instance: ForoughVpnService? = null
    }
}
