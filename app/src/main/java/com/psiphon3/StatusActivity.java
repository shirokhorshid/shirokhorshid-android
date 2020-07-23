/*
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

package com.psiphon3;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity;
import com.psiphon3.psiphonlibrary.ProxyOptionsPreferenceActivity;
import com.psiphon3.psiphonlibrary.PsiphonPreferenceFragmentCompat;
import com.psiphon3.psiphonlibrary.RegionListPreference;
import com.psiphon3.psiphonlibrary.TunnelManager;
import com.psiphon3.psiphonlibrary.UpstreamProxySettings;
import com.psiphon3.psiphonlibrary.Utils;
import com.psiphon3.psiphonlibrary.VpnAppsUtils;
import com.psiphon3.psiphonlibrary.VpnOptionsPreferenceActivity;

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

public class StatusActivity
        extends com.psiphon3.psiphonlibrary.MainBase.TabbedActivityBase {
    public static final String BANNER_FILE_NAME = "bannerImage";
    public static final String ACTION_SHOW_GET_HELP_DIALOG = "com.psiphon3.StatusActivity.SHOW_GET_HELP_CONNECTING_DIALOG";

    private ImageView m_banner;
    private boolean m_firstRun = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.main);

        m_banner = (ImageView) findViewById(R.id.banner);
        m_tabHost = (TabHost) findViewById(R.id.tabHost);
        m_tabsScrollView = (HorizontalScrollView) findViewById(R.id.tabsScrollView);
        m_toggleButton = (Button) findViewById(R.id.toggleButton);

        // NOTE: super class assumes m_tabHost is initialized in its onCreate

        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            m_optionsTabFragment = new OptionsTabFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container_settings, m_optionsTabFragment, "OptionsTabFragment")
                    .commit();
        } else {
            m_optionsTabFragment = (OptionsTabFragment) getSupportFragmentManager().findFragmentByTag("OptionsTabFragment");
        }

        // EmbeddedValues.initialize(this); is called in MainBase.OnCreate

        setUpBanner();

        HandleCurrentIntent();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isFirstRun", m_firstRun);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        m_firstRun = savedInstanceState.getBoolean("isFirstRun");
    }

    private void preventAutoStart() {
        m_firstRun = false;
    }

    private boolean shouldAutoStart() {
        return m_firstRun &&
                !tunnelServiceInteractor.isServiceRunning(getApplicationContext()) &&
                !getIntent().getBooleanExtra(INTENT_EXTRA_PREVENT_AUTO_START, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean wantVPN = m_multiProcessPreferences
                .getBoolean(getString(R.string.tunnelWholeDevicePreference),
                        true);
        if(wantVPN) {
            // Auto-start on app first run
            if (shouldAutoStart()) {
                startUp();
            }
        } else {
            // Legacy case: do not auto-start if last preference was BOM
            // Instead we switch to the options tab and display a modal with the help information
            m_tabHost.setCurrentTabByTag("settings");
            LayoutInflater inflater = this.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.legacy_bom_alert_view_layout, null);
            TextView tv = dialogView.findViewById(R.id.legacy_mode_alert_tv);
            String text = getString(R.string.legacy_bom_alert_message, getString(R.string.app_name));
            String formattedText = text.replaceAll("\n", "\n\n");
            SpannableString spannableString = new SpannableString(formattedText);

            Matcher matcher = Pattern.compile("\n\n").matcher(formattedText);
            while (matcher.find()) {
                spannableString.setSpan(new AbsoluteSizeSpan(10, true), matcher.start() + 1, matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tv.setText(spannableString);
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    // We are displaying important information to the user, so make sure the dialog
                    // is not dismissed accidentally as it won't be shown again.
                    .setCancelable(false)
                    .setPositiveButton(R.string.label_ok, null)
                    .setOnDismissListener(dialog ->
                            m_multiProcessPreferences.remove(getString(R.string.tunnelWholeDevicePreference)));
            // Add 'VPN settings' button if VPN exclusions are supported
            if (Utils.supportsVpnExclusions()) {
                builder.setNegativeButton(R.string.label_vpn_settings, (dialog, which) ->
                    startActivityForResult(new Intent(StatusActivity.this,
                            VpnOptionsPreferenceActivity.class), REQUEST_CODE_VPN_PREFERENCES)
                );
            }
            builder.show();
        }
        preventAutoStart();
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
                m_banner.setImageBitmap(bitmap);
                m_banner.setBackgroundColor(getMostCommonColor(bitmap));
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // If the app is already foreground (so onNewIntent is being called),
        // the incoming intent is not automatically set as the activity's intent
        // (i.e., the intent returned by getIntent()). We want this behaviour,
        // so we'll set it explicitly.
        setIntent(intent);

        // Handle explicit intent that is received when activity is already running
        HandleCurrentIntent();
    }

    protected void HandleCurrentIntent() {
        Intent intent = getIntent();
        if (intent == null || intent.getAction() == null) {
            return;
        }
        // StatusActivity is exposed to other apps because it is declared as an entry point activity of the app in the manifest.
        // For the purpose of handling internal intents, such as handshake, etc., from the tunnel service we have declared a not
        // exported activity alias 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler' that should act as a proxy for StatusActivity.
        // We expect our own intents have a component set to 'com.psiphon3.psiphonlibrary.TunnelIntentsHandler', all other intents
        // should be ignored.
        ComponentName tunnelIntentsActivityComponentName = new ComponentName(this, "com.psiphon3.psiphonlibrary.TunnelIntentsHandler");
        if (!tunnelIntentsActivityComponentName.equals(intent.getComponent())) {
            return;
        }

        if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_HANDSHAKE)) {
            Bundle data = intent.getExtras();
            if(data != null) {
                ArrayList<String> homePages = data.getStringArrayList(TunnelManager.DATA_TUNNEL_STATE_HOME_PAGES);
                if (homePages != null && homePages.size() > 0) {
                    String url = homePages.get(0);
                    // At this point we're showing the URL in either the embedded webview or in a browser.
                    // Some URLs are excluded from being embedded as home pages.
                    if(shouldLoadInEmbeddedWebView(url)) {
                        // Reset m_loadedSponsorTab and switch to the home tab.
                        // The embedded web view will get loaded by the updateServiceStateUI.
                        m_loadedSponsorTab = false;
                        m_tabHost.setCurrentTabByTag("home");
                    } else {
                        displayBrowser(this, url);
                    }
                }
            }

            // We only want to respond to the HANDSHAKE_SUCCESS action once,
            // so we need to clear it (by setting it to a non-special intent).
            setIntent(new Intent(
                    "ACTION_VIEW",
                    null,
                    this,
                    this.getClass()));
        } else if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE)) {
            // Switch to settings tab
            m_tabHost.setCurrentTabByTag("settings");

            // At this point the service should be stopped and the persisted region selection set
            // to PsiphonConstants.REGION_CODE_ANY by TunnelManager, so we only need to update the
            // region selection UI.
            // Update region preference in the options tab.
            if (m_optionsTabFragment != null) {
                m_optionsTabFragment.updateRegionSelectorFromPreferences();
            }

            // Show "Selected region unavailable" toast
            Toast toast = Toast.makeText(this, R.string.selected_region_currently_not_available, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();

            // We only want to respond to the INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE action once,
            // so we need to clear it (by setting it to a non-special intent).
            setIntent(new Intent(
                    "ACTION_VIEW",
                    null,
                    this,
                    this.getClass()));
        } else if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_VPN_REVOKED)) {
            showVpnAlertDialog(R.string.StatusActivity_VpnRevokedTitle, R.string.StatusActivity_VpnRevokedMessage);
        } else if (0 == intent.getAction().compareTo(ACTION_SHOW_GET_HELP_DIALOG)) {
            // OK to be null because we don't use it
            onGetHelpConnectingClick(null);
        }
    }

    public void onToggleClick(View v)
    {
        doToggle();
    }

    public void onGetHelpConnectingClick(View v) {
        showConnectionHelpDialog(this, R.layout.dialog_get_help_connecting);
    }

    @Override
    protected void startUp() {
        startTunnel();
    }

    @Override
    public void displayBrowser(Context context, String urlString) {
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

    @Override
    protected void onVpnPromptCancelled() {
        showVpnAlertDialog(R.string.StatusActivity_VpnPromptCancelledTitle, R.string.StatusActivity_VpnPromptCancelledMessage);
    }

    private void showVpnAlertDialog(int titleId, int messageId) {
        new AlertDialog.Builder(getContext())
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(messageId)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static class OptionsTabFragment extends PsiphonPreferenceFragmentCompat {
        private RegionListPreference regionListPreference;
        private Preference vpnOptionsPreference;
        private Preference proxyOptionsPreference;
        boolean arePreferencesCreated = false;

        @Override
        public void onCreatePreferencesFix(Bundle bundle, String s) {
            super.onCreatePreferencesFix(bundle, s);
            addPreferencesFromResource(R.xml.settings_preferences_screen);

            regionListPreference = (RegionListPreference) findPreference(getContext().getString(R.string.regionPreferenceKey));
            regionListPreference.setOnRegionSelectedListener(regionCode -> {
                StatusActivity activity = (StatusActivity) getActivity();
                if (activity != null && !activity.isFinishing()) {
                    activity.onRegionSelected(regionCode);
                    regionListPreference.setCurrentRegionFromPreferences();
                }
            });

            Preference feedbackPreference = findPreference(getContext().getString(R.string.feedbackPreferenceKey));
            feedbackPreference.setIntent(new Intent(getActivity(), FeedbackActivity.class));

            Preference moreOptionsPreference = findPreference(getContext().getString(R.string.moreOptionsPreferenceKey));
            moreOptionsPreference.setOnPreferenceClickListener(__ -> {
                final FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    getActivity().startActivityForResult(new Intent(getActivity(),
                            MoreOptionsPreferenceActivity.class), REQUEST_CODE_MORE_PREFERENCES);
                }
                return true;
            });

            vpnOptionsPreference = findPreference(getContext().getString(R.string.vpnOptionsPreferenceKey));
            if (Utils.supportsVpnExclusions()) {
                vpnOptionsPreference.setOnPreferenceClickListener(__ -> {
                    final FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        getActivity().startActivityForResult(new Intent(getActivity(),
                                VpnOptionsPreferenceActivity.class), REQUEST_CODE_VPN_PREFERENCES);
                    }
                    return true;
                });
            } else {
                vpnOptionsPreference.setEnabled(false);
                vpnOptionsPreference.setSummary(R.string.vpn_exclusions_preference_not_available_summary);
            }

            proxyOptionsPreference = findPreference(getContext().getString(R.string.proxyOptionsPreferenceKey));
            proxyOptionsPreference.setOnPreferenceClickListener(__ -> {
                final FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    getActivity().startActivityForResult(new Intent(getActivity(),
                            ProxyOptionsPreferenceActivity.class), REQUEST_CODE_PROXY_PREFERENCES);
                }
                return true;
            });
            arePreferencesCreated = true;
            setSummaryFromPreferences();
        }

        public void setSummaryFromPreferences() {
            if (!arePreferencesCreated) {
                return;
            }

            int count;
            String summary;
            // Update VPN setting summary if applicable
            if (Utils.supportsVpnExclusions()) {
                switch (VpnAppsUtils.getVpnAppsExclusionMode(getContext())) {
                    case ALL_APPS:
                        vpnOptionsPreference.setSummary(R.string.preference_routing_all_apps_tunnel_summary);
                        break;
                    case EXCLUDE_APPS:
                        count = VpnAppsUtils.getCurrentAppsExcludedFromVpn(getContext()).size();
                        summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_exclude_summary, count, count);
                        vpnOptionsPreference.setSummary(summary);
                        break;
                    case INCLUDE_APPS:
                        count = VpnAppsUtils.getCurrentAppsIncludedInVpn(getContext()).size();
                        summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_include_summary, count, count);
                        vpnOptionsPreference.setSummary(summary);
                        break;
                }
            }
            // Update Proxy setting summary
            if (UpstreamProxySettings.getUseHTTPProxy(getContext())) {
                if (UpstreamProxySettings.getUseCustomProxySettings(getContext())) {
                    proxyOptionsPreference.setSummary(R.string.preference_summary_custom_proxy);
                } else {
                    proxyOptionsPreference.setSummary(R.string.preference_summary_system_proxy);
                }
            } else {
                proxyOptionsPreference.setSummary(R.string.preference_summary_no_proxy);
            }
        }

        public void updateRegionSelectorFromPreferences() {
            if (arePreferencesCreated) {
                regionListPreference.setCurrentRegionFromPreferences();
            }
        }
    }
}
