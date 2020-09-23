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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.psiphon3.MainActivityViewModel;
import com.psiphon3.R;

import java.util.ArrayList;

public class ProxyOptionsPreferenceActivity extends LocalizedActivities.AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new ProxyOptionsPreferenceFragment())
                    .commit();
        }

        MainActivityViewModel viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(MainActivityViewModel.class);
        getLifecycle().addObserver(viewModel);
    }

    public static class ProxyOptionsPreferenceFragment extends PsiphonPreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        CheckBoxPreference useProxy;
        RadioButtonPreference useSystemProxy;
        RadioButtonPreference useCustomProxy;
        CheckBoxPreference useProxyAuthentication;
        EditTextPreference proxyHost;
        EditTextPreference proxyPort;
        EditTextPreference proxyUsername;
        EditTextPreference proxyPassword;
        EditTextPreference proxyDomain;
        private ArrayList<EditTextPreference> editTextPreferences;
        private Bundle defaultSummaryBundle = new Bundle();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            addPreferencesFromResource(R.xml.proxy_options_preferences);
            final PreferenceScreen preferences = getPreferenceScreen();

            useProxy = (CheckBoxPreference) preferences.findPreference(getString(R.string.useProxySettingsPreference));
            useSystemProxy = (RadioButtonPreference) preferences
                    .findPreference(getString(R.string.useSystemProxySettingsPreference));
            useCustomProxy = (RadioButtonPreference) preferences
                    .findPreference(getString(R.string.useCustomProxySettingsPreference));

            proxyHost = (EditTextPreference) preferences
                    .findPreference(getString(R.string.useCustomProxySettingsHostPreference));
            proxyPort = (EditTextPreference) preferences
                    .findPreference(getString(R.string.useCustomProxySettingsPortPreference));
            proxyPort.setOnBindEditTextListener(editText ->
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER));

            useProxyAuthentication = (CheckBoxPreference) preferences
                    .findPreference(getString(R.string.useProxyAuthenticationPreference));
            proxyUsername = (EditTextPreference) preferences
                    .findPreference(getString(R.string.useProxyUsernamePreference));
            proxyPassword = (EditTextPreference) preferences
                    .findPreference(getString(R.string.useProxyPasswordPreference));
            proxyPassword.setOnBindEditTextListener(editText ->
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            proxyDomain = (EditTextPreference) preferences
                    .findPreference(getString(R.string.useProxyDomainPreference));

            editTextPreferences = new ArrayList<>();
            editTextPreferences.add(proxyHost);
            editTextPreferences.add(proxyPort);
            editTextPreferences.add(proxyUsername);
            editTextPreferences.add(proxyPassword);
            editTextPreferences.add(proxyDomain);

            // Collect default summaries of EditTextPreferences
            for (Preference pref : editTextPreferences) {
                if (pref != null) {
                    defaultSummaryBundle.putCharSequence(pref.getKey(), pref.getSummary());
                }
            }

            final PreferenceGetter preferenceGetter = getPreferenceGetter();
            useProxy.setChecked(preferenceGetter.getBoolean(getString(R.string.useProxySettingsPreference), false));
            // set use system proxy preference by default
            useSystemProxy.setChecked(preferenceGetter.getBoolean(getString(R.string.useSystemProxySettingsPreference), true));
            useCustomProxy.setChecked(!useSystemProxy.isChecked());

            // Make sure proxy hostname is trimmed when retrieved directly from preferences
            proxyHost.setText(preferenceGetter.getString(getString(R.string.useCustomProxySettingsHostPreference), "").trim());
            proxyPort.setText(preferenceGetter.getString(getString(R.string.useCustomProxySettingsPortPreference), ""));
            useProxyAuthentication.setChecked(preferenceGetter.getBoolean(getString(R.string.useProxyAuthenticationPreference), false));
            proxyUsername.setText(preferenceGetter.getString(getString(R.string.useProxyUsernamePreference), ""));
            proxyPassword.setText(preferenceGetter.getString(getString(R.string.useProxyPasswordPreference), ""));
            proxyDomain.setText(preferenceGetter.getString(getString(R.string.useProxyDomainPreference), ""));

            useSystemProxy.setOnPreferenceChangeListener((preference, o) -> {
                useSystemProxy.setChecked(true);
                useCustomProxy.setChecked(false);
                return false;
            });

            useCustomProxy.setOnPreferenceChangeListener((preference, o) -> {
                useSystemProxy.setChecked(false);
                useCustomProxy.setChecked(true);
                return false;
            });

            proxyHost.setOnPreferenceChangeListener((preference, newValue) -> {
                // Trim the hostname value before passing to validation.
                // The value will be checked upon again in onSharedPreferenceChanged before
                // it is stored and trimmed if needed.
                String proxyHost = ((String) newValue).trim();
                if (!UpstreamProxySettings.isValidProxyHostName(proxyHost)) {
                    Toast toast = Toast.makeText(getActivity(), R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT);
                    toast.show();
                    return false;
                }
                return true;
            });

            proxyPort.setOnPreferenceChangeListener((preference, newValue) -> {
                int proxyPort;
                try {
                    proxyPort = Integer.parseInt((String) newValue);
                } catch (NumberFormatException e) {
                    proxyPort = 0;
                }
                if (UpstreamProxySettings.isValidProxyPort(proxyPort)) {
                    return true;
                }
                Toast toast = Toast.makeText(getActivity(), R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT);
                toast.show();
                return false;
            });

            updateProxyPreferencesUI();
        }

        @Override
        public void onResume() {
            super.onResume();
            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            // Unregister the listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Trim proxy hostname if needed.
            if (getString(R.string.useCustomProxySettingsHostPreference).equals(key)) {
                String val = sharedPreferences.getString(getString(R.string.useCustomProxySettingsHostPreference), "");
                String trimmed = val.trim();
                if (!trimmed.equals(val)) {
                    proxyHost.setText(trimmed);
                    sharedPreferences.edit().putString(key, trimmed).apply();
                }
            }
            updateProxyPreferencesUI();
        }

        private void disableCustomProxySettings() {
            proxyHost.setEnabled(false);
            proxyPort.setEnabled(false);
            useProxyAuthentication.setEnabled(false);
            disableProxyAuthenticationSettings();
        }

        private void enableCustomProxySettings() {
            proxyHost.setEnabled(true);
            proxyPort.setEnabled(true);
            useProxyAuthentication.setEnabled(true);
            enableProxyAuthenticationSettings();
        }

        private void disableProxyAuthenticationSettings() {
            proxyUsername.setEnabled(false);
            proxyPassword.setEnabled(false);
            proxyDomain.setEnabled(false);
        }

        private void enableProxyAuthenticationSettings() {
            proxyUsername.setEnabled(true);
            proxyPassword.setEnabled(true);
            proxyDomain.setEnabled(true);
        }

        private void disableProxySettings() {
            useSystemProxy.setEnabled(false);
            useCustomProxy.setEnabled(false);
            disableCustomProxySettings();
        }

        private void enableProxySettings() {
            useSystemProxy.setEnabled(true);
            useCustomProxy.setEnabled(true);
            enableCustomProxySettings();
        }

        private void updateProxyPreferencesUI() {
            if (!useProxy.isChecked()) {
                disableProxySettings();
            } else {
                enableProxySettings();
                if (useSystemProxy.isChecked()) {
                    disableCustomProxySettings();
                } else {
                    enableCustomProxySettings();
                    if (useProxyAuthentication.isChecked()) {
                        enableProxyAuthenticationSettings();
                    } else {
                        disableProxyAuthenticationSettings();
                    }
                }
            }
            // Update summaries
            for (EditTextPreference editTextPref : editTextPreferences) {
                if (editTextPref != null) {
                    String summary = editTextPref.getText();
                    if (summary != null && !summary.trim().equals("")) {
                        boolean isPassword = editTextPref.getKey().equals(getString(R.string.useProxyPasswordPreference));
                        if (isPassword) {
                            editTextPref.setSummary(editTextPref.getText().replaceAll(".", "*"));
                        } else {
                            editTextPref.setSummary(editTextPref.getText());
                        }
                    } else {
                        editTextPref.setSummary((CharSequence) defaultSummaryBundle.get(editTextPref.getKey()));
                    }
                }
            }
        }
    }
}
