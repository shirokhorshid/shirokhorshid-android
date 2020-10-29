/*
 * Copyright (c) 2016, Psiphon Inc.
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import android.util.Log;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

import net.grandcentrix.tray.AppPreferences;

import java.io.File;
import java.util.Date;

import ca.psiphon.PsiphonTunnel;

/*
 * Self-upgrading notes.
 * - UpgradeChecker is responsible for processing downloaded upgrade files (authenticate package,
 *   check APK version -- via UpgradeManager), notifying users of upgrades, and invoking the OS installer. Only
 *   UpgradeChecker will do these things, so we ensure there’s only one upgrade notification, etc.
 * - Every X hours, an alarm will wake up UpgradeChecker and it will launch its own tunnel-core and
 *   download an upgrade if no upgrade is pending. This achieves the Google Play-like
 *   upgrade-when-not-running.
 * - The Psiphon app tunnel-core will also download upgrades, if no upgrade is pending. It will make
 *   an untunneled check when it can’t connect. Or it will download when handshake indicates an
 *   upgrade is available.
 * - An upgrade is pending if a valid upgrade has been downloaded and is awaiting install. Both
 *   tunnel-cores need to be configured to skip upgrades when there’s a pending upgrade. In fact,
 *   the UpgradeChecker tunnel-core need not be started at all in this case.
 * - When the Psiphon app tunnel-core downloads an upgrade, it notifies UpgradeChecker with an
 *   intent. UpgradeChecker takes ownership of the downloaded file and proceeds as if it downloaded
 *   the file.
 *     - Because the app tunnel-core and UpgradeChecker download to the same filename, there's a
 *       race condition to access the files -- partial download file, unverified file, verified file.
 *       We will rely on file locking and package verification to keep the files sane. There is a
 *       very tiny chance that a file could get deleted right before it's replaced, causing an error
 *       when the user clicks the notification, but very tiny.
 */

public class UpgradeChecker extends BroadcastReceiver {
    private static final int ALARM_INTENT_REQUEST_CODE = 0;
    private static final String ALARM_INTENT_ACTION = UpgradeChecker.class.getName()+":ALARM";
    private static final String CREATE_ALARM_INTENT_ACTION = UpgradeChecker.class.getName()+":CREATE_ALARM";

    public static final String UPGRADE_FILE_AVAILABLE_INTENT_ACTION = UpgradeChecker.class.getName()+":UPGRADE_AVAILABLE";
    public static final String UPGRADE_FILE_AVAILABLE_INTENT_EXTRA_FILENAME = UpgradeChecker.class.getName()+":UPGRADE_FILENAME";

    /**
     * Provides loggging functionality to the :UpgradeChecker process. Utilizes LoggingProvider.
     * May be called from any process or thread.
     * @param context
     * @param stringResID String resource ID.
     * @param sensitivity Log sensitivity level.
     * @param priority One of the log priority levels supported by MyLog. Like: Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.VERBOSE
     * @param formatArgs Arguments to be formatted into the log string.
     */
    private static void log(Context context, int stringResID, MyLog.Sensitivity sensitivity, int priority, Object... formatArgs) {

        /*
        //TODO: use MyLog?
        if(!MyLog.isSetLogger()) {
            final Context loggerCtx = context;
            MyLog.ILogger logger = new MyLog.ILogger() {
                @Override
                public Context getContext() {
                    return loggerCtx;
                }
            };
            MyLog.setLogger(logger);
        }
        */

        String logJSON = LoggingProvider.makeStatusLogJSON(
                context,
                new Date(),
                stringResID,
                sensitivity,
                formatArgs,
                priority);
        if (logJSON == null) {
            // Fail silently
            return;
        }

        ContentValues values = new ContentValues();
        values.put(LoggingProvider.LogDatabaseHelper.COLUMN_NAME_LOGJSON, logJSON);
        values.put(LoggingProvider.LogDatabaseHelper.COLUMN_NAME_IS_DIAGNOSTIC, false);

        context.getContentResolver().insert(
                LoggingProvider.INSERT_URI,
                values
        );
    }

    /**
     * Checks whether an upgrade check should be performed. False will be returned if there's already
     * an upgrade file downloaded.
     * May be called from any process or thread.
     * Side-effect: If an existing upgrade file is detected, the upgrade notification will be displayed.
     * Side-effect: Creates the UpgradeChecker alarm.
     * @param context the context
     * @return true if upgrade check is needed.
     */
    public static boolean upgradeCheckNeeded(Context context) {
        Context appContext = context.getApplicationContext();

        // The main process will call this when it tries to connect, so we will use this opportunity
        // to make sure our alarm is created.
        createAlarm(appContext);

        // Don't re-download the upgrade package when a verified upgrade file is
        // awaiting application by the user. A previous upgrade download will have
        // completed and have been extracted to this verified upgrade file.
        // Without this check, tunnel-core won't know that the upgrade is already
        // downloaded, as the file name differs from UpgradeDownloadFilename, and
        // so the entire upgrade will be re-downloaded on each tunnel connect until
        // the user actually applies the upgrade.
        // As a result of this check, a user that delays applying an upgrade until
        // after a subsequent upgrade is released will first apply a stale upgrade
        // and then download the next upgrade.
        // Note: depends on getAvailableCompleteUpgradeFile deleting VerifiedUpgradeFile
        // after upgrade is complete. Otherwise, no further upgrades would download.
        // TODO: implement version tracking for the verified upgrade file so that
        // we can proceed with downloading a newer upgrade when an outdated upgrade exists
        // on disk.

        if (!allowedToSelfUpgrade(context)) {
            log(context, R.string.upgrade_checker_no_upgrading, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            return false;
        }

        File downloadedUpgradeFile = new File(PsiphonTunnel.getDefaultUpgradeDownloadFilePath(appContext));

        if (UpgradeManager.UpgradeInstaller.upgradeFileAvailable(appContext, downloadedUpgradeFile)) {
            log(context, R.string.upgrade_checker_upgrade_file_exists, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            // We know there's an upgrade file available, so send an intent about it.
            Intent intent = new Intent(appContext, UpgradeChecker.class);
            intent.setAction(UPGRADE_FILE_AVAILABLE_INTENT_ACTION);
            intent.putExtra(UPGRADE_FILE_AVAILABLE_INTENT_EXTRA_FILENAME, downloadedUpgradeFile.getName());
            appContext.sendBroadcast(intent);
            return false;
        }

        // Verify if 'Download upgrades on WiFi only' user preference is on
        // but current network is not WiFi
        final AppPreferences multiProcessPreferences = new AppPreferences(appContext);
        if (multiProcessPreferences.getBoolean(
                context.getString(R.string.downloadWifiOnlyPreference), PsiphonConstants.DOWNLOAD_WIFI_ONLY_PREFERENCE_DEFAULT) &&
                !Utils.isOnWiFi(appContext)) {
            log(context, R.string.upgrade_checker_upgrade_wifi_only, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            return false;
        }

        log(appContext, R.string.upgrade_checker_check_needed, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);

        return true;
    }

    /**
     * Checks if the current app installation is allowed to upgrade itself.
     * @param appContext The application context.
     * @return true if the app is allowed to self-upgrade, false otherwise.
     */
    private static boolean allowedToSelfUpgrade(Context appContext) {
        if (EmbeddedValues.UPGRADE_URLS_JSON.length() == "[]".length()) {
            // We don't know where to find an upgrade.
            return false;
        }
        else if (EmbeddedValues.IS_PLAY_STORE_BUILD) {
            // Play Store Build instances must not use custom auto-upgrade, as it's a ToS violation.
            return false;
        }

        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // This service runs as a separate process, so it needs to initialize embedded values
        EmbeddedValues.initialize(context);

        // Make sure the alarm is created, regardless of which intent we received.
        createAlarm(context.getApplicationContext());

        String action = intent.getAction();

        if (action.equals(ALARM_INTENT_ACTION)) {
            log(context, R.string.upgrade_checker_alarm_intent_received, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            if (!upgradeCheckNeeded(context)) {
                return;
            }
            checkForUpgrade(context);
        }
        else if (action.equals(UPGRADE_FILE_AVAILABLE_INTENT_ACTION)) {
            log(context, R.string.upgrade_checker_upgrade_file_available_intent_received, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            // Create upgrade notification. User clicking the notification will trigger the install.
            String filename = intent.getStringExtra(UPGRADE_FILE_AVAILABLE_INTENT_EXTRA_FILENAME);
            UpgradeManager.UpgradeInstaller.notifyUpgrade(LocaleManager.getInstance(context).setLocale(context), filename);
        }
        else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            log(context, R.string.upgrade_checker_boot_completed_intent_received, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            // Pass. We created the alarm above, so nothing else to do (until the alarm runs).
        }
        else if (action.equals(CREATE_ALARM_INTENT_ACTION)) {
            log(context, R.string.upgrade_checker_create_alarm_intent_received, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            // Pass. We created the alarm above, so nothing else to do (until the alarm runs).
        }
    }

    /**
     * Creates the periodic alarm used to check for updates. Can be called unconditionally; it
     * handles cases when the alarm is already created.
     * @param appContext The application context.
     */
    private static void createAlarm(Context appContext) {
        if (!allowedToSelfUpgrade(appContext)) {
            // Don't waste resources with an alarm if we can't possibly self-upgrade.
            log(appContext, R.string.upgrade_checker_no_alarm_no_selfupgrading, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            return;
        }

        Intent intent = new Intent(appContext, UpgradeChecker.class);
        intent.setAction(ALARM_INTENT_ACTION);

        boolean alarmExists = (PendingIntent.getBroadcast(
                appContext,
                ALARM_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE) != null);

        if (alarmExists) {
            log(appContext, R.string.upgrade_checker_alarm_exists, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            return;
        }

        log(appContext, R.string.upgrade_checker_creating_alarm, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);

        PendingIntent alarmIntent = PendingIntent.getBroadcast(
                appContext,
                ALARM_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmMgr = (AlarmManager)appContext.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_HALF_DAY,
                alarmIntent);
    }

    /**
     * Launches the upgrade checking service. Returns immediately.
     */
    private void checkForUpgrade(Context context) {
        log(context, R.string.upgrade_checker_start_service, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
        UpgradeCheckerService.enqueueWork(context);
    }


    /**
     * The service that does the upgrade checking, via tunnel-core.
     */
    public static class UpgradeCheckerService extends JobIntentService implements PsiphonTunnel.HostService {
        private static final int JOB_ID = 10029;
        /**
         * The tunnel-core instance.
         */
        private PsiphonTunnel mTunnel = PsiphonTunnel.newPsiphonTunnel(this);

        /**
         * Keep track if the upgrade check is already in progress
         */
        private boolean mUpgradeCheckInProgress = false;

        /**
         * Used to post back to stop the tunnel, to avoid locking the thread.
         */
        Handler mStopHandler = new Handler();

        /**
         * Used to keep track of whether we've already sent the intent indicating that the
         * upgrade is available.
         */
        private boolean mUpgradeDownloaded;

        /**
         * Convenience method for enqueuing work in to this service.
         */
        public static void enqueueWork(Context context) {
            enqueueWork(context, UpgradeCheckerService.class, JOB_ID, new Intent());
        }

        /**
         * Entry point for starting the upgrade service.
         * @param intent Intent passed to enqueueWork, ignored.
         */
        @Override
        protected void onHandleWork(@NonNull Intent intent) {
            log(this, R.string.upgrade_checker_check_start, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);

            if (mUpgradeCheckInProgress) {
                // A check is already in progress, log and return
                log(this, R.string.upgrade_checker_already_in_progress, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
                // Not calling shutDownTunnel() because we don't want to interfere with the currently running request.
                return;
            }

            mUpgradeDownloaded = false;
            mUpgradeCheckInProgress = true;

            Utils.initializeSecureRandom();

            try {
                mTunnel.startTunneling(TunnelManager.getServerEntries(this));
            } catch (PsiphonTunnel.Exception e) {
                log(this, R.string.upgrade_checker_start_tunnel_failed, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN, e.getMessage());
                // No need to call shutDownTunnel().
                mUpgradeCheckInProgress = false;
                stopSelf();
            }
        }

        /**
         * Called when tunnel-core upgrade processing is finished (one way or another).
         * May be called more than once.
         */
        protected void shutDownTunnel() {
            final Context context = this;
            mStopHandler.post(new Runnable() {
                @Override
                public void run() {
                    log(context, R.string.upgrade_checker_done, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
                    mTunnel.stop();
                }
            });
        }

        /*
         * PsiphonTunnel.HostService implementation
         */

        @Override
        public String getPsiphonConfig() {
            // Build a temporary tunnel config to use
            TunnelManager.Config tunnelManagerConfig = new TunnelManager.Config();
            final AppPreferences multiProcessPreferences = new AppPreferences(this);
            tunnelManagerConfig.disableTimeouts = multiProcessPreferences.getBoolean(
                    this.getString(R.string.disableTimeoutsPreference), false);

            TunnelManager.setPlatformAffixes(mTunnel, "Psiphon_UpgradeChecker_");

            String tunnelCoreConfig = TunnelManager.buildTunnelCoreConfig(
                    this,                       // context
                    tunnelManagerConfig,
                    true,
                    "upgradechecker");           // tempTunnelName
            return tunnelCoreConfig == null ? "" : tunnelCoreConfig;
        }

        /**
         * Called when the tunnel discovers that we're already on the latest version. This indicates
         * that we can start shutting down.
         */
        @Override
        public void onClientIsLatestVersion() {
            log(this, R.string.upgrade_checker_client_is_latest_version, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            shutDownTunnel();
        }

        /**
         * Called when the tunnel discovers that an upgrade has been downloaded. This indicates that
         * we should send an intent about it and start shutting down.
         */
        @Override
        public void onClientUpgradeDownloaded(String filename) {
            log(this, R.string.upgrade_checker_client_upgrade_downloaded, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);

            if (mUpgradeDownloaded) {
                // Because tunnel-core may create multiple server connections and do multiple
                // handshakes, onClientUpgradeDownloaded may get called multiple times.
                // We want to avoid sending the intent each time.
                return;
            }
            mUpgradeDownloaded = true;

            Intent intent = new Intent(this, UpgradeChecker.class);
            intent.setAction(UPGRADE_FILE_AVAILABLE_INTENT_ACTION);
            intent.putExtra(UPGRADE_FILE_AVAILABLE_INTENT_EXTRA_FILENAME, filename);
            this.sendBroadcast(intent);

            shutDownTunnel();
        }

        /**
         * Called when the tunnel has finished shutting down. We're all done and can shut down the JobIntentService
         * May be due to a connection timeout, or simply an exit triggered by one of the shutDownTunnel() calls.
         */
        @Override
        public void onExiting() {
            log(this, R.string.upgrade_checker_tunnel_exiting, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN);
            stopSelf();
        }

        @Override
        public void onDiagnosticMessage(String message) {
            log(this, R.string.upgrade_checker_tunnel_diagnostic_message, MyLog.Sensitivity.NOT_SENSITIVE, Log.WARN, message);
        }

        @Override
        public String getAppName() {
            return getString(R.string.app_name);
        }

        @Override
        public Context getContext() {
            return this;
        }
    }
}
