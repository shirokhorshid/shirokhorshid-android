/*
 * Copyright (c) 2014, Psiphon Inc.
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

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.Toast;

import com.psiphon3.psiphonlibrary.*;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;


public class StatusActivity
    extends com.psiphon3.psiphonlibrary.MainBase.TabbedActivityBase
{
    public static final String BANNER_FILE_NAME = "bannerImage";

    private ImageView m_banner;
    private boolean m_tunnelWholeDevicePromptShown = false;
    private boolean m_loadedSponsorTab = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.main);

        m_banner = (ImageView) findViewById(R.id.banner);
        m_tabHost = (TabHost) findViewById(R.id.tabHost);
        m_toggleButton = (Button) findViewById(R.id.toggleButton);

        // NOTE: super class assumes m_tabHost is initialized in its onCreate

        super.onCreate(savedInstanceState);

        // EmbeddedValues.initialize(this); is called in MainBase.OnCreate

        setUpBanner();

        // Auto-start on app first run
        if (m_firstRun) {
            m_firstRun = false;
            startUp();
        }

        // Initialize WebView proxy settings before attempting to load any URLs
        WebViewProxySettings.initialize(this);

        m_loadedSponsorTab = false;
        HandleCurrentIntent();

        restoreSponsorTab();
    }

    @Override
    protected void restoreSponsorTab() {
        // HandleCurrentIntent() may have already loaded the sponsor tab
        if (isTunnelConnected() && !m_loadedSponsorTab)
        {
            loadSponsorTab(false);
        }
    }

    private void loadSponsorTab(boolean freshConnect)
    {
        resetSponsorHomePage(freshConnect);
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
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        // This is a work around for SDK 9, 10 as they don't have the Intent.FLAG_ACTIVITY_CLEAR_TASK
        // If we have this extra, restart the activity
        if (intent.hasExtra(MoreOptionsPreferenceActivity.FORCE_ACTIVITY_RESTART)) {
            restartActivity();
        }

        // If the app is already foreground (so onNewIntent is being called),
        // the incoming intent is not automatically set as the activity's intent
        // (i.e., the intent returned by getIntent()). We want this behaviour,
        // so we'll set it explicitly.
        setIntent(intent);

        // Handle explicit intent that is received when activity is already running
        HandleCurrentIntent();
    }

    private void restartActivity() {
        // This should only be used to restart the activity in SDK 9 or 10 as Intent.FLAG_ACTIVITY_CLEAR_TASK is not available
        Intent intent = new Intent(this, StatusActivity.class);
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        }

        finish();
    }

    @Override
    protected PendingIntent getHandshakePendingIntent() {
        Intent intent = new Intent(
                TunnelManager.INTENT_ACTION_HANDSHAKE,
                null,
                this,
                com.psiphon3.StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected PendingIntent getServiceNotificationPendingIntent() {
        Intent intent = new Intent(
                "ACTION_VIEW",
                null,
                this,
                com.psiphon3.StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected PendingIntent getRegionNotAvailablePendingIntent() {
        Intent intent = new Intent(
                TunnelManager.INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE,
                null,
                this,
                com.psiphon3.StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected PendingIntent getVpnRevokedPendingIntent() {
        Intent intent = new Intent(
                TunnelManager.INTENT_ACTION_VPN_REVOKED,
                null,
                this,
                com.psiphon3.StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    protected void HandleCurrentIntent()
    {
        Intent intent = getIntent();

        if (intent == null || intent.getAction() == null)
        {
            return;
        }

        if (0 == intent.getAction().compareTo(TunnelManager.INTENT_ACTION_HANDSHAKE))
        {
            getTunnelStateFromHandshakeIntent(intent);

            // OLD COMMENT:
            // Show the home page. Always do this in browser-only mode, even
            // after an automated reconnect -- since the status activity was
            // brought to the front after an unexpected disconnect. In whole
            // device mode, after an automated reconnect, we don't re-invoke
            // the browser.
            // UPDATED:
            // We don't bring the status activity to the front after an
            // unexpected disconnect in browser-only mode any more.
            // Show the home page, unless this was an automatic reconnect,
            // since the homepage should already be showing.
            if (!intent.getBooleanExtra(TunnelManager.DATA_HANDSHAKE_IS_RECONNECT, false))
            {
                m_tabHost.setCurrentTabByTag("home");
                loadSponsorTab(true);
                m_loadedSponsorTab = true;
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

            // Set egress region preference to 'Best Performance'
            updateEgressRegionPreference(PsiphonConstants.REGION_CODE_ANY);

            // Set region selection to 'Best Performance' too
            m_regionSelector.setSelectionByValue(PsiphonConstants.REGION_CODE_ANY);

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
        }
    }

    public void onToggleClick(View v)
    {
        doToggle();
    }

    public void onOpenBrowserClick(View v)
    {
        displayBrowser(this, null);
    }

    @Override
    public void onFeedbackClick(View v)
    {
        Intent feedbackIntent = new Intent(this, FeedbackActivity.class);
        startActivity(feedbackIntent);
    }

    @Override
    protected void startUp()
    {
        // If the user hasn't set a whole-device-tunnel preference, show a prompt
        // (and delay starting the tunnel service until the prompt is completed)

        boolean hasPreference;
        AppPreferences mpPreferences = new AppPreferences(this);
        try {
            mpPreferences.getBoolean(TUNNEL_WHOLE_DEVICE_PREFERENCE);
            hasPreference = true;
        } catch (ItemNotFoundException e) {
            hasPreference = false;
        }

        if (m_tunnelWholeDeviceToggle.isEnabled() &&
            !hasPreference &&
            !isServiceRunning())
        {
            if (!m_tunnelWholeDevicePromptShown && !this.isFinishing())
            {
                final Context context = this;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog dialog = new AlertDialog.Builder(context)
                                .setCancelable(false)
                                .setOnKeyListener(
                                        new DialogInterface.OnKeyListener() {
                                            @Override
                                            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                                // Don't dismiss when hardware search button is clicked (Android 2.3 and earlier)
                                                return keyCode == KeyEvent.KEYCODE_SEARCH;
                                            }
                                        })
                                .setTitle(R.string.StatusActivity_WholeDeviceTunnelPromptTitle)
                                .setMessage(R.string.StatusActivity_WholeDeviceTunnelPromptMessage)
                                .setPositiveButton(R.string.StatusActivity_WholeDeviceTunnelPositiveButton,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                // Persist the "on" setting
                                                updateWholeDevicePreference(true);
                                                startTunnel();
                                            }
                                        })
                                .setNegativeButton(R.string.StatusActivity_WholeDeviceTunnelNegativeButton,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                // Turn off and persist the "off" setting
                                                m_tunnelWholeDeviceToggle.setChecked(false);
                                                updateWholeDevicePreference(false);
                                                startTunnel();
                                            }
                                        })
                                .setOnCancelListener(
                                        new DialogInterface.OnCancelListener() {
                                            @Override
                                            public void onCancel(DialogInterface dialog) {
                                                // Don't change or persist preference (this prompt may reappear)
                                                startTunnel();
                                            }
                                        })
                                .show();

                        // Our text no longer fits in the AlertDialog buttons on Lollipop, so force the
                        // font size (on older versions, the text seemed to be scaled down to fit).
                        // TODO: custom layout
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        {
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                        }
                    }
                });
                
                m_tunnelWholeDevicePromptShown = true;
            }
            else
            {
                // ...there's a prompt already showing (e.g., user hit Home with the
                // prompt up, then resumed Psiphon)
            }

            // ...wait and let onClick handlers will start tunnel
        }
        else
        {
            // No prompt, just start the tunnel (if not already running)

            startTunnel();
        }
    }

    @Override
    public void displayBrowser(Context context, Uri uri) {
        if (uri == null) {
            for (String homePage : getHomePages()) {
                uri = Uri.parse(homePage);
                break;
            }
        }

        // No URI to display - do nothing
        if (uri == null) {
            return;
        }

        try {
            if (getTunnelConfigWholeDevice()) {
                // TODO: support multiple home pages in whole device mode. This is
                // disabled due to the case where users haven't set a default browser
                // and will get the prompt once per home page.

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);
                if (resolveInfo == null || resolveInfo.activityInfo == null ||
                        resolveInfo.activityInfo.name == null || resolveInfo.activityInfo.name.toLowerCase().contains("resolver")) {
                    // No default web browser is set, so try opening in Chrome
                    browserIntent.setPackage("com.android.chrome");
                    try {
                        context.startActivity(browserIntent);
                    } catch (ActivityNotFoundException ex) {
                        // We tried to open Chrome and it is not installed,
                        // so reinvoke with the default behaviour
                        browserIntent.setPackage(null);
                        context.startActivity(browserIntent);
                    }
                } else {
                    context.startActivity(browserIntent);
                }
            } else {
                Intent intent = new Intent(
                        "ACTION_VIEW",
                        uri,
                        context,
                        org.zirco.ui.activities.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // This intent displays the Zirco browser.
                // We use "extras" to communicate Psiphon settings to Zirco.
                // When Zirco is first created, it will use the homePages
                // extras to open tabs for each home page, respectively. When the intent
                // triggers an existing Zirco instance (and it's a singleton) this extra
                // is ignored and the browser is displayed as-is.
                // When a uri is specified, it will open as a new tab. This is
                // independent of the home pages.
                // Note: Zirco now directly accesses PsiphonData to get the current
                // local HTTP proxy port for WebView tunneling.

                intent.putExtra("localProxyPort", getListeningLocalHttpProxyPort());
                intent.putExtra("homePages", getHomePages());

                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            // Thrown by startActivity; in this case, we ignore and the URI isn't opened
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
}
