/*
 * Copyright (c) 2020, Psiphon Inc.
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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.PublishRelay;
import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

import net.grandcentrix.tray.AppPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import ca.psiphon.PsiphonTunnel;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class TunnelManager implements PsiphonTunnel.HostService {
    // Android IPC messages
    // Client -> Service
    enum ClientToServiceMessage {
        REGISTER,
        UNREGISTER,
        STOP_SERVICE,
        RESTART_SERVICE,
        CHANGED_LOCALE,
    }

    // Service -> Client
    enum ServiceToClientMessage {
        TUNNEL_CONNECTION_STATE,
        DATA_TRANSFER_STATS,
        PING,
    }

    public static final String INTENT_ACTION_VIEW = "ACTION_VIEW";
    public static final String INTENT_ACTION_HANDSHAKE = "com.psiphon3.psiphonlibrary.TunnelManager.HANDSHAKE";
    public static final String INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE = "com.psiphon3.psiphonlibrary.TunnelManager.SELECTED_REGION_NOT_AVAILABLE";
    public static final String INTENT_ACTION_VPN_REVOKED = "com.psiphon3.psiphonlibrary.TunnelManager.INTENT_ACTION_VPN_REVOKED";
    public static final String INTENT_ACTION_STOP_TUNNEL = "com.psiphon3.psiphonlibrary.TunnelManager.ACTION_STOP_TUNNEL";
    public static final String IS_CLIENT_AN_ACTIVITY = "com.psiphon3.psiphonlibrary.TunnelManager.IS_CLIENT_AN_ACTIVITY";

    // Service -> Client bundle parameter names
    static final String DATA_TUNNEL_STATE_IS_RUNNING = "isRunning";
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

    void updateNotifications() {
        postServiceNotification(false, m_tunnelState.isConnected);
    }

    // Tunnel config, received from the client.
    static class Config {
        String egressRegion = PsiphonConstants.REGION_CODE_ANY;
        boolean disableTimeouts = false;
        String sponsorId = EmbeddedValues.SPONSOR_ID;
    }

    private Config m_tunnelConfig;

    private void setTunnelConfig(Config config) {
        m_tunnelConfig = config;
    }

    // Shared tunnel state, sent to the client in the HANDSHAKE
    // intent and in the MSG_TUNNEL_CONNECTION_STATE service message.
    public static class State {
        boolean isRunning = false;
        boolean isConnected = false;
        int listeningLocalSocksProxyPort = 0;
        int listeningLocalHttpProxyPort = 0;
        String clientRegion = "";
        String sponsorId = "";
        ArrayList<String> homePages = new ArrayList<>();
    }

    private State m_tunnelState = new State();

    private NotificationManager mNotificationManager = null;
    private final static String NOTIFICATION_CHANNEL_ID = "psiphon_notification_channel";
    private Service m_parentService;

    private Context m_context;
    private boolean m_firstStart = true;
    private CountDownLatch m_tunnelThreadStopSignal;
    private Thread m_tunnelThread;
    private AtomicBoolean m_startedTunneling;
    private AtomicBoolean m_isReconnect;
    private final AtomicBoolean m_isStopping;
    private PsiphonTunnel m_tunnel;
    private String m_lastUpstreamProxyErrorMessage;
    private Handler m_Handler = new Handler();

    private PendingIntent m_notificationPendingIntent;

    private BehaviorRelay<Boolean> m_tunnelConnectedBehaviorRelay = BehaviorRelay.create();
    private PublishRelay<Object> m_newClientPublishRelay = PublishRelay.create();
    private CompositeDisposable m_compositeDisposable = new CompositeDisposable();
    private ConnectivityManager.NetworkCallback networkCallback;
    private AtomicBoolean m_waitingForConnectivity = new AtomicBoolean(false);
    private VpnAppsUtils.VpnAppsExclusionSetting vpnAppsExclusionSetting = VpnAppsUtils.VpnAppsExclusionSetting.ALL_APPS;
    private int vpnAppsExclusionCount = 0;

    TunnelManager(Service parentService) {
        m_parentService = parentService;
        m_context = parentService;
        m_startedTunneling = new AtomicBoolean(false);
        m_isReconnect = new AtomicBoolean(false);
        m_isStopping = new AtomicBoolean(false);
        // Note that we are requesting manual control over PsiphonTunnel.routeThroughTunnel() functionality.
        m_tunnel = PsiphonTunnel.newPsiphonTunnel(this, false);
    }

    void onCreate() {
        m_notificationPendingIntent = getPendingIntent(m_parentService, INTENT_ACTION_VIEW);

        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID, getContext().getText(R.string.psiphon_service_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW);
                mNotificationManager.createNotificationChannel(notificationChannel);
            }
        }

        m_parentService.startForeground(R.string.psiphon_service_notification_id, createNotification(false, false));

        m_tunnelState.isRunning = true;
        // This service runs as a separate process, so it needs to initialize embedded values
        EmbeddedValues.initialize(getContext());

        m_compositeDisposable.clear();
        m_compositeDisposable.add(connectionStatusUpdaterDisposable());
    }

    // Implementation of android.app.Service.onStartCommand
    int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && INTENT_ACTION_STOP_TUNNEL.equals(intent.getAction())) {
            if(m_tunnelThreadStopSignal == null || m_tunnelThreadStopSignal.getCount() == 0) {
                m_parentService.stopForeground(true);
                m_parentService.stopSelf();
            } else {
                signalStopService();
            }
            return Service.START_NOT_STICKY;
        }

        if (m_firstStart) {
            MyLog.v(R.string.client_version, MyLog.Sensitivity.NOT_SENSITIVE, EmbeddedValues.CLIENT_VERSION);
            m_firstStart = false;
            m_tunnelThreadStopSignal = new CountDownLatch(1);
            m_compositeDisposable.add(
                    getTunnelConfigSingle()
                            .doOnSuccess(config -> {
                                setTunnelConfig(config);
                                m_tunnelThread = new Thread(this::runTunnel);
                                m_tunnelThread.start();
                            })
                            .subscribe());
        }
        return Service.START_REDELIVER_INTENT;
    }

    IBinder onBind(Intent intent) {
        return m_incomingMessenger.getBinder();
    }

    // Sends handshake intent and tunnel state updates to the client Activity,
    // also updates service notification.
    private Disposable connectionStatusUpdaterDisposable() {
        return connectionObservable()
                .switchMapSingle(isConnected -> {
                    // If tunnel is not connected return immediately
                    if (!isConnected) {
                        return Single.just(isConnected);
                    }
                    // If this is a reconnect return immediately
                    if (m_isReconnect.get()) {
                        return Single.just(isConnected);
                    }
                    // If there are no home pages to show return immediately
                    if (m_tunnelState.homePages == null || m_tunnelState.homePages.size() == 0) {
                        return Single.just(isConnected);
                    }
                    // If OS is less than Android 10 return immediately
                    if (Build.VERSION.SDK_INT < 29) {
                        return Single.just(isConnected);
                    }
                    // If there is at least one live activity client, which means there is at least
                    // one activity in foreground bound to the service - return immediately
                    if (pingForActivity()) {
                        return Single.just(isConnected);
                    }
                    // If there are no live client wait for new ones to bind
                    return m_newClientPublishRelay
                            // Test the activity client(s) again by pinging, block until there's at least one live client
                            .filter(__ -> pingForActivity())
                            // We have a live client, complete this inner subscription and send down original isConnected value
                            .map(__ -> isConnected)
                            .firstOrError()
                            // Show "Open Psiphon" notification when subscribed to
                            .doOnSubscribe(__ -> showOpenAppToFinishConnectingNotification())
                            // Cancel "Open Psiphon to keep connecting" when completed or disposed
                            .doFinally(() -> cancelOpenAppToFinishConnectingNotification());
                })
                .doOnNext(isConnected -> {
                    m_tunnelState.isConnected = isConnected;
                    // Any subsequent onConnected after this first one will be a reconnect.
                    if (isConnected && m_isReconnect.compareAndSet(false, true)) {
                        m_tunnel.routeThroughTunnel();
                        if (m_tunnelState.homePages != null && m_tunnelState.homePages.size() > 0) {
                            sendHandshakeIntent();
                        }
                    }
                    sendClientMessage(ServiceToClientMessage.TUNNEL_CONNECTION_STATE.ordinal(), getTunnelStateBundle());
                    // Don't update notification to CONNECTING, etc., when a stop was commanded.
                    if (!m_isStopping.get()) {
                        // We expect only distinct connection status from connectionObservable
                        // which means we always add a sound / vibration alert to the notification
                        postServiceNotification(true, isConnected);
                    }
                })
                .subscribe();
    }

    private void cancelOpenAppToFinishConnectingNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(R.id.notification_id_open_app_to_keep_connecting);
        }
    }

    private void showOpenAppToFinishConnectingNotification() {
        if (mNotificationManager == null) {
            return;
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID);
        notificationBuilder
                .setSmallIcon(R.drawable.ic_psiphon_alert_notification)
                .setContentTitle(getContext().getString(R.string.notification_title_action_required))
                .setContentText(getContext().getString(R.string.notification_text_open_psiphon_to_finish_connecting))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getContext().getString(R.string.notification_text_open_psiphon_to_finish_connecting)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(m_notificationPendingIntent);

        mNotificationManager.notify(R.id.notification_id_open_app_to_keep_connecting, notificationBuilder.build());
    }

    // Implementation of android.app.Service.onDestroy
    void onDestroy() {
        if (mNotificationManager != null) {
            // Only cancel our own service notifications, do not cancel _all_ notifications.
            mNotificationManager.cancel(R.string.psiphon_service_notification_id);
        }
        // Cancel "open app to finish connecting" notifications too.
        cancelOpenAppToFinishConnectingNotification();

        stopAndWaitForTunnel();
        m_compositeDisposable.dispose();
    }

    void onRevoke() {
        MyLog.w(R.string.vpn_service_revoked, MyLog.Sensitivity.NOT_SENSITIVE);

        stopAndWaitForTunnel();
        PendingIntent vpnRevokedPendingIntent = getPendingIntent(m_parentService, INTENT_ACTION_VPN_REVOKED);
        // Try and foreground client activity with the vpnRevokedPendingIntent in order to notify user.
        // If Android < 10 or there is a live activity client then send the intent right away,
        // otherwise show a notification.
        if (Build.VERSION.SDK_INT < 29 || pingForActivity()) {
            try {
                vpnRevokedPendingIntent.send(m_parentService, 0, null);
            } catch (PendingIntent.CanceledException e) {
                MyLog.g(String.format("vpnRevokedPendingIntent failed: %s", e.getMessage()));
            }
        } else {
            if (mNotificationManager == null) {
                return;
            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID);
            notificationBuilder
                    .setSmallIcon(R.drawable.ic_psiphon_alert_notification)
                    .setContentTitle(getContext().getString(R.string.notification_title_vpn_revoked))
                    .setContentText(getContext().getString(R.string.notification_text_vpn_revoked))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(getContext().getString(R.string.notification_text_vpn_revoked)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(vpnRevokedPendingIntent);
            mNotificationManager.notify(R.id.notification_id_vpn_revoked, notificationBuilder.build());
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

    private PendingIntent getPendingIntent(Context ctx, final String actionString) {
        // This comment is copied from MainActivity::HandleCurrentIntent
        //
        // MainActivity is exposed to other apps because it is declared as an entry point activity of the app in the manifest.
        // For the purpose of handling internal intents, such as handshake, etc., from the tunnel service we have declared a not
        // exported activity alias 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler' that should act as a proxy for MainActivity.
        // We expect our own intents have a component set to 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler', all other intents
        // should be ignored.
        Intent intent = new Intent();
        ComponentName intentComponentName = new ComponentName(m_parentService, "com.psiphon3.psiphonlibrary.TunnelIntentsHandler");
        intent.setComponent(intentComponentName);
        intent.setAction(actionString);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return PendingIntent.getActivity(
                ctx,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Single<Config> getTunnelConfigSingle() {
        Single<Config> configSingle = Single.fromCallable(() -> {
            final AppPreferences multiProcessPreferences = new AppPreferences(getContext());
            Config tunnelConfig = new Config();
            tunnelConfig.egressRegion = multiProcessPreferences
                    .getString(getContext().getString(R.string.egressRegionPreference),
                            PsiphonConstants.REGION_CODE_ANY);
            tunnelConfig.disableTimeouts = multiProcessPreferences
                    .getBoolean(getContext().getString(R.string.disableTimeoutsPreference),
                            false);
            return tunnelConfig;
        });

        return configSingle;
    }

    private Notification createNotification(boolean alert, boolean isConnected) {
        int iconID;
        CharSequence contentText;
        CharSequence ticker = null;
        int defaults = 0;

        if (isConnected) {
            iconID = R.drawable.notification_icon_connected;
            switch(vpnAppsExclusionSetting) {
                case INCLUDE_APPS:
                    contentText = getContext().getResources()
                            .getQuantityString(R.plurals.psiphon_service_notification_message_vpn_include_apps,
                                    vpnAppsExclusionCount, vpnAppsExclusionCount);
                    break;
                case EXCLUDE_APPS:
                    contentText = getContext().getResources()
                            .getQuantityString(R.plurals.psiphon_service_notification_message_vpn_exclude_apps,
                                    vpnAppsExclusionCount, vpnAppsExclusionCount);
                    break;
                case ALL_APPS:
                default:
                    contentText = getContext().getString(R.string.psiphon_service_notification_message_vpn_all_apps);
                    break;
            }
        } else {
            iconID = R.drawable.notification_icon_connecting_animation;
            contentText = getContext().getString(R.string.psiphon_service_notification_message_connecting);
            ticker = getContext().getText(R.string.psiphon_service_notification_message_connecting);
        }

        // Only add notification vibration and sound defaults from preferences
        // when user has access to Sound and Vibration in the app's settings.
        if (alert && Utils.supportsNotificationSound()) {
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

        Intent stopTunnelIntent = new Intent(getContext(), m_parentService.getClass());
        stopTunnelIntent.setAction(INTENT_ACTION_STOP_TUNNEL);
        PendingIntent stopTunnelPendingIntent = PendingIntent.getService(getContext(), 0, stopTunnelIntent, 0);
        NotificationCompat.Action notificationAction = new NotificationCompat.Action.Builder(
                        R.drawable.ic_btn_stop,
                        getContext().getString(R.string.stop),
                        stopTunnelPendingIntent)
                .build();


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID);
        return notificationBuilder
                .setSmallIcon(iconID)
                .setContentTitle(getContext().getText(R.string.app_name))
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setTicker(ticker)
                .setDefaults(defaults)
                .setContentIntent(m_notificationPendingIntent)
                .addAction(notificationAction)
                .build();
    }

    /**
     * Update the context used to get resources with the passed context
     *
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
                    Notification notification = createNotification(alert, isConnected);
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

    private static class MessengerWrapper {
        @NonNull Messenger messenger;
        boolean isActivity;
        MessengerWrapper(@NonNull Messenger messenger, Bundle data) {
            this.messenger = messenger;
            if (data!= null) {
                isActivity = data.getBoolean(IS_CLIENT_AN_ACTIVITY, false);
            }
        }

        void send(Message message) throws RemoteException {
            messenger.send(message);
        }
    }

    private final Messenger m_incomingMessenger = new Messenger(
            new IncomingMessageHandler(this));
    private HashMap<Integer, MessengerWrapper> mClients = new HashMap<>();


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
                case REGISTER:
                    if (manager != null) {
                        if (msg.replyTo == null) {
                            MyLog.d("Error registering a client: client's messenger is null.");
                            return;
                        }
                        MessengerWrapper client = new MessengerWrapper(msg.replyTo, msg.getData());
                        // Respond immediately to the new client with current connection state and
                        // data stats. All following distinct tunnel connection updates will be provided
                        // by an Rx connectionStatusUpdaterDisposable() subscription to all clients.
                        List<Message> messageList = new ArrayList<>();
                        messageList.add(manager.composeClientMessage(ServiceToClientMessage.TUNNEL_CONNECTION_STATE.ordinal(),
                                manager.getTunnelStateBundle()));
                        messageList.add(manager.composeClientMessage(ServiceToClientMessage.DATA_TRANSFER_STATS.ordinal(),
                                manager.getDataTransferStatsBundle()));
                        for (Message message : messageList) {
                            try {
                                client.send(message);
                            } catch (RemoteException e) {
                                // Client is dead, do not add it to the clients list
                                return;
                            }
                        }
                        manager.mClients.put(msg.replyTo.hashCode(), client);
                        manager.m_newClientPublishRelay.accept(new Object());
                    }
                    break;

                case UNREGISTER:
                    if (manager != null) {
                        manager.mClients.remove(msg.replyTo.hashCode());
                    }
                    break;

                case STOP_SERVICE:
                    if (manager != null) {
                        // Do not send any more messages after a stop was commanded.
                        // Client side will receive a ServiceConnection.onServiceDisconnected callback
                        // when the service finally stops.
                        manager.mClients.clear();
                        manager.signalStopService();
                    }
                    break;

                case RESTART_SERVICE:
                    if (manager != null) {
                        manager.m_compositeDisposable.add(
                                manager.getTunnelConfigSingle()
                                        .doOnSuccess(config -> {
                                            manager.setTunnelConfig(config);
                                            manager.onRestartCommand();
                                        })
                                        .subscribe());
                    }
                    break;

                case CHANGED_LOCALE:
                    if (manager != null) {
                        setLocale(manager);
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private static void setLocale(TunnelManager manager) {
        LocaleManager localeManager = LocaleManager.getInstance(manager.m_parentService);
        String languageCode = localeManager.getLanguage();
        if (localeManager.isSystemLocale(languageCode)) {
            manager.m_context = localeManager.resetToSystemLocale(manager.m_parentService);
        } else {
            manager.m_context = localeManager.setNewLocale(manager.m_parentService, languageCode);
        }
        manager.updateNotifications();
        // Also update upgrade notifications
        UpgradeManager.UpgradeInstaller.updateNotification(manager.getContext());
    }

    private Message composeClientMessage(int what, Bundle data) {
        Message msg = Message.obtain(null, what);
        if (data != null) {
            msg.setData(data);
        }
        return msg;
    }

    private void sendClientMessage(int what, Bundle data) {
        Message msg = composeClientMessage(what, data);
        for (Iterator i = mClients.entrySet().iterator(); i.hasNext();) {
            Map.Entry pair = (Map.Entry) i.next();
            MessengerWrapper messenger = (MessengerWrapper) pair.getValue();
            try {
                messenger.send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                i.remove();
            }
        }
    }

    private boolean pingForActivity() {
        Message msg = composeClientMessage(ServiceToClientMessage.PING.ordinal(), null);
        for (Map.Entry<Integer, MessengerWrapper> entry : mClients.entrySet()) {
            MessengerWrapper messenger = entry.getValue();
            if (messenger.isActivity) {
                try {
                    messenger.send(msg);
                    return true;
                } catch (RemoteException ignore) {
                }
            }
        }
        return false;
    }

    private void sendHandshakeIntent() {
        Intent fillInExtras = new Intent();
        fillInExtras.putExtras(getTunnelStateBundle());
        PendingIntent handshakePendingIntent = getPendingIntent(m_parentService, INTENT_ACTION_HANDSHAKE);
        try {
            handshakePendingIntent.send(m_parentService, 0, fillInExtras);
        } catch (PendingIntent.CanceledException e) {
            MyLog.g(String.format("sendHandshakeIntent failed: %s", e.getMessage()));
        }
    }

    private Bundle getTunnelStateBundle() {
        // Update with the latest sponsorId from the tunnel config
        m_tunnelState.sponsorId = m_tunnelConfig != null ? m_tunnelConfig.sponsorId : "";

        Bundle data = new Bundle();
        data.putBoolean(DATA_TUNNEL_STATE_IS_RUNNING, m_tunnelState.isRunning);
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

    static String getServerEntries(Context context) {
        StringBuilder list = new StringBuilder();

        for (String encodedServerEntry : EmbeddedValues.EMBEDDED_SERVER_LIST) {
            list.append(encodedServerEntry);
            list.append("\n");
        }

        // Delete legacy server entries if they exist
        context.deleteFile(LEGACY_SERVER_ENTRY_FILENAME);

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
        // Also set locale
        setLocale(this);

        m_isReconnect.set(false);
        m_isStopping.set(false);
        m_startedTunneling.set(false);
        m_tunnelConnectedBehaviorRelay.accept(false);

        // Notify if an upgrade has already been downloaded and is waiting for install
        UpgradeManager.UpgradeInstaller.notifyUpgrade(getContext(), PsiphonTunnel.getDefaultUpgradeDownloadFilePath(getContext()));

        MyLog.v(R.string.current_network_type, MyLog.Sensitivity.NOT_SENSITIVE, Utils.getNetworkTypeName(m_parentService));

        MyLog.v(R.string.starting_tunnel, MyLog.Sensitivity.NOT_SENSITIVE);

        m_tunnelState.homePages.clear();

        DataTransferStats.getDataTransferStatsForService().startSession();
        sendDataTransferStatsHandler.postDelayed(sendDataTransferStats, sendDataTransferStatsIntervalMs);
        periodicMaintenanceHandler.postDelayed(periodicMaintenance, periodicMaintenanceIntervalMs);

        try {
            if (!m_tunnel.startRouting()) {
                throw new PsiphonTunnel.Exception("application is not prepared or revoked");
            }
            MyLog.v(R.string.vpn_service_running, MyLog.Sensitivity.NOT_SENSITIVE);

            m_tunnel.startTunneling(getServerEntries(m_parentService));
            m_startedTunneling.set(true);
            startNetworkStateMonitoring();
            try {
                m_tunnelThreadStopSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (PsiphonTunnel.Exception e) {
            String errorMessage = e.getMessage();
            MyLog.e(R.string.start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, errorMessage);
            if ((errorMessage.startsWith("get package uid:") || errorMessage.startsWith("getPackageUid:"))
                    && errorMessage.endsWith("android.permission.INTERACT_ACROSS_USERS.")) {
                MyLog.v(R.string.vpn_exclusions_conflict, MyLog.Sensitivity.NOT_SENSITIVE);
            }
        } finally {
            MyLog.v(R.string.stopping_tunnel, MyLog.Sensitivity.NOT_SENSITIVE);

            stopNetworkStateMonitoring();
            m_isStopping.set(true);
            m_tunnelConnectedBehaviorRelay.accept(false);
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
                    Builder vpnBuilder = ((TunnelVpnService) m_parentService).newBuilder();
                    m_tunnel.seamlessVpnRestart(vpnBuilder);
                } catch (PsiphonTunnel.Exception e) {
                    MyLog.e(R.string.start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, e.getMessage());
                }
            }
        });
    }

    private void startNetworkStateMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) m_parentService.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return;
            }
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLost(Network network) {
                    if (m_waitingForConnectivity.get()) {
                        // Already waiting for connectivity, do not restart
                        return;
                    }
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    boolean needRestart = networkInfo == null || !networkInfo.isConnected();
                    if (needRestart) {
                        m_Handler.post(() -> {
                            try {
                                m_tunnel.restartPsiphon();
                            } catch (PsiphonTunnel.Exception e) {
                                MyLog.e(R.string.start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, e.getMessage());
                            }
                        });
                    }
                }
            };
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            } catch (RuntimeException e) {
                // Could be a security exception or any other runtime exception on customized firmwares.
                MyLog.g("TunnelManager: failed to register network callback: " + e);
                networkCallback = null;
            }
        }
    }

    private void stopNetworkStateMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (networkCallback == null) {
                return;
            }
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) m_parentService.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return;
            }
            // Note: ConnectivityManager.unregisterNetworkCallback may throw
            // "java.lang.IllegalArgumentException: NetworkCallback was not registered"
            // but this scenario should be handled in the startNetworkStateMonitoring() above.
            //
            // Crash if any other error condition encountered, the service is getting stopped at
            // this point, so it is less of a concern.
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
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
        // only can control tunneling post lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return vpnBuilder;
        }

//        Added on API 29:
//        Marks the VPN network as metered. A VPN network is classified as metered when the user is
//        sensitive to heavy data usage due to monetary costs and/or data limitations. In such cases,
//        you should set this to true so that apps on the system can avoid doing large data transfers.
//        Otherwise, set this to false. Doing so would cause VPN network to inherit its meteredness
//        from its underlying networks.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vpnBuilder.setMetered(false);
        }

        Context context = getContext();
        PackageManager pm = context.getPackageManager();

        switch (VpnAppsUtils.getVpnAppsExclusionMode(context)) {
            case ALL_APPS:
                vpnAppsExclusionSetting = VpnAppsUtils.VpnAppsExclusionSetting.ALL_APPS;
                vpnAppsExclusionCount = 0;
                MyLog.v(R.string.no_apps_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS);
                break;

            case INCLUDE_APPS:
                Set<String> includedApps = VpnAppsUtils.getCurrentAppsIncludedInVpn(context);
                int includedAppsCount = includedApps.size();
                // allow the selected apps
                for (Iterator<String> iterator = includedApps.iterator(); iterator.hasNext(); ) {
                    String packageId = iterator.next();
                    try {
                        // VpnBuilder.addAllowedApplication() is supposed to throw NameNotFoundException
                        // in case the app is no longer available but we observed this is not the case.
                        // Therefore we will perform our own check first.
                        pm.getApplicationInfo(packageId, 0);
                        vpnBuilder.addAllowedApplication(packageId);
                        MyLog.v(R.string.individual_app_included, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, packageId);
                    } catch (PackageManager.NameNotFoundException e) {
                        iterator.remove();
                    }
                }
                // If some packages are no longer installed, updated persisted set
                if (includedAppsCount != includedApps.size()) {
                    VpnAppsUtils.setCurrentAppsToIncludeInVpn(context, includedApps);
                    includedAppsCount = includedApps.size();
                }
                // If we run in this mode and there at least one allowed app then add ourselves too
                if (includedAppsCount > 0) {
                    try {
                        vpnBuilder.addAllowedApplication(context.getPackageName());
                    } catch (PackageManager.NameNotFoundException e) {
                        // this should never be thrown
                    }
                    vpnAppsExclusionSetting = VpnAppsUtils.VpnAppsExclusionSetting.INCLUDE_APPS;
                    vpnAppsExclusionCount = includedAppsCount;
                } else {
                    // There's no included apps, we're tunnelling all
                    vpnAppsExclusionSetting = VpnAppsUtils.VpnAppsExclusionSetting.ALL_APPS;
                    vpnAppsExclusionCount = 0;
                    MyLog.v(R.string.no_apps_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS);
                }
                break;

            case EXCLUDE_APPS:
                Set<String> excludedApps = VpnAppsUtils.getCurrentAppsExcludedFromVpn(context);
                int excludedAppsCount = excludedApps.size();
                // disallow the selected apps
                for (Iterator<String> iterator = excludedApps.iterator(); iterator.hasNext(); ) {
                    String packageId = iterator.next();
                    try {
                        // VpnBuilder.addDisallowedApplication() is supposed to throw NameNotFoundException
                        // in case the app is no longer available but we observed this is not the case.
                        // Therefore we will perform our own check first.
                        pm.getApplicationInfo(packageId, 0);
                        vpnBuilder.addDisallowedApplication(packageId);
                        MyLog.v(R.string.individual_app_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS, packageId);
                    } catch (PackageManager.NameNotFoundException e) {
                        iterator.remove();
                    }
                }
                // If some packages are no longer installed update persisted set
                if (excludedAppsCount != excludedApps.size()) {
                    VpnAppsUtils.setCurrentAppsToExcludeFromVpn(context, excludedApps);
                    excludedAppsCount = excludedApps.size();
                }
                if (excludedAppsCount == 0) {
                    MyLog.v(R.string.no_apps_excluded, MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS);
                }
                vpnAppsExclusionSetting = VpnAppsUtils.VpnAppsExclusionSetting.EXCLUDE_APPS;
                vpnAppsExclusionCount = excludedAppsCount;
                break;
        }

        return vpnBuilder;
    }

    /**
     * Create a tunnel-core config suitable for different tasks (i.e., the main Psiphon app
     * tunnel, the UpgradeChecker temp tunnel and the FeedbackWorker upload operation).
     *
     * @param context
     * @param tunnelConfig         Config values to be set in the tunnel core config.
     * @param useUpstreamProxy     If an upstream proxy has been configured, include it in the returned
     *                             config. Used to omit the proxy from the returned config when network
     *                             operations will already be tunneled over a connection which uses the
     *                             configured upstream proxy.
     * @param tempTunnelName       null if not a temporary tunnel. If set, must be a valid to use in file path.
     * @return JSON string of config. null on error.
     */
    public static String buildTunnelCoreConfig(
            Context context,
            Config tunnelConfig,
            boolean useUpstreamProxy,
            String tempTunnelName) {
        boolean temporaryTunnel = tempTunnelName != null && !tempTunnelName.isEmpty();

        JSONObject json = new JSONObject();

        try {

            json.put("ClientVersion", EmbeddedValues.CLIENT_VERSION);

            if (UpgradeChecker.upgradeCheckNeeded(context)) {

                json.put("UpgradeDownloadURLs", new JSONArray(EmbeddedValues.UPGRADE_URLS_JSON));

                json.put("UpgradeDownloadClientVersionHeader", "x-amz-meta-psiphon-client-version");
            }

            json.put("MigrateUpgradeDownloadFilename",
                    new UpgradeManager.OldDownloadedUpgradeFile(context).getFullPath());

            json.put("PropagationChannelId", EmbeddedValues.PROPAGATION_CHANNEL_ID);

            json.put("SponsorId", tunnelConfig.sponsorId);

            json.put("RemoteServerListURLs", new JSONArray(EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON));

            json.put("ObfuscatedServerListRootURLs", new JSONArray(EmbeddedValues.OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON));

            json.put("RemoteServerListSignaturePublicKey", EmbeddedValues.REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY);

            json.put("ServerEntrySignaturePublicKey", EmbeddedValues.SERVER_ENTRY_SIGNATURE_PUBLIC_KEY);

            json.put("ExchangeObfuscationKey", EmbeddedValues.SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY);

            if (useUpstreamProxy) {
                if (UpstreamProxySettings.getUseHTTPProxy(context)) {
                    if (UpstreamProxySettings.getProxySettings(context) != null) {
                        json.put("UpstreamProxyUrl", UpstreamProxySettings.getUpstreamProxyUrl(context));
                    }
                }
            }

            json.put("EmitDiagnosticNotices", true);

            json.put("EmitDiagnosticNetworkParameters", true);

            json.put("FeedbackUploadURLs", new JSONArray(EmbeddedValues.FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_URLS_JSON));
            json.put("FeedbackEncryptionPublicKey", EmbeddedValues.FEEDBACK_ENCRYPTION_PUBLIC_KEY);

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
                json.put("DataRootDirectory", tempTunnelDir.getAbsolutePath());

                json.put("MigrateDataStoreDirectory", tempTunnelDir.getAbsolutePath());

                File remoteServerListDownload = new File(tempTunnelDir, "remote_server_list");
                json.put("MigrateRemoteServerListDownloadFilename", remoteServerListDownload.getAbsolutePath());

                File oslDownloadDir = new File(tempTunnelDir, "osl");
                if (oslDownloadDir.exists()) {
                    json.put("MigrateObfuscatedServerListDownloadDirectory", oslDownloadDir.getAbsolutePath());
                }

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
                json.put("NetworkLatencyMultiplierLambda", 0.1);
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
        return m_tunnelConnectedBehaviorRelay
                .hide()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged();
    }

    /**
     * Configure tunnel with appropriate client platform affixes (i.e., the main Psiphon app
     * tunnel and the UpgradeChecker temp tunnel).
     *
     * @param tunnel
     * @param clientPlatformPrefix null if not applicable (i.e., for main Psiphon app); should be provided
     *                             for temp tunnels. Will be prepended to standard client platform value.
     */
    static public void setPlatformAffixes(PsiphonTunnel tunnel, String clientPlatformPrefix) {
        String prefix = "";
        if (clientPlatformPrefix != null && !clientPlatformPrefix.isEmpty()) {
            prefix = clientPlatformPrefix;
        }

        String suffix = Utils.getClientPlatformSuffix();

        tunnel.setClientPlatformAffixes(prefix, suffix);
    }

    @Override
    public String getPsiphonConfig() {
        this.setPlatformAffixes(m_tunnel, null);
        String config = buildTunnelCoreConfig(getContext(), m_tunnelConfig, true, null);
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
                AppPreferences mp = new AppPreferences(getContext());
                mp.put(RegionListPreference.KNOWN_REGIONS_PREFERENCE, TextUtils.join(",", regions));

                if (!isSelectedEgressRegionAvailable(regions)) {
                    // command service stop
                    signalStopService();

                    // Set region preference to PsiphonConstants.REGION_CODE_ANY
                    mp.put(m_parentService.getString(R.string.egressRegionPreference), PsiphonConstants.REGION_CODE_ANY);

                    // Send REGION_NOT_AVAILABLE intent,
                    // Activity intent handler will show "Region not available" toast and populate
                    // the region selector with new available regions
                    PendingIntent regionNotAvailablePendingIntent = getPendingIntent(m_parentService, INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE);

                    // If Android < 10 or there is a live activity client then send the intent right away,
                    // otherwise show a notification.
                    if (Build.VERSION.SDK_INT < 29 || pingForActivity()) {
                        try {
                            regionNotAvailablePendingIntent.send(m_parentService, 0, null);
                        } catch (PendingIntent.CanceledException e) {
                            MyLog.g(String.format("regionNotAvailablePendingIntent failed: %s", e.getMessage()));
                        }
                    } else {
                        if (mNotificationManager == null) {
                            return;
                        }

                        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext(), NOTIFICATION_CHANNEL_ID);
                        notificationBuilder
                                .setSmallIcon(R.drawable.ic_psiphon_alert_notification)
                                .setContentTitle(getContext().getString(R.string.notification_title_region_not_available))
                                .setContentText(getContext().getString(R.string.notification_text_region_not_available))
                                .setStyle(new NotificationCompat.BigTextStyle()
                                        .bigText(getContext().getString(R.string.notification_text_region_not_available)))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)
                                .setContentIntent(regionNotAvailablePendingIntent);
                        mNotificationManager.notify(R.id.notification_id_region_not_available, notificationBuilder.build());
                    }
                }
                // UPDATE:
                // The region list preference view is created with the stored known regions list every time
                // before presenting, there is no need to notify the activity of the data change anymore.
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
                m_tunnelConnectedBehaviorRelay.accept(false);
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

                m_tunnelConnectedBehaviorRelay.accept(true);
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
        m_Handler.post(new Runnable() {
            @Override
            public void run() {
                UpgradeManager.UpgradeInstaller.notifyUpgrade(getContext(), filename);
            }
        });
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

                sendClientMessage(ServiceToClientMessage.TUNNEL_CONNECTION_STATE.ordinal(), getTunnelStateBundle());
                m_waitingForConnectivity.set(true);
            }
        });
    }

    @Override
    public void onStoppedWaitingForNetworkConnectivity() {
        m_waitingForConnectivity.set(false);
    }
}
