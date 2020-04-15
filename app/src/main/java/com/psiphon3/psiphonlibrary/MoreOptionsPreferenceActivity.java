/*
 * Copyright (c) 2015, Psiphon Inc.
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

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import org.zirco.providers.ZircoBookmarksContentProvider;
import org.zirco.ui.runnables.XmlHistoryBookmarksExporter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class MoreOptionsPreferenceActivity extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceClickListener {

    // This is taken from https://developer.android.com/reference/android/provider/Settings#ACTION_VPN_SETTINGS
    // As we target to low of an SDK we cannot reference this constant directly
    private static final String ACTION_VPN_SETTINGS = "android.settings.VPN_SETTINGS";

    public static final String INTENT_EXTRA_LANGUAGE_CHANGED = "com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity.LANGUAGE_CHANGED";
    public static final String INTENT_EXTRA_VPN_EXCLUSIONS_ONLY = "com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity.VPN_EXCLUSIONS_ONLY";;

    private interface PreferenceGetter {
        boolean getBoolean(@NonNull final String key, final boolean defaultValue);

        String getString(@NonNull final String key, final String defaultValue);
    }

    private class AppPreferencesWrapper implements PreferenceGetter {
        AppPreferences prefs;

        public AppPreferencesWrapper(AppPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            return prefs.getBoolean(key, defaultValue);
        }

        @Override
        public String getString(@NonNull String key, String defaultValue) {
            return prefs.getString(key, defaultValue);
        }
    }

    private class SharedPreferencesWrapper implements PreferenceGetter {
        SharedPreferences prefs;

        public SharedPreferencesWrapper(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            return prefs.getBoolean(key, defaultValue);
        }

        @Override
        public String getString(@NonNull String key, String defaultValue) {
            return prefs.getString(key, defaultValue);
        }
    }

    CheckBoxPreference mNotificationSound;
    CheckBoxPreference mNotificationVibration;
    RadioButtonPreference mTunnelAllApps;
    RadioButtonPreference mTunnelSelectedApps;
    RadioButtonPreference mTunnelNotSelectedApps;
    Preference mSelectApps;
    CheckBoxPreference mUseProxy;
    RadioButtonPreference mUseSystemProxy;
    RadioButtonPreference mUseCustomProxy;
    CheckBoxPreference mUseProxyAuthentication;
    EditTextPreference mProxyHost;
    EditTextPreference mProxyPort;
    EditTextPreference mProxyUsername;
    EditTextPreference mProxyPassword;
    EditTextPreference mProxyDomain;
    Bundle mDefaultSummaryBundle;
    ListPreference mLanguageSelector;

    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Store temporary preferences used in this activity in its own file
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(getString(R.string.moreOptionsPreferencesName));

        addPreferencesFromResource(R.xml.preferences);
        final PreferenceScreen preferences = getPreferenceScreen();

        mNotificationSound = (CheckBoxPreference) preferences.findPreference(getString(R.string.preferenceNotificationsWithSound));
        mNotificationVibration = (CheckBoxPreference) preferences.findPreference(getString(R.string.preferenceNotificationsWithVibrate));

        mUseProxy = (CheckBoxPreference) preferences.findPreference(getString(R.string.useProxySettingsPreference));
        mUseSystemProxy = (RadioButtonPreference) preferences
                .findPreference(getString(R.string.useSystemProxySettingsPreference));
        mUseCustomProxy = (RadioButtonPreference) preferences
                .findPreference(getString(R.string.useCustomProxySettingsPreference));

        mProxyHost = (EditTextPreference) preferences
                .findPreference(getString(R.string.useCustomProxySettingsHostPreference));
        mProxyPort = (EditTextPreference) preferences
                .findPreference(getString(R.string.useCustomProxySettingsPortPreference));

        mUseProxyAuthentication = (CheckBoxPreference) preferences
                .findPreference(getString(R.string.useProxyAuthenticationPreference));
        mProxyUsername = (EditTextPreference) preferences
                .findPreference(getString(R.string.useProxyUsernamePreference));
        mProxyPassword = (EditTextPreference) preferences
                .findPreference(getString(R.string.useProxyPasswordPreference));
        mProxyDomain = (EditTextPreference) preferences
                .findPreference(getString(R.string.useProxyDomainPreference));

        PreferenceGetter preferenceGetter;

        // Initialize with current shared preferences if restoring from configuration change,
        // otherwise initialize with tray preferences values.
        if (savedInstanceState != null && savedInstanceState.getBoolean("onSaveInstanceState", false)) {
            preferenceGetter = new SharedPreferencesWrapper(PreferenceManager.getDefaultSharedPreferences(this));
        } else {
            preferenceGetter = new AppPreferencesWrapper(new AppPreferences(this));
        }

        if (Utils.supportsAlwaysOnVPN()) {
            setupNavigateToVPNSettings(preferences);
        }

        if (supportsRoutingConfiguration()) {
            setupTunnelConfiguration(preferences, preferenceGetter);
        }

        setupLanguageSelector(preferences);

        mNotificationSound.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithSound), false));
        mNotificationVibration.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithVibrate), false));

        mUseProxy.setChecked(preferenceGetter.getBoolean(getString(R.string.useProxySettingsPreference), false));
        // set use system proxy preference by default
        mUseSystemProxy.setChecked(preferenceGetter.getBoolean(getString(R.string.useSystemProxySettingsPreference), true));
        mUseCustomProxy.setChecked(preferenceGetter.getBoolean(getString(R.string.useCustomProxySettingsPreference), false));
        mProxyHost.setText(preferenceGetter.getString(getString(R.string.useCustomProxySettingsHostPreference), ""));
        mProxyPort.setText(preferenceGetter.getString(getString(R.string.useCustomProxySettingsPortPreference), ""));
        mUseProxyAuthentication.setChecked(preferenceGetter.getBoolean(getString(R.string.useProxyAuthenticationPreference), false));
        mProxyUsername.setText(preferenceGetter.getString(getString(R.string.useProxyUsernamePreference), ""));
        mProxyPassword.setText(preferenceGetter.getString(getString(R.string.useProxyPasswordPreference), ""));
        mProxyDomain.setText(preferenceGetter.getString(getString(R.string.useProxyDomainPreference), ""));

        // Set listeners
        mUseSystemProxy.setOnPreferenceClickListener(this);
        mUseCustomProxy.setOnPreferenceClickListener(this);

        mProxyHost.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String proxyHost = (String) newValue;
                if (TextUtils.isEmpty(proxyHost)) {
                    Toast toast = Toast.makeText(MoreOptionsPreferenceActivity.this, R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT);
                    toast.show();
                    return false;
                }
                return true;
            }
        });

        mProxyPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int proxyPort;
                try {
                    proxyPort = Integer.valueOf((String) newValue);
                } catch (NumberFormatException e) {
                    proxyPort = 0;
                }
                if (proxyPort >= 1 && proxyPort <= 65535) {
                    return true;
                }
                Toast toast = Toast.makeText(MoreOptionsPreferenceActivity.this, R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT);
                toast.show();
                return false;
            }
        });

        mDefaultSummaryBundle = new Bundle();

        if (getIntent().getBooleanExtra(INTENT_EXTRA_VPN_EXCLUSIONS_ONLY, false)) {
            trimToVpnExclusionsOnly();
            return;
        }

        updateProxyPreferencesUI();
        setExportZircoBookmarksPreference();
    }

    private void setExportZircoBookmarksPreference() {
        final PreferenceScreen screen = this.getPreferenceScreen();
        Preference exportZircoPref = screen.findPreference(getString(R.string.exportZircoPref));

        // do nothing if the preference already exists
        if (exportZircoPref != null) {
            return;
        }
        final Cursor cursor = ZircoBookmarksContentProvider.getAllRecords(this.getContentResolver());

        // Do not show preference if there is no data to export
        if (cursor == null || cursor.getCount() == 0) {
            return;
        }

        PreferenceCategory category = new PreferenceCategory(screen.getContext());
        category.setTitle(R.string.legacy_zirco_export_pref_category_title);
        category.setKey(getString(R.string.exportZircoPref));
        screen.addPreference(category);

        Preference pref = new Preference(this);
        pref.setTitle("Export data");
        pref.setSummary("Click here to export legacy Zirco browser bookmarks and history to external data storage");

        pref.setOnPreferenceClickListener(preference -> {
            exportZircoHistoryBookmarks(cursor);
            return true;
        });
        category.addPreference(pref);
    }

    private void trimToVpnExclusionsOnly() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = preferenceScreen.getPreferenceCount() - 1; i >= 0; i--) {
            Preference p = preferenceScreen.getPreference(i);
            if (p instanceof PreferenceCategory) {
                PreferenceCategory pCat = (PreferenceCategory) p;
                if (!pCat.getKey().equals(getString(R.string.vpnSettingsCategoryKey))) {
                    preferenceScreen.removePreference(p);
                } else {
                    for (int j = pCat.getPreferenceCount() - 1; j >= 0; j--) {
                        Preference pSub = pCat.getPreference(j);
                        if (pSub.getKey().equals(getString(R.string.preferenceNavigateToVPNSetting))) {
                            pCat.removePreference(pSub);
                        }
                    }
                }
            }
            mTunnelSelectedApps.setSummary(R.string.vpn_exclusions_pro_tip_summary);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        initSummary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
        Preference curPref = findPreference(key);
        updatePrefsSummary(sharedPreferences, curPref);
        updateProxyPreferencesUI();

        // If language preference has changed we need to set new locale based on the current
        // preference value and restart the app.
        if (key.equals(getString(R.string.preferenceLanguageSelection))) {
            String languageCode = mLanguageSelector.getValue();
            try {
                int pos = mLanguageSelector.findIndexOfValue(languageCode);
                mLanguageSelector.setSummary(mLanguageSelector.getEntries()[pos]);
            } catch (Exception ignored) {
            }
            setLanguageAndRestartApp(languageCode);
        }
    }

    private void setLanguageAndRestartApp(String languageCode) {
        // The LocaleManager will correctly set the resource + store the language preference for the future
        LocaleManager localeManager = LocaleManager.getInstance(MoreOptionsPreferenceActivity.this);
        if (languageCode.equals("")) {
            localeManager.resetToSystemLocale(MoreOptionsPreferenceActivity.this);
        } else {
            localeManager.setNewLocale(MoreOptionsPreferenceActivity.this, languageCode);
        }

        // Finish back to the StatusActivity and inform the language has changed
        Intent data = new Intent();
        data.putExtra(INTENT_EXTRA_LANGUAGE_CHANGED, true);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(getString(R.string.moreOptionsPreferencesName), mode);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mUseSystemProxy) {
            mUseSystemProxy.setChecked(true);
            mUseCustomProxy.setChecked(false);
        } else if (preference == mUseCustomProxy) {
            mUseSystemProxy.setChecked(false);
            mUseCustomProxy.setChecked(true);
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("onSaveInstanceState", true);
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void updatePrefsSummary(SharedPreferences sharedPreferences, Preference pref) {
        if (pref instanceof EditTextPreference) {
            // EditPreference
            EditTextPreference editTextPref = (EditTextPreference) pref;
            String summary = editTextPref.getText();
            if (summary != null && !summary.trim().equals("")) {
                //hide passwords
                //http://stackoverflow.com/questions/15044595/preventing-edittextpreference-from-updating-summary-for-inputtype-password
                int inputType = editTextPref.getEditText().getInputType() & InputType.TYPE_MASK_VARIATION;
                boolean isPassword = ((inputType == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                        || (inputType == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        || (inputType == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));

                if (isPassword) {
                    editTextPref.setSummary(editTextPref.getText().replaceAll(".", "*"));
                } else {
                    editTextPref.setSummary(editTextPref.getText());
                }
            } else {
                editTextPref.setSummary((CharSequence) mDefaultSummaryBundle.get(editTextPref.getKey()));
            }
        }
    }

    /*
     * Init summary fields
     */
    protected void initSummary() {
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initPrefsSummary(getPreferenceManager()
                    .getSharedPreferences(), getPreferenceScreen()
                    .getPreference(i));
        }
    }

    /*
     * Init single Preference
     */
    protected void initPrefsSummary(SharedPreferences sharedPreferences, Preference p) {
        if (p instanceof PreferenceCategory) {
            PreferenceCategory pCat = (PreferenceCategory) p;
            for (int i = 0; i < pCat.getPreferenceCount(); i++) {
                initPrefsSummary(sharedPreferences, pCat.getPreference(i));
            }
        } else {
            mDefaultSummaryBundle.putCharSequence(p.getKey(), p.getSummary());
            updatePrefsSummary(sharedPreferences, p);
        }
    }

    private void setupNavigateToVPNSettings(PreferenceScreen preferences) {
        Preference preference = preferences.findPreference(getString(R.string.preferenceNavigateToVPNSetting));
        preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ACTION_VPN_SETTINGS));
                return true;
            }
        });
    }

    private void setupLanguageSelector(PreferenceScreen preferences) {
        // Get the preference view and create the locale manager with the app's context.
        // Cannot use this activity as the context as we also need StatusActivity to pick up on it.
        mLanguageSelector = (ListPreference) preferences.findPreference(getString(R.string.preferenceLanguageSelection));

        // Collect the string array of <language name>,<language code>
        String[] locales = getResources().getStringArray(R.array.languages);
        CharSequence[] languageNames = new CharSequence[locales.length + 1];
        CharSequence[] languageCodes = new CharSequence[locales.length + 1];

        // Setup the "Default" locale
        languageNames[0] = getString(R.string.preference_language_default_language);
        languageCodes[0] = "";

        LocaleManager localeManager = LocaleManager.getInstance(this);
        String currentLocaleLanguageCode = localeManager.getLanguage();
        int currentLocaleLanguageIndex = -1;

        if (localeManager.isSystemLocale(currentLocaleLanguageCode)) {
            currentLocaleLanguageIndex = 0;
        }

        for (int i = 1; i <= locales.length; ++i) {
            // Split the string on the comma
            String[] localeArr = locales[i - 1].split(",");
            languageNames[i] = localeArr[0];
            languageCodes[i] = localeArr[1];

            if (localeArr[1] != null && localeArr[1].equals(currentLocaleLanguageCode)) {
                currentLocaleLanguageIndex = i;
            }
        }

        // Entries are displayed to the user, codes are the value used in the backend
        mLanguageSelector.setEntries(languageNames);
        mLanguageSelector.setEntryValues(languageCodes);

        // If current locale is on the list set it selected
        if (currentLocaleLanguageIndex >= 0) {
            try {
                mLanguageSelector.setValueIndex(currentLocaleLanguageIndex);
                mLanguageSelector.setSummary(languageNames[currentLocaleLanguageIndex]);
            } catch (Exception ignored) {
            }
        }
    }

    private void disableCustomProxySettings() {
        mProxyHost.setEnabled(false);
        mProxyPort.setEnabled(false);
        mUseProxyAuthentication.setEnabled(false);
        disableProxyAuthenticationSettings();
    }

    private void enableCustomProxySettings() {
        mProxyHost.setEnabled(true);
        mProxyPort.setEnabled(true);
        mUseProxyAuthentication.setEnabled(true);
        enableProxyAuthenticationSettings();
    }

    private void disableProxyAuthenticationSettings() {
        mProxyUsername.setEnabled(false);
        mProxyPassword.setEnabled(false);
        mProxyDomain.setEnabled(false);
    }

    private void enableProxyAuthenticationSettings() {
        mProxyUsername.setEnabled(true);
        mProxyPassword.setEnabled(true);
        mProxyDomain.setEnabled(true);
    }

    private void disableProxySettings() {
        mUseSystemProxy.setEnabled(false);
        mUseCustomProxy.setEnabled(false);
        disableCustomProxySettings();
        disableProxyAuthenticationSettings();
    }

    private void enableProxySettings() {
        mUseSystemProxy.setEnabled(true);
        mUseCustomProxy.setEnabled(true);
        enableCustomProxySettings();
        enableProxyAuthenticationSettings();
    }

    private void updateProxyPreferencesUI() {
        if (!mUseProxy.isChecked()) {
            disableProxySettings();
        } else {
            enableProxySettings();
            if (mUseSystemProxy.isChecked()) {
                disableCustomProxySettings();
            } else {
                enableCustomProxySettings();
                if (mUseProxyAuthentication.isChecked()) {
                    enableProxyAuthenticationSettings();
                } else {
                    disableProxyAuthenticationSettings();
                }
            }
        }
    }

    private void setupTunnelConfiguration(PreferenceScreen preferences, PreferenceGetter preferenceGetter) {
        mTunnelAllApps = (RadioButtonPreference) preferences.findPreference(getString(R.string.preferenceIncludeAllAppsInVpn));
        mTunnelSelectedApps = (RadioButtonPreference) preferences.findPreference(getString(R.string.preferenceIncludeAppsInVpn));
        mTunnelNotSelectedApps = (RadioButtonPreference) preferences.findPreference(getString(R.string.preferenceExcludeAppsFromVpn));
        mSelectApps = preferences.findPreference(getString(R.string.preferenceSelectApps));

        // Migrate old VPN exclusions preferences if any
        VpnAppsUtils.migrate(getApplicationContext());

        // Also create a snapshot of current VPN exclusion sets. We need this because tunnel restart
        // logic when we return back to main activity from this screen will compare the preferences
        // set in this screen with currently stored preferences in order to make decision if the
        // preferences change needs to trigger a tunnel restart.
        String currentIncludeAppsString = preferenceGetter.getString(getString(R.string.preferenceIncludeAppsInVpnString), "");
        preferences.getEditor().putString(getString(R.string.preferenceIncludeAppsInVpnString), currentIncludeAppsString).apply();
        String currentExcludeAppsString = preferenceGetter.getString(getString(R.string.preferenceExcludeAppsFromVpnString), "");
        preferences.getEditor().putString(getString(R.string.preferenceExcludeAppsFromVpnString), currentExcludeAppsString).apply();

        if (preferenceGetter.getBoolean(getString(R.string.preferenceIncludeAllAppsInVpn), false)) {
            tunnelAllApps();
        } else if (preferenceGetter.getBoolean(getString(R.string.preferenceIncludeAppsInVpn), false)) {
            tunnelSelectedApps();
        } else {
            tunnelNotSelectedApps();
        }

        mTunnelAllApps.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                tunnelAllApps();
                return true;
            }
        });

        mTunnelSelectedApps.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                tunnelSelectedApps();
                return true;
            }
        });

        mTunnelNotSelectedApps.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                tunnelNotSelectedApps();
                return true;
            }
        });

        mSelectApps.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final InstalledAppsMultiSelectListPreference installedAppsMultiSelectListPreference =
                        new InstalledAppsMultiSelectListPreference(MoreOptionsPreferenceActivity.this,
                                getLayoutInflater(), mTunnelSelectedApps.isChecked());

                final AlertDialog alertDialog = installedAppsMultiSelectListPreference
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                if (mTunnelAllApps.isChecked()) {
                                    tunnelAllApps();
                                } else if (mTunnelSelectedApps.isChecked()) {
                                    tunnelSelectedApps();
                                } else {
                                    tunnelNotSelectedApps();
                                }
                            }
                        })
                        .create();

                alertDialog.setOnShowListener(dialog -> {
                    Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                    button.setOnClickListener(v -> {
                        if (!installedAppsMultiSelectListPreference.isLoaded()) {
                            alertDialog.dismiss();
                            return;
                        }
                        Set<String> selectedApps = installedAppsMultiSelectListPreference.getSelectedApps();
                        int installedAppsCount = installedAppsMultiSelectListPreference.getInstalledAppsCount();
                        if (installedAppsMultiSelectListPreference.isWhitelist()) {
                            if (selectedApps.size() > 0) {
                                VpnAppsUtils.setPendingAppsToIncludeInVpn(getApplicationContext(), selectedApps);
                            } else {
                                new AlertDialog.Builder(MoreOptionsPreferenceActivity.this)
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setTitle(R.string.bad_vpn_exclusion_setting_alert_title)
                                        .setMessage(R.string.bad_vpn_exclusion_whitelist_alert_message)
                                        .setPositiveButton(R.string.label_ok, null)
                                        .setCancelable(true)
                                        .show();
                                return;
                            }
                        } else {
                            if (installedAppsCount > selectedApps.size()) {
                                VpnAppsUtils.setPendingAppsToExcludeFromVpn(getApplicationContext(), selectedApps);
                            } else {
                                new AlertDialog.Builder(MoreOptionsPreferenceActivity.this)
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setTitle(R.string.bad_vpn_exclusion_setting_alert_title)
                                        .setMessage(R.string.bad_vpn_exclusion_blacklist_alert_message)
                                        .setPositiveButton(R.string.label_ok, null)
                                        .setCancelable(true)
                                        .show();
                                return;
                            }
                        }
                        alertDialog.dismiss();
                    });
                });

                alertDialog.show();

                return true;
            }
        });
    }

    private void tunnelAllApps() {
        mTunnelAllApps.setChecked(true);
        mTunnelSelectedApps.setChecked(false);
        mTunnelNotSelectedApps.setChecked(false);
        mSelectApps.setEnabled(false);
        mSelectApps.setSummary(R.string.preference_routing_all_apps_tunnel_summary);
    }

    private void tunnelSelectedApps() {
        mTunnelAllApps.setChecked(false);
        mTunnelSelectedApps.setChecked(true);
        mTunnelNotSelectedApps.setChecked(false);
        mSelectApps.setEnabled(true);
        int count = VpnAppsUtils.getPendingAppsIncludedInVpn(getApplicationContext()).size();
        String summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_include_summary, count, count);
        mSelectApps.setSummary(summary);
    }

    private void tunnelNotSelectedApps() {
        mTunnelAllApps.setChecked(false);
        mTunnelSelectedApps.setChecked(false);
        mTunnelNotSelectedApps.setChecked(true);
        mSelectApps.setEnabled(true);
        int count = VpnAppsUtils.getPendingAppsExcludedFromVpn(getApplicationContext()).size();
        String summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_exclude_summary, count, count);
        mSelectApps.setSummary(summary);
    }

    private boolean supportsRoutingConfiguration() {
        // technically supported after v14 but the earliest preference file with it is v21
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private void exportZircoHistoryBookmarks(Cursor cursor) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);

        final String fileName =  sdf.format(c.getTime()) + ".xml";
        final ProgressDialog progressDialog = ProgressDialog.show(this,
                "Wait",
                "Exporting bookmarks and history");

        final XmlHistoryBookmarksExporter exporter = new XmlHistoryBookmarksExporter(this,
                fileName,
                cursor,
                progressDialog
        );
        new Thread(exporter).start();
    }
}
