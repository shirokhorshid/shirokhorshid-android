package com.psiphon3;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import com.psiphon3.psiphonlibrary.LoggingObserver;
import com.psiphon3.psiphonlibrary.LoggingProvider;
import com.psiphon3.psiphonlibrary.TunnelManager;
import com.psiphon3.psiphonlibrary.Utils;
import com.psiphon3.psiphonlibrary.VpnAppsUtils;

import net.grandcentrix.tray.AppPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class MainActivity extends LocalizedActivities.AppCompatActivity {
    public MainActivity() {
        Utils.initializeSecureRandom();
    }

    public static final String INTENT_EXTRA_PREVENT_AUTO_START = "com.psiphon3.MainActivity.PREVENT_AUTO_START";
    private static final String CURRENT_TAB = "currentTab";
    private static final String BANNER_FILE_NAME = "bannerImage";

    private static final int REQUEST_CODE_PREPARE_VPN = 100;

    private LoggingObserver loggingObserver;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private CompositeDisposable uiUpdatesCompositeDisposable;
    private Button toggleButton;
    private ProgressBar connectionProgressBar;
    private Button openBrowserButton;
    private MainActivityViewModel viewModel;
    private Toast invalidProxySettingsToast;
    private AppPreferences multiProcessPreferences;
    private ViewPager viewPager;
    private PsiphonTabLayout tabLayout;
    private ImageView banner;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        EmbeddedValues.initialize(getApplicationContext());
        multiProcessPreferences = new AppPreferences(this);

        viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(MainActivityViewModel.class);
        getLifecycle().addObserver(viewModel);

        // On first run remove logs from previous sessions if tunnel service is not running.
        if (viewModel.isFirstRun() && !viewModel.isServiceRunning(getApplication())) {
            LoggingProvider.LogDatabaseHelper.truncateLogs(getApplication(), true);
        }

        // The LoggingObserver will run in a separate thread
        HandlerThread loggingObserverThread = new HandlerThread("LoggingObserverThread");
        loggingObserverThread.start();
        loggingObserver = new LoggingObserver(getApplicationContext(),
                new Handler(loggingObserverThread.getLooper()));

        banner = findViewById(R.id.banner);
        setUpBanner();

        toggleButton = findViewById(R.id.toggleButton);
        connectionProgressBar = findViewById(R.id.connectionProgressBar);
        openBrowserButton = findViewById(R.id.openBrowserButton);
        toggleButton.setOnClickListener(v ->
                compositeDisposable.add(viewModel.tunnelStateFlowable()
                        .take(1)
                        .doOnNext(state -> {
                            if (state.isRunning()) {
                                viewModel.stopTunnelService();
                            } else {
                                startTunnel();
                            }
                        })
                        .subscribe()));

        tabLayout = findViewById(R.id.main_activity_tablayout);
        tabLayout.addTab(tabLayout.newTab().setTag("home").setText(R.string.home_tab_name));
        tabLayout.addTab(tabLayout.newTab().setTag("statistics").setText(R.string.statistics_tab_name));
        tabLayout.addTab(tabLayout.newTab().setTag("settings").setText(R.string.settings_tab_name));
        tabLayout.addTab(tabLayout.newTab().setTag("logs").setText(R.string.logs_tab_name));
        PageAdapter pageAdapter = new PageAdapter(getSupportFragmentManager(), tabLayout.getTabCount());

        viewPager = findViewById(R.id.tabs_view_pager);
        // Try and keep all pages of the view pager loaded. For 4 tabs in total the off screen pages
        // max is 3.
        viewPager.setOffscreenPageLimit(3);
        viewPager.setAdapter(pageAdapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int tabPosition = tab.getPosition();
                viewPager.setCurrentItem(tab.getPosition());
                multiProcessPreferences.put(CURRENT_TAB, tabPosition);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Switch to last tab when view pager is ready
        viewPager.post(() ->
                viewPager.setCurrentItem(multiProcessPreferences.getInt(CURRENT_TAB, 0), false));

        // Schedule handling current intent when the main view is fully inflated
        getWindow().getDecorView().post(() -> HandleCurrentIntent(getIntent()));
    }

    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(loggingObserver);
        cancelInvalidProxySettingsToast();
        uiUpdatesCompositeDisposable.dispose();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load new logs from the logging provider now
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            loggingObserver.dispatchChange(false, LoggingProvider.INSERT_URI);
        } else {
            loggingObserver.dispatchChange(false);
        }
        // Load new logs from the logging provider when it changes
        getContentResolver().registerContentObserver(LoggingProvider.INSERT_URI, true, loggingObserver);

        uiUpdatesCompositeDisposable = new CompositeDisposable();
        // Observe tunnel state changes to update UI
        uiUpdatesCompositeDisposable.add(viewModel.tunnelStateFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(this::updateServiceStateUI)
                .subscribe());

        // Observe custom proxy validation results to show a toast for invalid ones
        uiUpdatesCompositeDisposable.add(viewModel.customProxyValidationResultFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(isValidResult -> {
                    if (!isValidResult) {
                        cancelInvalidProxySettingsToast();
                        invalidProxySettingsToast = Toast.makeText(this,
                                R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT);
                        invalidProxySettingsToast.show();
                    }
                })
                .subscribe());

        // Observe link clicks in the embedded web view to open in the external browser
        uiUpdatesCompositeDisposable.add(viewModel.externalBrowserUrlFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(url -> displayBrowser(this, url))
                .subscribe());

        boolean shouldAutoStart = shouldAutoStart();
        preventAutoStart();

        // Auto-start on app first run
        if (shouldAutoStart) {
            startTunnel();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PREPARE_VPN) {
            if (resultCode == RESULT_OK) {
                viewModel.startTunnelService();
            } else if (resultCode == RESULT_CANCELED) {
                showVpnAlertDialog(R.string.StatusActivity_VpnPromptCancelledTitle,
                        R.string.StatusActivity_VpnPromptCancelledMessage);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        HandleCurrentIntent(intent);
    }

    private void updateServiceStateUI(final TunnelState tunnelState) {
        if (tunnelState.isUnknown()) {
            openBrowserButton.setEnabled(false);
            toggleButton.setEnabled(false);
            toggleButton.setText(getText(R.string.waiting));
            connectionProgressBar.setVisibility(View.INVISIBLE);
        } else if (tunnelState.isRunning()) {
            toggleButton.setEnabled(true);
            toggleButton.setText(getText(R.string.stop));
            if (tunnelState.connectionData().isConnected()) {
                openBrowserButton.setEnabled(true);
                connectionProgressBar.setVisibility(View.INVISIBLE);

                ArrayList<String> homePages = tunnelState.connectionData().homePages();
                final String url;
                if (homePages != null && homePages.size() > 0) {
                    url = homePages.get(0);
                } else {
                    url = null;
                }
                openBrowserButton.setOnClickListener(view -> displayBrowser(this, url));
            } else {
                openBrowserButton.setEnabled(false);
                connectionProgressBar.setVisibility(View.VISIBLE);
            }
        } else {
            // Service not running
            toggleButton.setText(getText(R.string.start));
            toggleButton.setEnabled(true);
            openBrowserButton.setEnabled(false);
            connectionProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    private void displayBrowser(Context context, String urlString) {
        // TODO: support multiple home pages in whole device mode. This is
        // disabled due to the case where users haven't set a default browser
        // and will get the prompt once per home page.

        // If URL is not empty we will try to load in an external browser, otherwise we will
        // try our best to open an external browser instance without specifying URL to load
        // or will load "about:blank" URL if that fails.

        // Prepare browser starting intent.
        Intent browserIntent;
        if (TextUtils.isEmpty(urlString)) {
            // If URL is empty, just start the app.
            browserIntent = new Intent(Intent.ACTION_MAIN);
        } else {
            // If URL is not empty, start the app with URL load intent.
            browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
        }
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // LinkedHashSet maintains FIFO order and does not allow duplicates.
        Set<String> browserIdsSet = new LinkedHashSet<>();

        // Put Brave first in the ordered set.
        browserIdsSet.add("com.brave.browser");

        // Add all resolved browsers to the set preserving the order.
        browserIdsSet.addAll(VpnAppsUtils.getInstalledWebBrowserPackageIds(getPackageManager()));

        // Put Chrome at the end if it is not already in the set.
        browserIdsSet.add("com.android.chrome");

        // If we have a candidate then set the app package ID for the browser intent and try to
        // start the app with the intent right away.
        for (String id : browserIdsSet) {
            if (VpnAppsUtils.isTunneledAppId(context, id)) {
                browserIntent.setPackage(id);
                try {
                    context.startActivity(browserIntent);
                    // Return immediately if success.
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Continue looping if error.
                }
            }
        }

        // Last effort - let the system handle it.
        // Note that the browser picked by the system will be most likely not tunneled.
        try {
            // We don't have explicit package ID for the browser intent, so the URL cannot be empty.
            // In this case try loading a special URL 'about:blank'.
            if (TextUtils.isEmpty(urlString)) {
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(browserIntent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Fail silently.
        }
    }

    private void HandleCurrentIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        // Handle external deep links first
        // Examples:
        // psiphon://settings
        // psiphon://settings/vpn
        if (handleDeepLinkIntent(intent)) {
            return;
        }

        // MainActivity is exposed to other apps because it is declared as an entry point activity of the app in the manifest.
        // For the purpose of handling internal intents, such as handshake, etc., from the tunnel service we have declared a not
        // exported activity alias 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler' that should act as a proxy for MainActivity.
        // We expect our own intents have a component set to 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler', all other intents
        // should be ignored.
        ComponentName tunnelIntentsActivityComponentName =
                new ComponentName(this, "com.psiphon3.psiphonlibrary.TunnelIntentsHandler");
        if (!tunnelIntentsActivityComponentName.equals(intent.getComponent())) {
            return;
        }

        if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_HANDSHAKE)) {
            Bundle data = intent.getExtras();
            if (data != null) {
                ArrayList<String> homePages = data.getStringArrayList(TunnelManager.DATA_TUNNEL_STATE_HOME_PAGES);
                if (homePages != null && homePages.size() > 0) {
                    String url = homePages.get(0);
                    // If the URL should not be open in the embedded web view then try and open it
                    // in an external browser. The home tab fragment will make a decision to open
                    // the URL in an embedded web view independently, if needed.
                    if (!shouldLoadInEmbeddedWebView(url)) {
                        displayBrowser(this, url);
                    } else {
                        selectTabByTag("home");
                    }
                }
            }
        } else if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE)) {
            // At this point the service should be stopped and the persisted region selection set
            // to PsiphonConstants.REGION_CODE_ANY by TunnelManager, so we only need to update the
            // region selection UI.

            // Switch to settings tab
            selectTabByTag("settings");
            // Signal Rx subscription in the options tab to update available regions list
            viewModel.signalAvailableRegionsUpdate();

            // Show "Selected region unavailable" toast
            Toast toast = Toast.makeText(this, R.string.selected_region_currently_not_available, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_VPN_REVOKED)) {
            showVpnAlertDialog(R.string.StatusActivity_VpnRevokedTitle, R.string.StatusActivity_VpnRevokedMessage);
        }
    }

    private boolean handleDeepLinkIntent(@NonNull Intent intent) {
        final String FWD_SLASH = "/";

        final String PSIPHON_SCHEME = "psiphon";

        final String SETTINGS_HOST = "settings";
        final String SETTINGS_PATH_VPN = "/vpn";
        final String SETTINGS_PATH_PROXY = "/proxy";
        final String SETTINGS_PATH_MORE_OPTIONS = "/more-options";

        Uri intentUri = intent.getData();
        // Check if this is a deep link intent we can handle
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) ||
                intentUri == null ||
                !PSIPHON_SCHEME.equals(intentUri.getScheme())) {
            // Intent not handled
            return false;
        }

        String path = intentUri.getPath();

        switch (intentUri.getHost()) {
            case SETTINGS_HOST:
                selectTabByTag("settings");
                if (path != null) {
                    // If uri path is "/vpn" or "/vpn/.*" then signal to navigate to VPN settings screen.
                    // If the path is "/proxy" or "/proxy/.*" then signal to navigate to Proxy settings screen.
                    // If the path is "/more-options" or "/more-options/.*" then signal to navigate to More Options screen.
                    if (path.equals(SETTINGS_PATH_VPN) || path.startsWith(SETTINGS_PATH_VPN + FWD_SLASH)) {
                        viewModel.signalOpenVpnSettings();
                    } else if (path.equals(SETTINGS_PATH_PROXY) || path.startsWith(SETTINGS_PATH_PROXY + FWD_SLASH)) {
                        viewModel.signalOpenProxySettings();
                    } else if (path.equals(SETTINGS_PATH_MORE_OPTIONS) || path.startsWith(SETTINGS_PATH_MORE_OPTIONS)) {
                        viewModel.signalOpenMoreOptions();
                    }
                }
                // intent handled
                return true;
        }
        // intent not handled
        return false;
    }

    private void showVpnAlertDialog(int titleId, int messageId) {
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(messageId)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void startTunnel() {
        // Don't start if custom proxy settings is selected and values are invalid
        if (!viewModel.validateCustomProxySettings()) {
            return;
        }
        boolean waitingForPrompt = doVpnPrepare();
        if (!waitingForPrompt) {
            viewModel.startTunnelService();
        }
    }

    private boolean doVpnPrepare() {
        // Devices without VpnService support throw various undocumented
        // exceptions, including ActivityNotFoundException and ActivityNotFoundException.
        // For example: http://code.google.com/p/ics-openvpn/source/browse/src/de/blinkt/openvpn/LaunchVPN.java?spec=svn2a81c206204193b14ac0766386980acdc65bee60&name=v0.5.23&r=2a81c206204193b14ac0766386980acdc65bee60#376
        try {
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                // TODO: can we disable the mode before we reach this this
                // failure point with
                // resolveActivity()? We'll need the intent from prepare() or
                // we'll have to mimic it.
                // http://developer.android.com/reference/android/content/pm/PackageManager.html#resolveActivity%28android.content.Intent,%20int%29

                startActivityForResult(intent, REQUEST_CODE_PREPARE_VPN);

                // start service will be called in onActivityResult
                return true;
            }
            return false;
        } catch (Exception e) {
            Utils.MyLog.e(R.string.tunnel_whole_device_exception, Utils.MyLog.Sensitivity.NOT_SENSITIVE);
            // true = waiting for prompt, although we can't start the
            // activity so onActivityResult won't be called
            return true;
        }
    }

    private void cancelInvalidProxySettingsToast() {
        if (invalidProxySettingsToast != null) {
            View toastView = invalidProxySettingsToast.getView();
            if (toastView != null) {
                if (toastView.isShown()) {
                    invalidProxySettingsToast.cancel();
                }
            }
        }
    }

    private void preventAutoStart() {
        viewModel.setFirstRun(false);
    }

    private boolean shouldAutoStart() {
        return viewModel.isFirstRun() &&
                !getIntent().getBooleanExtra(INTENT_EXTRA_PREVENT_AUTO_START, false);
    }

    public static boolean shouldLoadInEmbeddedWebView(String url) {
        for (String homeTabUrlExclusion : EmbeddedValues.HOME_TAB_URL_EXCLUSIONS) {
            if (url.contains(homeTabUrlExclusion)) {
                return false;
            }
        }
        return true;
    }

    private void setUpBanner() {
        // Play Store Build instances should use existing banner from previously installed APK
        // (if present). To enable this, non-Play Store Build instances write their banner to
        // a private file.
        try {
            Bitmap bitmap = getBannerBitmap();
            if (!EmbeddedValues.IS_PLAY_STORE_BUILD) {
                saveBanner(bitmap);
            }

            // If we successfully got the banner image set it and it's background
            if (bitmap != null) {
                banner.setImageBitmap(bitmap);
                banner.setBackgroundColor(getMostCommonColor(bitmap));
            }
        } catch (IOException e) {
            // Ignore failure
        }
    }

    private void saveBanner(Bitmap bitmap) throws IOException {
        if (bitmap == null) {
            return;
        }

        FileOutputStream out = openFileOutput(BANNER_FILE_NAME, Context.MODE_PRIVATE);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
    }

    private Bitmap getBannerBitmap() {
        if (EmbeddedValues.IS_PLAY_STORE_BUILD) {
            File bannerImageFile = new File(getFilesDir(), BANNER_FILE_NAME);
            if (bannerImageFile.exists()) {
                return BitmapFactory.decodeFile(bannerImageFile.getAbsolutePath());
            }
        }

        return BitmapFactory.decodeResource(getResources(), R.drawable.banner);
    }

    private int getMostCommonColor(Bitmap bitmap) {
        if (bitmap == null) {
            return Color.WHITE;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int pixels[] = new int[size];

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        HashMap<Integer, Integer> colorMap = new HashMap<>();

        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            if (colorMap.containsKey(color)) {
                colorMap.put(color, colorMap.get(color) + 1);
            } else {
                colorMap.put(color, 1);
            }
        }

        ArrayList<Map.Entry<Integer, Integer>> entries = new ArrayList<>(colorMap.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        return entries.get(0).getKey();
    }

    public void selectTabByTag(@NonNull Object tag) {
        viewPager.post(() -> {
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null) {
                    Object tabTag = tabLayout.getTabAt(i).getTag();
                    if (tag.equals(tabTag)) {
                        viewPager.setCurrentItem(i, true);
                    }
                }
            }
        });
    }


    static class PageAdapter extends FragmentPagerAdapter {
        private int numOfTabs;

        PageAdapter(FragmentManager fm, int numOfTabs) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.numOfTabs = numOfTabs;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new HomeTabFragment();
                case 1:
                    return new StatisticsTabFragment();
                case 2:
                    return new OptionsTabFragment();
                case 3:
                    return new LogsTabFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return numOfTabs;
        }
    }
}
