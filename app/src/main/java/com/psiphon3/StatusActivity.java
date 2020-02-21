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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.Toast;

import com.psiphon3.psiphonlibrary.AppExclusionsManager;
import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.PsiphonConstants;
import com.psiphon3.psiphonlibrary.TunnelManager;
import com.psiphon3.psiphonlibrary.Utils;

import net.grandcentrix.tray.core.ItemNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusActivity
        extends com.psiphon3.psiphonlibrary.MainBase.TabbedActivityBase {
    public static final String BANNER_FILE_NAME = "bannerImage";
    public static final String ACTION_SHOW_GET_HELP_DIALOG = "com.psiphon3.StatusActivity.SHOW_GET_HELP_CONNECTING_DIALOG";

    private ImageView m_banner;
    private boolean m_tunnelWholeDevicePromptShown = false;
    private boolean m_firstRun = true;

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
        // Auto-start on app first run
        if (shouldAutoStart()) {
            startUp();
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

    public void onHowToHelpClick(View view) {
        showConnectionHelpDialog(this, R.layout.dialog_how_to_help_connect);
    }

    @Override
    public void onFeedbackClick(View v)
    {
        Intent feedbackIntent = new Intent(this, FeedbackActivity.class);
        startActivity(feedbackIntent);
    }

    @Override
    protected void startUp() {
        // If the user hasn't set a whole-device-tunnel preference, show a prompt
        // (and delay starting the tunnel service until the prompt is completed)
        boolean hasPreference;
        try {
            m_multiProcessPreferences.getBoolean(getString(R.string.tunnelWholeDevicePreference));
            hasPreference = true;
        } catch (ItemNotFoundException e) {
            hasPreference = false;
        }
        if (Utils.hasVpnService() && !hasPreference) {
            if (!m_tunnelWholeDevicePromptShown && !this.isFinishing()) {
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                        }
                    }
                });
                m_tunnelWholeDevicePromptShown = true;
            } else {
                // ...there's a prompt already showing (e.g., user hit Home with the
                // prompt up, then resumed Psiphon)
            }
            // ...wait and let onClick handlers will start tunnel
        } else {
            // No prompt, just start the tunnel (if not already running)
            startTunnel();
        }
    }

    @Override
    public void displayBrowser(Context context, String urlString) {
        boolean wantVPN = m_multiProcessPreferences
                .getBoolean(getString(R.string.tunnelWholeDevicePreference),
                        false);

        if (wantVPN && Utils.hasVpnService()) {
            // TODO: support multiple home pages in whole device mode. This is
            // disabled due to the case where users haven't set a default browser
            // and will get the prompt once per home page.

            // If URL is not empty we will try to load in an external browser, otherwise we will
            // try our best to open an external browser instance without specifying URL to load
            // or will load "about:blank" URL if that fails.

            AppExclusionsManager appExclusionsManager = new AppExclusionsManager(context);

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

            List<String> browserIds = new ArrayList<>();
            // Put Brave first in the list
            browserIds.add("com.brave.browser");

            // Add all resolved browsers to the list
            for (String id : appExclusionsManager.getInstalledWebBrowserPackageIds(getPackageManager())) {
                if (browserIds.contains(id)) {
                    continue;
                }
                browserIds.add(id);
            }

            // Put Chrome at the end if it is not already on the list
            String chromeId = "com.android.chrome";
            if (!browserIds.contains(chromeId)) {
                browserIds.add(chromeId);
            }

            // Last effort - let the system handle it
            browserIds.add(null);

            for (String id : browserIds) {
                if (id == null) {
                    // If URL is empty try loading a special URL 'about:blank'
                    if (TextUtils.isEmpty(urlString)) {
                        browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"));
                    }
                }

                if (id == null || appExclusionsManager.isTunneledAppId(context, id)) {
                    browserIntent.setPackage(id);
                    try {
                        context.startActivity(browserIntent);
                        return;
                    } catch (ActivityNotFoundException | SecurityException ignored) {
                    }
                }
            }
        } else {
            // BOM, try to open Zirco
            Uri uri = null;
            if (!TextUtils.isEmpty(urlString)) {
                uri = Uri.parse(urlString);
            }

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

            if (urlString != null) {
                intent.putExtra("homePages", new ArrayList<>(Collections.singletonList(urlString)));
            }
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
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
