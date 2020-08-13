package com.psiphon3;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.jakewharton.rxrelay2.PublishRelay;
import com.psiphon3.psiphonlibrary.LoggingProvider;
import com.psiphon3.psiphonlibrary.TunnelServiceInteractor;
import com.psiphon3.psiphonlibrary.UpstreamProxySettings;
import com.psiphon3.psiphonlibrary.Utils;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public class MainActivityViewModel extends AndroidViewModel implements LifecycleObserver {
    private final TunnelServiceInteractor tunnelServiceInteractor;
    private final PublishRelay<Boolean> customProxyValidationResultRelay = PublishRelay.create();
    private final Context context;
    private final PublishRelay<Object> availableRegionsSelectionRelay = PublishRelay.create();
    private final PublishRelay<Object> openVpnSettingsRelay = PublishRelay.create();
    private PublishRelay<String> externalBrowserUrlRelay = PublishRelay.create();
    private PublishRelay<String> lastLogEntryRelay = PublishRelay.create();
    private boolean isFirstRun = true;

    public MainActivityViewModel(@NonNull Application application) {
        super(application);
        context = application.getApplicationContext();
        tunnelServiceInteractor = new TunnelServiceInteractor(application.getApplicationContext());
        Utils.MyLog.setLogger(() -> context);

        // remove logs from previous sessions if tunnel service is not running.
        if (!tunnelServiceInteractor.isServiceRunning(application.getApplicationContext())) {
            LoggingProvider.LogDatabaseHelper.truncateLogs(context, true);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    protected void onLifeCycleStop() {
        tunnelServiceInteractor.onStop(getApplication());
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    protected void onLifeCycleStart() {
        tunnelServiceInteractor.onStart(getApplication());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Utils.MyLog.unsetLogger();
        tunnelServiceInteractor.onDestroy(context);
    }

    public Flowable<TunnelState> tunnelStateFlowable() {
        return tunnelServiceInteractor.tunnelStateFlowable();
    }

    public Flowable<Boolean> dataStatsFlowable() {
        return tunnelServiceInteractor.dataStatsFlowable();
    }

    public void stopTunnelService() {
        tunnelServiceInteractor.stopTunnelService();
    }

    public void startTunnelService() {
        tunnelServiceInteractor.startTunnelService(context);
    }

    public void restartTunnelService() {
        tunnelServiceInteractor.scheduleRunningTunnelServiceRestart(context);
    }

    // Basic check of proxy settings values
    public boolean validateCustomProxySettings() {
        boolean useHTTPProxyPreference = UpstreamProxySettings.getUseHTTPProxy(context);
        boolean useCustomProxySettingsPreference = UpstreamProxySettings.getUseCustomProxySettings(context);

        if (!useHTTPProxyPreference ||
                !useCustomProxySettingsPreference) {
            return true;
        }
        UpstreamProxySettings.ProxySettings proxySettings = UpstreamProxySettings.getProxySettings(context);
        boolean isValid = proxySettings != null &&
                proxySettings.proxyHost.length() > 0 &&
                proxySettings.proxyPort >= 1 &&
                proxySettings.proxyPort <= 65535;

        customProxyValidationResultRelay.accept(isValid);

        return isValid;
    }

    public Flowable<Boolean> customProxyValidationResultFlowable() {
        return customProxyValidationResultRelay.toFlowable(BackpressureStrategy.LATEST);
    }

    public void signalAvailableRegionsUpdate() {
        availableRegionsSelectionRelay.accept(new Object());
    }

    public Flowable<Object> updateAvailableRegionsFlowable() {
        return availableRegionsSelectionRelay.toFlowable(BackpressureStrategy.LATEST);
    }

    public void signalOpenVpnSettings() {
        openVpnSettingsRelay.accept(new Object());
    }

    public Flowable<Object> openVpnSettingsFlowable() {
        return openVpnSettingsRelay.toFlowable(BackpressureStrategy.LATEST);
    }

    public void signalExternalBrowserUrl(String url) {
        externalBrowserUrlRelay.accept(url);
    }

    public Flowable<String> externalBrowserUrlFlowable() {
        return externalBrowserUrlRelay.toFlowable(BackpressureStrategy.LATEST);
    }

    public void signalLastLogEntryAdded(String log) {
        lastLogEntryRelay.accept(log);
    }

    public Flowable<String> lastLogEntryFlowable() {
        return lastLogEntryRelay.toFlowable(BackpressureStrategy.LATEST);
    }

    public boolean isFirstRun() {
        return isFirstRun;
    }

    public void setFirstRun(boolean firstRun) {
        isFirstRun = firstRun;
    }
}
