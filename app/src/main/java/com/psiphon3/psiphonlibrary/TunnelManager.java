/*
 *
 * Copyright (c) 2019, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3.psiphonlibrary;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.psiphon3.R;
import com.psiphon3.StatusActivity;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

import net.grandcentrix.tray.AppPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import ca.psiphon.PsiphonTunnel;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

public class TunnelManager implements PsiphonTunnel.HostService, MyLog.ILogger {
    // Android IPC messages
    // Client -> Service
    enum ClientToServiceMessage {
        REGISTER,
        UNREGISTER,
        STOP_SERVICE,
        RESTART_SERVICE,
        SET_LANGUAGE,
    }
    // Service -> Client
    enum ServiceToClientMessage {
        KNOWN_SERVER_REGIONS,
        TUNNEL_CONNECTION_STATE,
        DATA_TRANSFER_STATS,
    }

    public static final String INTENT_ACTION_VIEW = "ACTION_VIEW";
    public static final String INTENT_ACTION_HANDSHAKE = "com.psiphon3.psiphonlibrary.TunnelManager.HANDSHAKE";
    public static final String INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE = "com.psiphon3.psiphonlibrary.TunnelManager.SELECTED_REGION_NOT_AVAILABLE";
    public static final String INTENT_ACTION_VPN_REVOKED = "com.psiphon3.psiphonlibrary.TunnelManager.INTENT_ACTION_VPN_REVOKED";

    // Service -> Client bundle parameter names
    static final String DATA_TUNNEL_STATE_IS_RUNNING = "isRunning";
    static final String DATA_TUNNEL_STATE_IS_VPN = "isVpn";
    static final String DATA_TUNNEL_STATE_IS_CONNECTED = "isConnected";
    static final String DATA_TUNNEL_STATE_LISTENING_LOCAL_SOCKS_PROXY_PORT = "listeningLocalSocksProxyPort";
    static final String DATA_TUNNEL_STATE_LISTENING_LOCAL_HTTP_PROXY_PORT = "listeningLocalHttpProxyPort";
    static final String DATA_TUNNEL_STATE_CLIENT_REGION = "clientRegion";
    static final String DATA_TUNNEL_STATE_SPONSOR_ID = "sponsorId";
    public static final String DATA_TUNNEL_STATE_HOME_PAGES = "homePages";
    static final String DATA_TRANSFER_STATS_CONNECTED_TIME = "dataTransferStatsConnectedTime";
    static final String DATA_TRANSFER_STATS_TOTAL_BYTES_SENT = "dataTransferStatsTotalBytesSent";
    static final String DATA_TRANSFER_STATS_TOTAL_BYTES_RECEIVED = "dataTransferStatsTotalBytesReceived";
    static final String DATA_TRANSFER_STATS_SLOW_BUCKETS = "dataTransferStatsSlowBuckets";
    static final String DATA_TRANSFER_STATS_SLOW_BUCKETS_LAST_START_TIME = "dataTransferStatsSlowBucketsLastStartTime";
    static final String DATA_TRANSFER_STATS_FAST_BUCKETS = "dataTransferStatsFastBuckets";
    static final String DATA_TRANSFER_STATS_FAST_BUCKETS_LAST_START_TIME = "dataTransferStatsFastBucketsLastStartTime";

    // Extras in start service intent (Client -> Service)
    static final String DATA_TUNNEL_CONFIG_WHOLE_DEVICE = "tunnelConfigWholeDevice";
    static final String DATA_TUNNEL_CONFIG_EGRESS_REGION = "tunnelConfigEgressRegion";
    static final String DATA_TUNNEL_CONFIG_DISABLE_TIMEOUTS = "tunnelConfigDisableTimeouts";
    static final String EXTRA_LANGUAGE_CODE = "languageCode";

    void updateNotifications() {
        postServiceNotification(false, m_tunnelState.isConnected);
    }

    // Tunnel config, received from the client.
    static class Config {
        boolean wholeDevice = false;
        String egressRegion = PsiphonConstants.REGION_CODE_ANY;
        boolean disableTimeouts = false;
        String sponsorId = EmbeddedValues.SPONSOR_ID;
    }

    private Config m_tunnelConfig = new Config();

    // Shared tunnel state, sent to the client in the HANDSHAKE
    // intent and in the MSG_TUNNEL_CONNECTION_STATE service message.
    public static class State {
        boolean isRunning = false;
        boolean isConnected = false;
        boolean isVPN = false;
        int listeningLocalSocksProxyPort = 0;
        int listeningLocalHttpProxyPort = 0;
        String clientRegion = "";
        String sponsorId = "";
        ArrayList<String> homePages = new ArrayList<>();
    }

    private State m_tunnelState = new State();

    private NotificationManager mNotificationManager = null;
    private NotificationCompat.Builder mNotificationBuilder = null;
    private Service m_parentService;
    private Context m_context;
    private boolean m_firstStart = true;
    private CountDownLatch m_tunnelThreadStopSignal;
    private Thread m_tunnelThread;
    private AtomicBoolean m_isReconnect;
    private final AtomicBoolean m_isStopping;
    private PsiphonTunnel m_tunnel;
    private String m_lastUpstreamProxyErrorMessage;
    private Handler m_Handler = new Handler();

    private PendingIntent m_handshakePendingIntent;
    private PendingIntent m_notificationPendingIntent;
    private PendingIntent m_regionNotAvailablePendingIntent;
    private PendingIntent m_vpnRevokedPendingIntent;

    private ReplaySubject<Boolean> m_tunnelConnectedSubject;
    private CompositeDisposable m_compositeDisposable;

    TunnelManager(Service parentService) {
        m_parentService = parentService;
        m_context = parentService;
        m_isReconnect = new AtomicBoolean(false);
        m_isStopping = new AtomicBoolean(false);
        m_tunnel = PsiphonTunnel.newPsiphonTunnel(this);
        m_tunnelConnectedSubject = ReplaySubject.createWithSize(1);
        m_compositeDisposable = new CompositeDisposable();
    }

    void onCreate() {
        // At this point we've got application context, now we can initialize pending intents.
        m_handshakePendingIntent = getPendingIntent(m_parentService,
                StatusActivity.class,
                INTENT_ACTION_HANDSHAKE);

        m_notificationPendingIntent = getPendingIntent(m_parentService,
                StatusActivity.class,
                INTENT_ACTION_VIEW);

        m_regionNotAvailablePendingIntent = getPendingIntent(m_parentService,
                StatusActivity.class,
                INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE);

        m_vpnRevokedPendingIntent = getPendingIntent(m_parentService,
                StatusActivity.class,
                INTENT_ACTION_VPN_REVOKED);

        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        }

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new NotificationCompat.Builder(getContext());
        }

        m_tunnelState.isVPN = m_parentService instanceof TunnelVpnService;
        m_parentService.startForeground(R.string.psiphon_service_notification_id, createNotification(false, false, m_tunnelState.isVPN));

        m_tunnelState.isRunning = true;
        // This service runs as a separate process, so it needs to initialize embedded values
        EmbeddedValues.initialize(getContext());
        MyLog.setLogger(this);

        m_compositeDisposable.clear();
        m_compositeDisposable.add(connectionStatusUpdaterDisposable());
    }

    // Implementation of android.app.Service.onStartCommand
    int onStartCommand(Intent intent, int flags, int startId) {
        if (m_firstStart && intent != null) {
            getTunnelConfig(intent);
            MyLog.v(R.string.client_version, MyLog.Sensitivity.NOT_SENSITIVE, EmbeddedValues.CLIENT_VERSION);
            m_firstStart = false;
            m_tunnelThreadStopSignal = new CountDownLatch(1);
            m_tunnelThread = new Thread(this::runTunnel);
            m_tunnelThread.start();
        }
        return Service.START_REDELIVER_INTENT;
    }

    IBinder onBind(Intent intent) {
        return m_incomingMessenger.getBinder();
    }

    // Sends handshake intent and tunnel state updates to the client Activity
    // also updates service notification
    private Disposable connectionStatusUpdaterDisposable() {
        return connectionObservable()
                .doOnNext(isConnected -> {
                    m_tunnelState.isConnected = isConnected;
                    // Any subsequent onConnected after this first one will be a reconnect.
                    if(isConnected && m_isReconnect.compareAndSet(false,true)) {
                        sendHandshakeIntent();
                    }
                    sendClientMessage(ServiceToClientMessage.TUNNEL_CONNECTION_STATE.ordinal(), getTunnelStateBundle());
                    // Don't update notification to CONNECTING, etc., when a stop was commanded.
                    if(!m_isStopping.get()) {
                        // We expect only distinct connection status from connectionObservable
                        // which means we always add a sound / vibration alert to the notification
                        postServiceNotification(true, isConnected);
                    }
                })
                .doOnNext(s -> Log.d("HACK", "connectionStatusUpdaterDisposable: isConnected:" + s))
                .subscribe();
    }

    // Implementation of android.app.Service.onDestroy
    void onDestroy() {
        if (mNotificationManager != null) {
            // Only cancel our own service notification, do not cancel _all_ notifications.
            mNotificationManager.cancel(R.string.psiphon_service_notification_id);
        }
        stopAndWaitForTunnel();
        MyLog.unsetLogger();
        m_compositeDisposable.dispose();
    }

    void onRevoke() {
        MyLog.w(R.string.vpn_service_revoked, MyLog.Sensitivity.NOT_SENSITIVE);

        stopAndWaitForTunnel();

        // Foreground client activity with the vpnRevokedPendingIntent in order to notify user.
        try {
            m_vpnRevokedPendingIntent.send(
                    m_parentService, 0, null);
        } catch (PendingIntent.CanceledException e) {
            MyLog.g(String.format("vpnRevokedPendingIntent failed: %s", e.getMessage()));
        }

    }

    private void stopAndWaitForTunnel() {
        if (m_tunnelThread == null) {
            return;
        }

        // signalStopService could have been called, but in case is was not, call here.
        // If signalStopService was not already called, the join may block the calling
        // thread for some time.
        signalStopService();

        try {
            m_tunnelThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        m_tunnelThreadStopSignal = null;
        m_tunnelThread = null;
    }

    // signalStopService signals the runTunnel thread to stop. The thread will
    // self-stop the service. This is the preferred method for stopping the
    // Psiphon tunnel service:
    // 1. VpnService doesn't respond to stopService calls
    // 2. The UI will not block while waiting for stopService to return
    public void signalStopService() {
        if (m_tunnelThreadStopSignal != null) {
            m_tunnelThreadStopSignal.countDown();
        }
    }

    private PendingIntent getPendingIntent(Context ctx, Class activityClass, final String actionString) {
        Intent intent = new Intent(
                actionString,
                null,
                ctx,
                activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                ctx,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void getTunnelConfig(Intent intent) {
        m_tunnelConfig.wholeDevice = intent.getBooleanExtra(
                TunnelManager.DATA_TUNNEL_CONFIG_WHOLE_DEVICE, false);

        m_tunnelConfig.egressRegion = intent.getStringExtra(
                TunnelManager.DATA_TUNNEL_CONFIG_EGRESS_REGION);

        m_tunnelConfig.disableTimeouts = intent.getBooleanExtra(
                TunnelManager.DATA_TUNNEL_CONFIG_DISABLE_TIMEOUTS, false);
    }

    private Notification createNotification(boolean alert, boolean isConnected, boolean isVPN) {
        int contentTextID;
        int iconID;
        CharSequence ticker = null;
        int defaults = 0;

        if (isConnected) {
            if (isVPN) {
                contentTextID = R.string.psiphon_running_whole_device;
            } else {
                contentTextID = R.string.psiphon_running_browser_only;
            }
            iconID = R.drawable.notification_icon_connected;
        } else {
            contentTextID = R.string.psiphon_service_notification_message_connecting;
            ticker = getContext().getText(R.string.psiphon_service_notification_message_connecting);
            iconID = R.drawable.notification_icon_connecting_animation;
        }

        if (alert) {
            final AppPreferences multiProcessPreferences = new AppPreferences(getContext());

            if (multiProcessPreferences.getBoolean(
                    getContext().getString(R.string.preferenceNotificationsWithSound), false)) {
                defaults |= Notification.DEFAULT_SOUND;
            }
            if (multiProcessPreferences.getBoolean(
                    getContext().getString(R.string.preferenceNotificationsWithVibrate), false)) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }
        }

        mNotificationBuilder
                .setSmallIcon(iconID)
                .setContentTitle(getContext().getText(R.string.app_name))
                .setContentText(getContext().getText(contentTextID))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getContext().getText(contentTextID)))
                .setTicker(ticker)
                .setDefaults(defaults)
                .setContentIntent(m_notificationPendingIntent);

        return mNotificationBuilder.build();
    }

    /**
     * Update the context used to get resources with the passed context
     * @param context the new context to use for resources
     */
    void updateContext(Context context) {
        m_context = context;
    }

    private synchronized void postServiceNotification(boolean alert, boolean isConnected) {
        if (mNotificationManager != null) {
            m_Handler.post(new Runnable() {
                @Override
                public void run() {
                    Notification notification = createNotification(alert, isConnected, m_tunnelState.isVPN);
                    mNotificationManager.notify(
                            R.string.psiphon_service_notification_id,
                            notification);
                }
            });
        }
    }

    private boolean isSelectedEgressRegionAvailable(List<String> availableRegions) {
        String selectedEgressRegion = m_tunnelConfig.egressRegion;
        if (selectedEgressRegion == null || selectedEgressRegion.equals(PsiphonConstants.REGION_CODE_ANY)) {
            // User region is either not set or set to 'Best Performance', do nothing
            return true;
        }

        for (String regionCode : availableRegions) {
            if (selectedEgressRegion.equals(regionCode)) {
                return true;
            }
        }
        return false;
    }

    private final Messenger m_incomingMessenger = new Messenger(
            new IncomingMessageHandler(this));
    private Messenger m_outgoingMessenger = null;

    private static class IncomingMessageHandler extends Handler {
        private final WeakReference<TunnelManager> mTunnelManager;
        private final ClientToServiceMessage[] csm = ClientToServiceMessage.values();

        IncomingMessageHandler(TunnelManager manager) {
            mTunnelManager = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            TunnelManager manager = mTunnelManager.get();
            switch (csm[msg.what]) {
                case SET_LANGUAGE:
                    if (manager != null) {
                        Bundle dataBundle = msg.getData();
                        if (dataBundle == null) {
                            return;
                        }
                        String languageCode = dataBundle.getString(EXTRA_LANGUAGE_CODE);
                        LocaleManager localeManager = LocaleManager.getInstance(manager.m_parentService);
                        if (languageCode == null || languageCode.equals("")) {
                            manager.m_context = localeManager.resetToSystemLocale(manager.m_parentService);
                        } else {
                            manager.m_context = localeManager.setNewLocale(manager.m_parentService, languageCode);
                        }
                        manager.updateNotifications();
                    }
                case REGISTER:
                    if(manager != null) {
                        manager.m_outgoingMessenger = msg.replyTo;
                        // respond immediately with current connection state
                        // all following distinct tunnel connection updates will be provided
                        // by an Rx connectionStatusUpdaterDisposable() subscription
                        manager.sendClientMessage(ServiceToClientMessage.TUNNEL_CONNECTION_STATE.ordinal(), manager.getTunnelStateBundle());
                    }
                    break;
                case UNREGISTER:
                    if(manager != null) {
                        // Do not send any more messages after client unregistered.
                        manager.m_outgoingMessenger = null;
                    }
                    break;
                case STOP_SERVICE:
                    if (manager != null) {
                        // Do not send any more messages after a stop was commanded.
                        // Client side will receive a ServiceConnection.onServiceDisconnected callback
                        // when the service finally stops.
                        manager.m_outgoingMessenger = null;
                        manager.signalStopService();
                    }
                    break;

                case RESTART_SERVICE:
                    if (manager != null) {
                        Bundle configBundle = msg.getData();
                        if (configBundle != null) {
                            manager.getTunnelConfig(new Intent().putExtras(configBundle));
                            manager.onRestartCommand();
                        } else {
                            MyLog.g("TunnelManager::handleMessage TunnelManager.RESTART_SERVICE config bundle is null");
                            // It is probably best to stop too.
                            manager.signalStopService();
                        }
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void sendClientMessage(int what, Bundle data) {
        if (m_outgoingMessenger == null) {
            return;
        }
        try {
            Message msg = Message.obtain(null, what);
            if (data != null) {
                msg.setData(data);
            }
            m_outgoingMessenger.send(msg);
        } catch (RemoteException e) {
            // The receiver is dead, do not try to send more messages
            m_outgoingMessenger = null;
        }
    }

    private void sendHandshakeIntent() {
        Intent fillInExtras = new Intent();
        fillInExtras.putExtras(getTunnelStateBundle());
        try {
            m_handshakePendingIntent.send(
                    m_parentService, 0, fillInExtras);
        } catch (PendingIntent.CanceledException e) {
            MyLog.g(String.format("sendHandshakeIntent failed: %s", e.getMessage()));
        }
    }

    private Bundle getTunnelStateBundle() {
        // Update with the latest sponsorId from the tunnel config
        m_tunnelState.sponsorId = m_tunnelConfig.sponsorId;

        Bundle data = new Bundle();
        data.putBoolean(DATA_TUNNEL_STATE_IS_RUNNING, m_tunnelState.isRunning);
        data.putBoolean(DATA_TUNNEL_STATE_IS_VPN, m_tunnelState.isVPN);
        data.putBoolean(DATA_TUNNEL_STATE_IS_CONNECTED, m_tunnelState.isConnected);
        data.putInt(DATA_TUNNEL_STATE_LISTENING_LOCAL_SOCKS_PROXY_PORT, m_tunnelState.listeningLocalSocksProxyPort);
        data.putInt(DATA_TUNNEL_STATE_LISTENING_LOCAL_HTTP_PROXY_PORT, m_tunnelState.listeningLocalHttpProxyPort);
        data.putString(DATA_TUNNEL_STATE_CLIENT_REGION, m_tunnelState.clientRegion);
        data.putString(DATA_TUNNEL_STATE_SPONSOR_ID, m_tunnelState.sponsorId);
        data.putStringArrayList(DATA_TUNNEL_STATE_HOME_PAGES, m_tunnelState.homePages);
        return data;
    }

    private Bundle getDataTransferStatsBundle() {
        Bundle data = new Bundle();
        data.putLong(DATA_TRANSFER_STATS_CONNECTED_TIME, DataTransferStats.getDataTransferStatsForService().m_connectedTime);
        data.putLong(DATA_TRANSFER_STATS_TOTAL_BYTES_SENT, DataTransferStats.getDataTransferStatsForService().m_totalBytesSent);
        data.putLong(DATA_TRANSFER_STATS_TOTAL_BYTES_RECEIVED, DataTransferStats.getDataTransferStatsForService().m_totalBytesReceived);
        data.putParcelableArrayList(DATA_TRANSFER_STATS_SLOW_BUCKETS, DataTransferStats.getDataTransferStatsForService().m_slowBuckets);
        data.putLong(DATA_TRANSFER_STATS_SLOW_BUCKETS_LAST_START_TIME, DataTransferStats.getDataTransferStatsForService().m_slowBucketsLastStartTime);
        data.putParcelableArrayList(DATA_TRANSFER_STATS_FAST_BUCKETS, DataTransferStats.getDataTransferStatsForService().m_fastBuckets);
        data.putLong(DATA_TRANSFER_STATS_FAST_BUCKETS_LAST_START_TIME, DataTransferStats.getDataTransferStatsForService().m_fastBucketsLastStartTime);
        return data;
    }

    private final static String LEGACY_SERVER_ENTRY_FILENAME = "psiphon_server_entries.json";
    private final static int MAX_LEGACY_SERVER_ENTRIES = 100;

    static String getServerEntries(Context context) {
        StringBuilder list = new StringBuilder();

        for (String encodedServerEntry : EmbeddedValues.EMBEDDED_SERVER_LIST) {
            list.append(encodedServerEntry);
            list.append("\n");
        }

        // Import legacy server entries
        try {
            FileInputStream file = context.openFileInput(LEGACY_SERVER_ENTRY_FILENAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(file));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            file.close();
            JSONObject obj = new JSONObject(json.toString());
            JSONArray jsonServerEntries = obj.getJSONArray("serverEntries");

            // MAX_LEGACY_SERVER_ENTRIES ensures the list we pass through to tunnel-core
            // is unlikely to trigger an OutOfMemoryError
            for (int i = 0; i < jsonServerEntries.length() && i < MAX_LEGACY_SERVER_ENTRIES; i++) {
                list.append(jsonServerEntries.getString(i));
                list.append("\n");
            }

            // Don't need to repeat the import again
            context.deleteFile(LEGACY_SERVER_ENTRY_FILENAME);
        } catch (FileNotFoundException e) {
            // pass
        } catch (IOException | JSONException | OutOfMemoryError e) {
            MyLog.g(String.format("prepareServerEntries failed: %s", e.getMessage()));
        }

        return list.toString();
    }

    private Handler sendDataTransferStatsHandler = new Handler();
    private final long sendDataTransferStatsIntervalMs = 1000;
    private Runnable sendDataTransferStats = new Runnable() {
        @Override
        public void run() {
            sendClientMessage(ServiceToClientMessage.DATA_TRANSFER_STATS.ordinal(), getDataTransferStatsBundle());
            sendDataTransferStatsHandler.postDelayed(this, sendDataTransferStatsIntervalMs);
        }
    };

    private Handler periodicMaintenanceHandler = new Handler();
    private final long periodicMaintenanceIntervalMs = 12 * 60 * 60 * 1000;
    private final Runnable periodicMaintenance = new Runnable() {
        @Override
        public void run() {
            LoggingProvider.LogDatabaseHelper.truncateLogs(getContext(), false);
            periodicMaintenanceHandler.postDelayed(this, periodicMaintenanceIntervalMs);
        }
    };

    private void runTunnel() {
        Utils.initializeSecureRandom();

        m_isReconnect.set(false);
        m_isStopping.set(false);

        MyLog.v(R.string.current_network_type, MyLog.Sensitivity.NOT_SENSITIVE, Utils.getNetworkTypeName(m_parentService));

        MyLog.v(R.string.starting_tunnel, MyLog.Sensitivity.NOT_SENSITIVE);

        m_tunnelState.homePages.clear();

        DataTransferStats.getDataTransferStatsForService().startSession();
        sendDataTransferStatsHandler.postDelayed(sendDataTransferStats, sendDataTransferStatsIntervalMs);
        periodicMaintenanceHandler.postDelayed(periodicMaintenance, periodicMaintenanceIntervalMs);

        boolean runVpn =
                m_tunnelConfig.wholeDevice &&
                        Utils.hasVpnService() &&
                        // Guard against trying to start WDM mode when the global option flips while starting a TunnelService
                        (m_parentService instanceof TunnelVpnService);

        try {
            if (runVpn) {
                if (!m_tunnel.startRouting()) {
                    throw new PsiphonTunnel.Exception("application is not prepared or revoked");
                }
                MyLog.v(R.string.vpn_service_running, MyLog.Sensitivity.NOT_SENSITIVE);
            }

            m_tunnel.startTunneling(getServerEntries(m_parentService));

            try {
                m_tunnelThreadStopSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (PsiphonTunnel.Exception e) {
            MyLog.e(R.string.start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, e.getMessage());
        } finally {
            MyLog.v(R.string.stopping_tunnel, MyLog.Sensitivity.NOT_SENSITIVE);

            m_isStopping.set(true);
            m_tunnel.stop();

            periodicMaintenanceHandler.removeCallbacks(periodicMaintenance);
            sendDataTransferStatsHandler.removeCallbacks(sendDataTransferStats);
            DataTransferStats.getDataTransferStatsForService().stop();

            MyLog.v(R.string.stopped_tunnel, MyLog.Sensitivity.NOT_SENSITIVE);

            // Stop service
            m_parentService.stopForeground(true);
            m_parentService.stopSelf();
        }
    }

    private void onRestartCommand() {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                m_isReconnect.set(false);
                try {
                    if(Utils.hasVpnService()
                            && m_parentService instanceof TunnelVpnService
                            && m_tunnelConfig.wholeDevice) {
                        Builder vpnBuilder = ((TunnelVpnService) m_parentService).newBuilder();
                        m_tunnel.seamlessVpnRestart(vpnBuilder);
                    } else if (m_parentService instanceof TunnelService
                            && !m_tunnelConfig.wholeDevice) {
                        m_tunnel.restartPsiphon();
                    } else {
                        // There is a conflict in the restart call, we probably shouldn't keep running.
                        signalStopService();
                        MyLog.g(String.format(Locale.US,
                                "The %s received a restart command when the WDM flag was %s",
                                m_parentService.getClass().getSimpleName(),
                                m_tunnelConfig.wholeDevice ? "on" : "off"));
                    }
                } catch (PsiphonTunnel.Exception e) {
                    MyLog.e(R.string.start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, e.getMessage());
                }
            }
        });
    }

    @Override
    public String getAppName() {
        return m_parentService.getString(R.string.app_name);
    }

    @Override
    public Context getContext() {
        return m_context;
    }

    @Override
    public VpnService getVpnService() {
        return ((TunnelVpnService) m_parentService);
    }

    @Override
    public Builder newVpnServiceBuilder() {
        Builder vpnBuilder = ((TunnelVpnService) m_parentService).newBuilder();
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            final AppPreferences multiProcessPreferences = new AppPreferences(getContext());
            Resources res = getContext().getResources();


            // Check for individual apps to exclude
            String excludedAppsFromPreference = multiProcessPreferences.getString(res.getString(R.string.preferenceExcludeAppsFromVpnString), "");
            List<String> excludedApps;
            if (excludedAppsFromPreference.isEmpty()) {
                excludedApps = Collections.emptyList();
                MyLog.v(R.string.no_apps_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS);
            } else {
                excludedApps = Arrays.asList(excludedAppsFromPreference.split(","));
            }

            if (excludedApps.size() > 0) {
                for (String packageId : excludedApps) {
                    try {
                        vpnBuilder.addDisallowedApplication(packageId);
                        MyLog.v(R.string.individual_app_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, packageId);
                    } catch (PackageManager.NameNotFoundException e) {
                        // Because the list that is passed in to this builder was created by
                        // a PackageManager instance, this exception should never be thrown
                    }
                }
            }
        }

        return vpnBuilder;
    }

    /**
     * Create a tunnel-core config suitable for different tunnel types (i.e., the main Psiphon app
     * tunnel and the UpgradeChecker temp tunnel).
     *
     * @param context
     * @param tunnelConfig         Config values to be set in the tunnel core config.
     * @param tempTunnelName       null if not a temporary tunnel. If set, must be a valid to use in file path.
     * @param clientPlatformPrefix null if not applicable (i.e., for main Psiphon app); should be provided
     *                             for temp tunnels. Will be prepended to standard client platform value.
     * @return JSON string of config. null on error.
     */
    public static String buildTunnelCoreConfig(
            Context context,
            PsiphonTunnel tunnel,
            Config tunnelConfig,
            String tempTunnelName,
            String clientPlatformPrefix) {
        boolean temporaryTunnel = tempTunnelName != null && !tempTunnelName.isEmpty();

        JSONObject json = new JSONObject();

        try {
            String prefix = "";
            if (clientPlatformPrefix != null && !clientPlatformPrefix.isEmpty()) {
                prefix = clientPlatformPrefix;
            }

            String suffix = "";

            // Detect if device is rooted and append to the client_platform string
            if (Utils.isRooted()) {
                suffix += PsiphonConstants.ROOTED;
            }

            // Detect if this is a Play Store build
            if (EmbeddedValues.IS_PLAY_STORE_BUILD) {
                suffix += PsiphonConstants.PLAY_STORE_BUILD;
            }

            tunnel.setClientPlatformAffixes(prefix, suffix);

            json.put("ClientVersion", EmbeddedValues.CLIENT_VERSION);

            if (UpgradeChecker.upgradeCheckNeeded(context)) {

                json.put("UpgradeDownloadURLs", new JSONArray(EmbeddedValues.UPGRADE_URLS_JSON));

                json.put("UpgradeDownloadClientVersionHeader", "x-amz-meta-psiphon-client-version");

                json.put("UpgradeDownloadFilename",
                        new UpgradeManager.DownloadedUpgradeFile(context).getFullPath());
            }

            json.put("PropagationChannelId", EmbeddedValues.PROPAGATION_CHANNEL_ID);

            json.put("SponsorId", tunnelConfig.sponsorId);

            json.put("RemoteServerListURLs", new JSONArray(EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON));

            json.put("ObfuscatedServerListRootURLs", new JSONArray(EmbeddedValues.OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON));

            json.put("RemoteServerListSignaturePublicKey", EmbeddedValues.REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY);

            json.put("UpstreamProxyUrl", UpstreamProxySettings.getUpstreamProxyUrl(context));

            json.put("EmitDiagnosticNotices", true);

            json.put("EmitDiagnosticNetworkParameters", true);

            // If this is a temporary tunnel (like for UpgradeChecker) we need to override some of
            // the implicit config values.
            if (temporaryTunnel) {
                File tempTunnelDir = new File(context.getFilesDir(), tempTunnelName);
                if (!tempTunnelDir.exists()
                        && !tempTunnelDir.mkdirs()) {
                    // Failed to create DB directory
                    return null;
                }

                // On Android, these directories must be set to the app private storage area.
                // The Psiphon library won't be able to use its current working directory
                // and the standard temporary directories do not exist.
                json.put("DataStoreDirectory", tempTunnelDir.getAbsolutePath());

                File remoteServerListDownload = new File(tempTunnelDir, "remote_server_list");
                json.put("RemoteServerListDownloadFilename", remoteServerListDownload.getAbsolutePath());

                File oslDownloadDir = new File(tempTunnelDir, "osl");
                if (!oslDownloadDir.exists()
                        && !oslDownloadDir.mkdirs()) {
                    // Failed to create osl directory
                    // TODO: proceed anyway?
                    return null;
                }
                json.put("ObfuscatedServerListDownloadDirectory", oslDownloadDir.getAbsolutePath());

                // This number is an arbitrary guess at what might be the "best" balance between
                // wake-lock-battery-burning and successful upgrade downloading.
                // Note that the fall-back untunneled upgrade download doesn't start for 30 secs,
                // so we should be waiting longer than that.
                json.put("EstablishTunnelTimeoutSeconds", 300);

                json.put("TunnelWholeDevice", 0);
                json.put("EgressRegion", "");
            } else {
                String egressRegion = tunnelConfig.egressRegion;
                MyLog.g("EgressRegion", "regionCode", egressRegion);
                json.put("EgressRegion", egressRegion);
            }

            if (tunnelConfig.disableTimeouts) {
                //disable timeouts
                MyLog.g("DisableTimeouts", "disableTimeouts", true);
                json.put("NetworkLatencyMultiplier", 3.0);
            } else {
                // TEMP: The default value is too aggressive, it will be adjusted in a future release
                json.put("TunnelPortForwardTimeoutSeconds", 30);
            }

            return json.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    // Creates an observable from ReplaySubject of size(1) that holds the last connection state
    // value. The result is additionally filtered to output only distinct consecutive values.
    // Emits its current value to every new subscriber.
    private Observable<Boolean> connectionObservable() {
        return m_tunnelConnectedSubject
                .hide()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged();
    }

    @Override
    public String getPsiphonConfig() {
        String config = buildTunnelCoreConfig(getContext(), m_tunnel, m_tunnelConfig, null, null);
        return config == null ? "" : config;
    }

    @Override
    public void onDiagnosticMessage(final String message) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.g(message, "msg", message);
            }
        });
    }

    @Override
    public void onAvailableEgressRegions(final List<String> regions) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                // regions are already sorted alphabetically by tunnel core
                new AppPreferences(getContext()).put(RegionAdapter.KNOWN_REGIONS_PREFERENCE, TextUtils.join(",", regions));

                if (!isSelectedEgressRegionAvailable(regions)) {
                    // command service stop
                    signalStopService();

                    // Send REGION_NOT_AVAILABLE intent,
                    // Activity intent handler will show "Region not available" toast and populate
                    // the region selector with new available regions
                    try {
                        m_regionNotAvailablePendingIntent.send(
                                m_parentService, 0, null);
                    } catch (PendingIntent.CanceledException e) {
                        MyLog.g(String.format("regionNotAvailablePendingIntent failed: %s", e.getMessage()));
                    }

                }
                // Notify activity so it has a chance to update region selector values
                sendClientMessage(ServiceToClientMessage.KNOWN_SERVER_REGIONS.ordinal(), null);
            }
        });
    }

    @Override
    public void onSocksProxyPortInUse(final int port) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.e(R.string.socks_port_in_use, MyLog.Sensitivity.NOT_SENSITIVE, port);
                signalStopService();
            }
        });
    }

    @Override
    public void onHttpProxyPortInUse(final int port) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.e(R.string.http_proxy_port_in_use, MyLog.Sensitivity.NOT_SENSITIVE, port);
                signalStopService();
            }
        });
    }

    @Override
    public void onListeningSocksProxyPort(final int port) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.v(R.string.socks_running, MyLog.Sensitivity.NOT_SENSITIVE, port);
                m_tunnelState.listeningLocalSocksProxyPort = port;
            }
        });
    }

    @Override
    public void onListeningHttpProxyPort(final int port) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.v(R.string.http_proxy_running, MyLog.Sensitivity.NOT_SENSITIVE, port);
                m_tunnelState.listeningLocalHttpProxyPort = port;

                final AppPreferences multiProcessPreferences = new AppPreferences(getContext());
                multiProcessPreferences.put(
                        m_parentService.getString(R.string.current_local_http_proxy_port),
                        port);
            }
        });
    }

    @Override
    public void onUpstreamProxyError(final String message) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                // Display the error message only once, and continue trying to connect in
                // case the issue is temporary.
                if (m_lastUpstreamProxyErrorMessage == null || !m_lastUpstreamProxyErrorMessage.equals(message)) {
                    MyLog.v(R.string.upstream_proxy_error, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, message);
                    m_lastUpstreamProxyErrorMessage = message;
                }
            }
        });
    }

    @Override
    public void onConnecting() {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                m_tunnelConnectedSubject.onNext(Boolean.FALSE);
                DataTransferStats.getDataTransferStatsForService().stop();
                m_tunnelState.homePages.clear();

                // Do not log "Connecting" if tunnel is stopping
                if (!m_isStopping.get()) {
                    MyLog.v(R.string.tunnel_connecting, MyLog.Sensitivity.NOT_SENSITIVE);
                }
            }
        });
    }

    @Override
    public void onConnected() {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                DataTransferStats.getDataTransferStatsForService().startConnected();

                MyLog.v(R.string.tunnel_connected, MyLog.Sensitivity.NOT_SENSITIVE);

                m_tunnelConnectedSubject.onNext(Boolean.TRUE);

            }
        });
    }

    @Override
    public void onHomepage(final String url) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                for (String homePage : m_tunnelState.homePages) {
                    if (homePage.equals(url)) {
                        return;
                    }
                }
                m_tunnelState.homePages.add(url);
            }
        });
    }

    @Override
    public void onClientRegion(final String region) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                m_tunnelState.clientRegion = region;
            }
        });
    }

    @Override
    public void onClientUpgradeDownloaded(String filename) {
    }

    @Override
    public void onClientIsLatestVersion() {
    }

    @Override
    public void onSplitTunnelRegion(final String region) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.v(R.string.split_tunnel_region, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, region);
            }
        });
    }

    @Override
    public void onUntunneledAddress(final String address) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.v(R.string.untunneled_address, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, address);
            }
        });
    }

    @Override
    public void onBytesTransferred(final long sent, final long received) {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                DataTransferStats.DataTransferStatsForService stats = DataTransferStats.getDataTransferStatsForService();
                stats.addBytesSent(sent);
                stats.addBytesReceived(received);
            }
        });
    }

    @Override
    public void onStartedWaitingForNetworkConnectivity() {
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                MyLog.v(R.string.waiting_for_network_connectivity, MyLog.Sensitivity.NOT_SENSITIVE);
            }
        });
    }

    @Override
    public void onExiting() {
    }

    @Override
    public void onActiveAuthorizationIDs(List<String> authorizations) {}
}
