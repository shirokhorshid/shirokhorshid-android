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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.*;
import android.preference.Preference.OnPreferenceClickListener;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Toast;

import com.psiphon3.R;

import com.psiphon3.StatusActivity;
import net.grandcentrix.tray.AppPreferences;
import org.zirco.ui.activities.MainActivity;

import java.util.*;

public class MoreOptionsPreferenceActivity extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceClickListener {

    // This is taken from https://developer.android.com/reference/android/provider/Settings#ACTION_VPN_SETTINGS
    // As we target to low of an SDK we cannot reference this constant directly
    private static final String ACTION_VPN_SETTINGS = "android.settings.VPN_SETTINGS";

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
    DialogPreference mVpnAppExclusions;
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

        mVpnAppExclusions = (DialogPreference) preferences.findPreference(getString(R.string.preferenceExcludeAppsFromVpn));

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

        if (Utils.supportsAlwaysOnVPN()) {
            setupNavigateToVPNSettings(preferences);
        }

        setupLanguageSelector(preferences);

        PreferenceGetter preferenceGetter;

        // Initialize with current shared preferences if restoring from configuration change,
        // otherwise initialize with tray preferences values.
        if (savedInstanceState != null && savedInstanceState.getBoolean("onSaveInstanceState", false)) {
            preferenceGetter = new SharedPreferencesWrapper(PreferenceManager.getDefaultSharedPreferences(this));
        } else {
            preferenceGetter = new AppPreferencesWrapper(new AppPreferences(this));
        }

        mNotificationSound.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithSound), false));
        mNotificationVibration.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithVibrate), false));

        // R.xml.preferences is conditionally loaded at API version 11 and higher from the xml-v11 folder
        // If it isn't null here, we can reasonably assume it can be cast to our MultiSelectListPreference
        if (mVpnAppExclusions != null) {
            String excludedValuesFromPreference = preferenceGetter.getString(getString(R.string.preferenceExcludeAppsFromVpnString), "");

            SharedPreferences.Editor e = preferences.getEditor();
            e.putString(getString(R.string.preferenceExcludeAppsFromVpnString), excludedValuesFromPreference);
            // Use commit (sync) instead of apply (async) to prevent possible race with restarting
            // the tunnel happening before the value is fully persisted to shared preferences
            e.commit();

            if (!excludedValuesFromPreference.isEmpty()) {
                Set<String> excludedValuesSet = new HashSet<>(Arrays.asList(excludedValuesFromPreference.split(",")));
                ((InstalledAppsMultiSelectListPreference) mVpnAppExclusions).setValues(excludedValuesSet);
            } else {
                Set<String> noneExcluded = Collections.emptySet();
                ((InstalledAppsMultiSelectListPreference) mVpnAppExclusions).setValues(noneExcluded);
            }
        }

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
                String proxyHost = (String)newValue;
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
                } catch(NumberFormatException e) {
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

        updatePreferencesScreen();
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

        String currentLocaleLanguageCode = LocaleManager.getLanguage();
        int currentLocaleLangugeIndex = -1;

        if(currentLocaleLanguageCode.equals(LocaleManager.USE_SYSTEM_LANGUAGE_VAL)) {
            currentLocaleLangugeIndex = 0;
        }

        for (int i = 1; i <= locales.length; ++i) {
            // Split the string on the comma
            String[] localeArr = locales[i-1].split(",");
            languageNames[i] = localeArr[0];
            languageCodes[i] = localeArr[1];

            if(localeArr[1] != null && localeArr[1].equals(currentLocaleLanguageCode)) {
                currentLocaleLangugeIndex = i;
            }
        }

        // Entries are displayed to the user, codes are the value used in the backend
        mLanguageSelector.setEntries(languageNames);
        mLanguageSelector.setEntryValues(languageCodes);

        // If current locale is on the list set it selected
        if (currentLocaleLangugeIndex >= 0) {
            mLanguageSelector.setValueIndex(currentLocaleLangugeIndex);
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

    private void updatePreferencesScreen() {
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

    protected void updatePrefsSummary(SharedPreferences sharedPreferences, Preference pref) {
        if (pref instanceof EditTextPreference) {
            // EditPreference
            EditTextPreference editTextPref = (EditTextPreference) pref;
            String summary = editTextPref.getText();
            if (summary != null && !summary.trim().equals("")) {
                //hide passwords
                //http://stackoverflow.com/questions/15044595/preventing-edittextpreference-from-updating-summary-for-inputtype-password
                int inputType = editTextPref.getEditText().getInputType() & InputType.TYPE_MASK_VARIATION;
                boolean isPassword = ((inputType  == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                        ||(inputType  == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        ||(inputType  == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));

                if (isPassword) {
                    editTextPref.setSummary(editTextPref.getText().replaceAll(".", "*"));
                }
                else {
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
        updatePreferencesScreen();

        // If language preference has changed we need to set new locale based on the current
        // preference value and restart the app.
        if (key.equals(getString(R.string.preferenceLanguageSelection))) {
            String languageCode = mLanguageSelector.getValue();
            setLanguageAndRestartApp(languageCode);
        }
    }

    private void setLanguageAndRestartApp(String languageCode) {
        // The LocaleManager will correctly set the resource + store the language preference for the future
        if (languageCode.equals("")) {
            LocaleManager.resetToDefaultLocale(MoreOptionsPreferenceActivity.this);
        } else {
            LocaleManager.setNewLocale(MoreOptionsPreferenceActivity.this, languageCode);
        }

        // Kill the browser instance if it exists.
        // This is required as it's a singleTask activity and isn't recreated when it loses focus.
        if (MainActivity.INSTANCE != null) {
            MainActivity.INSTANCE.finish();
        }

        // Schedule app restart and kill the process
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(this, StatusActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
        }

        System.exit(0);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mUseSystemProxy) {
            mUseSystemProxy.setChecked(true);
            mUseCustomProxy.setChecked(false);
        }
        if (preference == mUseCustomProxy) {
            mUseSystemProxy.setChecked(false);
            mUseCustomProxy.setChecked(true);
        }
        return false;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(getString(R.string.moreOptionsPreferencesName), mode);
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("onSaveInstanceState", true);
        super.onSaveInstanceState(savedInstanceState);
    }
}
