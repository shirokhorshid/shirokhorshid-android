/*
 * Copyright (c) 2021, Psiphon Inc.
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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.psiphon3.MainActivityViewModel;
import com.psiphon3.R;

import java.util.Locale;

public class MoreOptionsPreferenceActivity extends LocalizedActivities.AppCompatActivity {
    public static final String INTENT_EXTRA_LANGUAGE_CHANGED = "com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity.LANGUAGE_CHANGED";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new MoreOptionsPreferenceFragment())
                    .commit();
        }

        MainActivityViewModel viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(MainActivityViewModel.class);
        getLifecycle().addObserver(viewModel);
    }

    public static class MoreOptionsPreferenceFragment extends PsiphonPreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        ListPreference mLanguageSelector;
        EditTextPreference mCdnFrontingCustomIpList;
        EditTextPreference mCdnFrontingCustomSni;
        EditTextPreference mShareProxySocksPort;
        EditTextPreference mShareProxyHttpPort;
        EditTextPreference mShareProxyUsername;
        EditTextPreference mShareProxyPassword;

        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            addPreferencesFromResource(R.xml.more_options_preferences);
            final PreferenceScreen preferences = getPreferenceScreen();
            final PreferenceGetter preferenceGetter = getPreferenceGetter();

            // Notifications
            if (Utils.supportsNotificationSound()) {
                CheckBoxPreference notificationSoundCheckBox =
                        (CheckBoxPreference) preferences.findPreference(getString(R.string.preferenceNotificationsWithSound));
                CheckBoxPreference notificationVibrationCheckBox =
                        (CheckBoxPreference) preferences.findPreference(getString(R.string.preferenceNotificationsWithVibrate));
                notificationSoundCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithSound), false));
                notificationVibrationCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.preferenceNotificationsWithVibrate), false));
            } else {
                // Remove "Notifications" category
                preferences.removePreference(findPreference(getString(R.string.preferencesNotifications)));
            }

            // Advanced
            boolean hasUpgradeChecker = false;
            try {
                Class.forName("com.psiphon3.psiphonlibrary.UpgradeChecker");
                hasUpgradeChecker = true;
            } catch (ClassNotFoundException e) {
                //my class isn't there!
            }
            CheckBoxPreference upgradeWiFiOnlyCheckBox =
                    (CheckBoxPreference) preferences.findPreference(getString(R.string.downloadWifiOnlyPreference));
            if (!EmbeddedValues.IS_PLAY_STORE_BUILD && hasUpgradeChecker) {
                upgradeWiFiOnlyCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.downloadWifiOnlyPreference), false));
            } else {
                preferences.removePreferenceRecursively(getString(R.string.downloadWifiOnlyPreference));
            }
            CheckBoxPreference disableTimeoutsCheckBox =
                    (CheckBoxPreference) preferences.findPreference(getString(R.string.disableTimeoutsPreference));
            disableTimeoutsCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.disableTimeoutsPreference), false));

            CheckBoxPreference autoOpenHomepageCheckBox =
                    (CheckBoxPreference) preferences.findPreference(getString(R.string.autoOpenHomepagePreference));
            autoOpenHomepageCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.autoOpenHomepagePreference), true));

            // TODO: Check if there are any VPN exclusions enabled and inform the user via pref summary?
            //  Maybe even disable the setting if that's the case? Whatever it is do the same when
            //  the 'unsafe traffic' alerts preference is on and user enables VPN exclusions in
            //  VpnOptionsPreferenceActivity
            CheckBoxPreference unsafeTrafficAlertsCheckBox =
                    (CheckBoxPreference) preferences.findPreference(getString(R.string.unsafeTrafficAlertsPreference));
            unsafeTrafficAlertsCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.unsafeTrafficAlertsPreference), false));

            if (Utils.supportsNfc(getContext())) {
                CheckBoxPreference nfcCheckBox =
                        (CheckBoxPreference) preferences.findPreference(getString(R.string.nfcBumpPreference));
                nfcCheckBox.setChecked(preferenceGetter.getBoolean(getString(R.string.nfcBumpPreference), true));
            } else {
                preferences.removePreferenceRecursively(getString(R.string.nfcBumpPreference));
            }

            // Protocol selection
            ListPreference protocolSelectionList =
                    (ListPreference) preferences.findPreference(getString(R.string.protocolSelectionPreference));
            String protocolValue = preferenceGetter.getString(getString(R.string.protocolSelectionPreference), "auto");
            protocolSelectionList.setValue(protocolValue);
            updateProtocolSelectionSummary(protocolSelectionList, protocolValue);

            mCdnFrontingCustomIpList = (EditTextPreference) preferences
                    .findPreference(getString(R.string.cdnFrontingCustomIpListPreference));
            if (mCdnFrontingCustomIpList != null) {
                String customIpList = preferenceGetter.getString(
                        getString(R.string.cdnFrontingCustomIpListPreference), "");
                mCdnFrontingCustomIpList.setText(customIpList);
                mCdnFrontingCustomIpList.setOnBindEditTextListener(editText -> {
                    editText.setSingleLine(false);
                    editText.setMinLines(3);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    editText.setSelection(editText.length());
                });
                updateCdnFrontingCustomIpSummary(mCdnFrontingCustomIpList, customIpList);
                mCdnFrontingCustomIpList.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateCdnFrontingCustomIpSummary(
                            (EditTextPreference) preference, (String) newValue);
                    return true;
                });
            }

            mCdnFrontingCustomSni = (EditTextPreference) preferences
                    .findPreference(getString(R.string.cdnFrontingCustomSniPreference));
            if (mCdnFrontingCustomSni != null) {
                String customSni = preferenceGetter.getString(
                        getString(R.string.cdnFrontingCustomSniPreference), "");
                mCdnFrontingCustomSni.setText(customSni);
                mCdnFrontingCustomSni.setOnBindEditTextListener(editText -> {
                    editText.setSingleLine(false);
                    editText.setMinLines(2);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    editText.setSelection(editText.length());
                });
                updateCdnFrontingCustomSniSummary(mCdnFrontingCustomSni, customSni);
                mCdnFrontingCustomSni.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateCdnFrontingCustomSniSummary(
                            (EditTextPreference) preference, (String) newValue);
                    return true;
                });
            }

            // Beast mode (aggressive establishment)
            SwitchPreference beastModeSwitch =
                    (SwitchPreference) preferences.findPreference(getString(R.string.beastModePreference));
            if (beastModeSwitch != null) {
                beastModeSwitch.setChecked(
                        preferenceGetter.getBoolean(getString(R.string.beastModePreference), true));
            }

            // Set initial protocol-specific category visibility.
            PreferenceCategory cdnFrontingCategory =
                    (PreferenceCategory) preferences.findPreference("cdnFrontingCategory");
            if (cdnFrontingCategory != null) {
                cdnFrontingCategory.setVisible(isCdnFrontingRelevantProtocol(protocolValue));
            }

            PreferenceCategory conduitCategory =
                    (PreferenceCategory) preferences.findPreference("conduitCategory");
            if (conduitCategory != null) {
                conduitCategory.setVisible(isConduitRelevantProtocol(protocolValue));
            }

            protocolSelectionList.setOnPreferenceChangeListener((preference, newValue) -> {
                String protocol = (String) newValue;
                updateProtocolSelectionSummary((ListPreference) preference, protocol);
                if (cdnFrontingCategory != null) {
                    cdnFrontingCategory.setVisible(isCdnFrontingRelevantProtocol(protocol));
                }
                // Show conduit settings only when protocol is auto or conduit
                if (conduitCategory != null) {
                    conduitCategory.setVisible(isConduitRelevantProtocol(protocol));
                }
                return true;
            });

            setupLanguageSelector(preferences);
            setupDisguise(preferences);
            setupConduitSettings(preferences, preferenceGetter);
            setupNetworkSharing(preferences, preferenceGetter);
            setupAbouts(preferences);
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

        @SuppressWarnings("deprecation")
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
            trimNetworkSharingPort(sharedPreferences, key, getString(R.string.shareProxyOnNetworkSocksPortPreference));
            trimNetworkSharingPort(sharedPreferences, key, getString(R.string.shareProxyOnNetworkHttpPortPreference));
            trimNetworkSharingUsername(sharedPreferences, key);
            updateNetworkSharingPreferences();

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
            LocaleManager localeManager = LocaleManager.getInstance(getActivity());
            if (languageCode.equals("")) {
                localeManager.resetToSystemLocale(getActivity());
            } else {
                localeManager.setNewLocale(getActivity(), languageCode);
            }

            // Signal tunnel service
            ((LocalizedActivities.AppCompatActivity) requireActivity())
                    .getTunnelServiceInteractor()
                    .sendLocaleChangedMessage();
            // Finish back to the MainActivity and inform the language has changed
            Intent data = new Intent();
            data.putExtra(INTENT_EXTRA_LANGUAGE_CHANGED, true);
            requireActivity().setResult(RESULT_OK, data);
            requireActivity().finish();
        }

        private void updateProtocolSelectionSummary(ListPreference preference, String value) {
            switch (value) {
                case "conduit":
                    preference.setSummary(getString(R.string.protocolSelectionSummaryConduit));
                    break;
                case "cdn_fronting":
                    preference.setSummary(getString(R.string.protocolSelectionSummaryCdnFronting));
                    break;
                case "direct":
                    preference.setSummary(getString(R.string.protocolSelectionSummaryDirect));
                    break;
                case "auto":
                default:
                    preference.setSummary(getString(R.string.protocolSelectionSummaryAuto));
                    break;
            }
        }

        private boolean isConduitRelevantProtocol(String protocol) {
            return "auto".equals(protocol) || "conduit".equals(protocol);
        }

        private boolean isCdnFrontingRelevantProtocol(String protocol) {
            return "auto".equals(protocol) ||
                    "direct".equals(protocol) ||
                    "cdn_fronting".equals(protocol);
        }

        private void updateCdnFrontingCustomIpSummary(EditTextPreference preference, String value) {
            int count = countCdnFrontingIpEntries(value);
            if (count > 0) {
                preference.setSummary(getString(
                        R.string.cdnFrontingCustomIpListPreferenceSummaryConfigured, count));
            } else {
                preference.setSummary(getString(R.string.cdnFrontingCustomIpListPreferenceSummary));
            }
        }

        private int countCdnFrontingIpEntries(String value) {
            if (TextUtils.isEmpty(value)) {
                return 0;
            }
            int count = 0;
            for (String entry : value.split("[\\s,;]+")) {
                String candidate = entry.trim();
                if (isValidIPv4Address(candidate) || isValidIPv4Cidr(candidate)) {
                    count++;
                }
            }
            return count;
        }

        private void updateCdnFrontingCustomSniSummary(EditTextPreference preference, String value) {
            int count = countCdnFrontingSniEntries(value);
            if (count == 0) {
                if (TextUtils.isEmpty(value) || TextUtils.isEmpty(value.trim())) {
                    preference.setSummary(getString(R.string.cdnFrontingCustomSniPreferenceSummary));
                } else {
                    preference.setSummary(getString(R.string.cdnFrontingCustomSniPreferenceSummaryInvalid));
                }
                return;
            }
            if (count == 1) {
                preference.setSummary(getString(
                        R.string.cdnFrontingCustomSniPreferenceSummaryConfigured,
                        firstCdnFrontingSniEntry(value)));
            } else {
                preference.setSummary(getString(
                        R.string.cdnFrontingCustomSniPreferenceSummaryConfiguredMultiple, count));
            }
        }

        private int countCdnFrontingSniEntries(String value) {
            if (TextUtils.isEmpty(value)) {
                return 0;
            }
            int count = 0;
            for (String entry : value.split("[\\s,;]+")) {
                if (!TextUtils.isEmpty(normalizeHostname(entry))) {
                    count++;
                }
            }
            return count;
        }

        private String firstCdnFrontingSniEntry(String value) {
            if (TextUtils.isEmpty(value)) {
                return "";
            }
            for (String entry : value.split("[\\s,;]+")) {
                String sni = normalizeHostname(entry);
                if (!TextUtils.isEmpty(sni)) {
                    return sni;
                }
            }
            return "";
        }

        private String normalizeHostname(String hostname) {
            if (TextUtils.isEmpty(hostname)) {
                return "";
            }
            String normalizedHostname = hostname.trim().toLowerCase(Locale.US);
            if (normalizedHostname.endsWith(".")) {
                normalizedHostname = normalizedHostname.substring(0, normalizedHostname.length() - 1);
            }
            if (!isValidHostname(normalizedHostname)) {
                return "";
            }
            return normalizedHostname;
        }

        private boolean isValidIPv4Address(String ipAddress) {
            if (TextUtils.isEmpty(ipAddress)) {
                return false;
            }
            String[] parts = ipAddress.split("\\.", -1);
            if (parts.length != 4) {
                return false;
            }

            for (String part : parts) {
                if (part.isEmpty() || part.length() > 3) {
                    return false;
                }
                for (int i = 0; i < part.length(); i++) {
                    if (!Character.isDigit(part.charAt(i))) {
                        return false;
                    }
                }
                try {
                    int parsed = Integer.parseInt(part);
                    if (parsed < 0 || parsed > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return true;
        }

        private boolean isValidIPv4Cidr(String cidr) {
            if (TextUtils.isEmpty(cidr)) {
                return false;
            }
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2 || !isValidIPv4Address(parts[0])) {
                return false;
            }
            try {
                int prefix = Integer.parseInt(parts[1]);
                return prefix >= 0 && prefix <= 32;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private boolean isValidHostname(String hostname) {
            if (TextUtils.isEmpty(hostname) || hostname.length() > 253 ||
                    isValidIPv4Address(hostname)) {
                return false;
            }

            String normalizedHostname = hostname;
            if (normalizedHostname.endsWith(".")) {
                normalizedHostname = normalizedHostname.substring(0, normalizedHostname.length() - 1);
            }
            if (normalizedHostname.isEmpty()) {
                return false;
            }

            String[] labels = normalizedHostname.split("\\.", -1);
            for (String label : labels) {
                if (label.isEmpty() || label.length() > 63 ||
                        label.startsWith("-") || label.endsWith("-")) {
                    return false;
                }
                for (int i = 0; i < label.length(); i++) {
                    char character = label.charAt(i);
                    if (!Character.isLetterOrDigit(character) && character != '-') {
                        return false;
                    }
                }
            }

            return true;
        }

        private void setupDisguise(PreferenceScreen preferences) {
            // Disguise identity list preference
            ListPreference disguiseList = (ListPreference) preferences.findPreference(
                    DisguiseManager.PREF_DISGUISE_IDENTITY);
            if (disguiseList != null) {
                // Build entries and values arrays
                CharSequence[] entries = new CharSequence[]{
                        getString(R.string.disguise_identity_default),
                        getString(R.string.disguise_name_calculator),
                        getString(R.string.disguise_name_weather),
                        getString(R.string.disguise_name_notes),
                        getString(R.string.disguise_name_clock),
                        getString(R.string.disguise_name_booru)
                };
                CharSequence[] entryValues = new CharSequence[]{
                        DisguiseManager.IDENTITY_DEFAULT,
                        DisguiseManager.IDENTITY_CALCULATOR,
                        DisguiseManager.IDENTITY_WEATHER,
                        DisguiseManager.IDENTITY_NOTES,
                        DisguiseManager.IDENTITY_CLOCK,
                        DisguiseManager.IDENTITY_BOORU
                };
                disguiseList.setEntries(entries);
                disguiseList.setEntryValues(entryValues);

                // Set current value
                String currentIdentity = DisguiseManager.getCurrentIdentity(requireContext());
                disguiseList.setValue(currentIdentity);
                updateDisguiseSummary(disguiseList, currentIdentity);

                disguiseList.setOnPreferenceChangeListener((preference, newValue) -> {
                    String identity = (String) newValue;
                    String oldIdentity = DisguiseManager.getCurrentIdentity(requireContext());
                    if (!identity.equals(oldIdentity)) {
                        // Show warning dialog before switching
                        new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.preference_disguise_identity_title))
                                .setMessage(getString(R.string.disguise_switch_warning))
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    DisguiseManager.switchIdentity(requireContext(), identity);
                                    updateDisguiseSummary((ListPreference) preference, identity);
                                    ((ListPreference) preference).setValue(identity);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        return false; // Don't auto-apply, we handle it in the dialog
                    }
                    return true;
                });
            }

            // Stealth notifications checkbox
            CheckBoxPreference stealthNotifCheckBox = (CheckBoxPreference) preferences.findPreference(
                    DisguiseManager.PREF_STEALTH_NOTIFICATIONS);
            if (stealthNotifCheckBox != null) {
                stealthNotifCheckBox.setChecked(
                        DisguiseManager.isStealthNotificationsEnabled(requireContext()));
            }
        }

        private void updateDisguiseSummary(ListPreference preference, String identity) {
            preference.setSummary(
                    DisguiseManager.getIdentityDisplayName(requireContext(), identity));
        }

        private void setupConduitSettings(PreferenceScreen preferences, PreferenceGetter preferenceGetter) {
            // Conduit mode selection
            ListPreference conduitModeList =
                    (ListPreference) preferences.findPreference(getString(R.string.conduitModePreference));
            if (conduitModeList != null) {
                String conduitModeValue = preferenceGetter.getString(getString(R.string.conduitModePreference), "auto");
                conduitModeList.setValue(conduitModeValue);
                updateConduitModeSummary(conduitModeList, conduitModeValue);
                conduitModeList.setOnPreferenceChangeListener((preference, newValue) -> {
                    String mode = (String) newValue;
                    updateConduitModeSummary((ListPreference) preference, mode);
                    // Show/hide timeout based on mode
                    ListPreference timeoutPref = (ListPreference) preferences.findPreference(
                            getString(R.string.conduitTimeoutPreference));
                    if (timeoutPref != null) {
                        timeoutPref.setVisible("auto".equals(mode));
                    }
                    return true;
                });
            }

            // Conduit timeout selection
            ListPreference conduitTimeoutList =
                    (ListPreference) preferences.findPreference(getString(R.string.conduitTimeoutPreference));
            if (conduitTimeoutList != null) {
                String timeoutValue = preferenceGetter.getString(getString(R.string.conduitTimeoutPreference), "180");
                conduitTimeoutList.setValue(timeoutValue);
                updateConduitTimeoutSummary(conduitTimeoutList, timeoutValue);
                conduitTimeoutList.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateConduitTimeoutSummary((ListPreference) preference, (String) newValue);
                    return true;
                });

                // Hide timeout if mode is not auto
                String currentMode = conduitModeList != null ?
                        conduitModeList.getValue() : "auto";
                conduitTimeoutList.setVisible("auto".equals(currentMode));
            }
        }

        private void updateConduitModeSummary(ListPreference preference, String value) {
            switch (value) {
                case "shirokhorshid":
                    preference.setSummary(getString(R.string.conduitModeSummaryShirOKhorshid));
                    break;
                case "public":
                    preference.setSummary(getString(R.string.conduitModeSummaryPublic));
                    break;
                case "auto":
                default:
                    preference.setSummary(getString(R.string.conduitModeSummaryAuto));
                    break;
            }
        }

        private void updateConduitTimeoutSummary(ListPreference preference, String value) {
            String label;
            switch (value) {
                case "120":
                    label = getString(R.string.conduit_timeout_2min);
                    break;
                case "300":
                    label = getString(R.string.conduit_timeout_5min);
                    break;
                case "600":
                    label = getString(R.string.conduit_timeout_10min);
                    break;
                case "180":
                default:
                    label = getString(R.string.conduit_timeout_3min);
                    break;
            }
            preference.setSummary(String.format(getString(R.string.conduitTimeoutSummary), label));
        }

        private void setupNetworkSharing(PreferenceScreen preferences, PreferenceGetter preferenceGetter) {
            SwitchPreference shareProxySwitch =
                    (SwitchPreference) preferences.findPreference(getString(R.string.shareProxyOnNetworkPreference));
            mShareProxySocksPort = (EditTextPreference) preferences
                    .findPreference(getString(R.string.shareProxyOnNetworkSocksPortPreference));
            mShareProxyHttpPort = (EditTextPreference) preferences
                    .findPreference(getString(R.string.shareProxyOnNetworkHttpPortPreference));
            mShareProxyUsername = (EditTextPreference) preferences
                    .findPreference(getString(R.string.shareProxyOnNetworkUsernamePreference));
            mShareProxyPassword = (EditTextPreference) preferences
                    .findPreference(getString(R.string.shareProxyOnNetworkPasswordPreference));

            setupNetworkSharingPortPreference(
                    mShareProxySocksPort,
                    preferenceGetter.getString(getString(R.string.shareProxyOnNetworkSocksPortPreference), ""));
            setupNetworkSharingPortPreference(
                    mShareProxyHttpPort,
                    preferenceGetter.getString(getString(R.string.shareProxyOnNetworkHttpPortPreference), ""));
            setupNetworkSharingUsernamePreference(
                    preferenceGetter.getString(getString(R.string.shareProxyOnNetworkUsernamePreference), ""));
            setupNetworkSharingPasswordPreference(
                    preferenceGetter.getString(getString(R.string.shareProxyOnNetworkPasswordPreference), ""));

            if (shareProxySwitch != null) {
                shareProxySwitch.setChecked(
                        preferenceGetter.getBoolean(getString(R.string.shareProxyOnNetworkPreference), false));
                updateNetworkSharingPreferences();

                shareProxySwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    if (enabled) {
                        // Show security warning dialog before enabling
                        new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.share_proxy_security_warning_title))
                                .setMessage(getString(R.string.share_proxy_security_warning_message))
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    // Manually persist the value since we returned false
                                    shareProxySwitch.setChecked(true);
                                    shareProxySwitch.getSharedPreferences().edit()
                                            .putBoolean(getString(R.string.shareProxyOnNetworkPreference), true)
                                            .apply();
                                    updateNetworkSharingPreferences();
                                    // Request tunnel restart to apply the new setting
                                    restartTunnelForNetworkSharing();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        return false; // Don't auto-apply, we handle it in the dialog
                    } else {
                        // Disabling doesn't need confirmation, but does need a tunnel restart
                        restartTunnelForNetworkSharing();
                        return true;
                    }
                });
            }
        }

        private void setupNetworkSharingPortPreference(EditTextPreference preference, String value) {
            if (preference == null) {
                return;
            }
            preference.setText(value == null ? "" : value.trim());
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setSelection(editText.length());
            });
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                String port = ((String) newValue).trim();
                if (isBlankOrValidPort(port)) {
                    return true;
                }
                Toast.makeText(getActivity(), R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT).show();
                return false;
            });
            updateNetworkSharingPortSummary(preference);
        }

        private void setupNetworkSharingUsernamePreference(String value) {
            if (mShareProxyUsername == null) {
                return;
            }
            mShareProxyUsername.setText(value == null ? "" : value.trim());
            mShareProxyUsername.setOnBindEditTextListener(editText -> {
                editText.setSingleLine(true);
                editText.setSelection(editText.length());
            });
            updateNetworkSharingUsernameSummary();
        }

        private void setupNetworkSharingPasswordPreference(String value) {
            if (mShareProxyPassword == null) {
                return;
            }
            mShareProxyPassword.setText(value == null ? "" : value);
            mShareProxyPassword.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editText.setSingleLine(true);
                editText.setSelection(editText.length());
            });
            updateNetworkSharingPasswordSummary();
        }

        private boolean isBlankOrValidPort(String port) {
            if (TextUtils.isEmpty(port)) {
                return true;
            }
            try {
                return UpstreamProxySettings.isValidProxyPort(Integer.parseInt(port));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void trimNetworkSharingPort(SharedPreferences sharedPreferences, String changedKey, String portKey) {
            if (!portKey.equals(changedKey)) {
                return;
            }
            String value = sharedPreferences.getString(portKey, "");
            if (value == null) {
                value = "";
            }
            String trimmed = value.trim();
            if (!trimmed.equals(value)) {
                sharedPreferences.edit().putString(portKey, trimmed).apply();
                EditTextPreference preference = findPreference(portKey);
                if (preference != null) {
                    preference.setText(trimmed);
                }
            }
        }

        private void trimNetworkSharingUsername(SharedPreferences sharedPreferences, String changedKey) {
            String usernameKey = getString(R.string.shareProxyOnNetworkUsernamePreference);
            if (!usernameKey.equals(changedKey)) {
                return;
            }
            String value = sharedPreferences.getString(usernameKey, "");
            if (value == null) {
                value = "";
            }
            String trimmed = value.trim();
            if (!trimmed.equals(value)) {
                sharedPreferences.edit().putString(usernameKey, trimmed).apply();
                if (mShareProxyUsername != null) {
                    mShareProxyUsername.setText(trimmed);
                }
            }
        }

        private void updateNetworkSharingPreferences() {
            SwitchPreference shareProxySwitch =
                    findPreference(getString(R.string.shareProxyOnNetworkPreference));
            boolean sharingEnabled = shareProxySwitch != null && shareProxySwitch.isChecked();
            updateNetworkSharingPortPreference(mShareProxySocksPort, sharingEnabled);
            updateNetworkSharingPortPreference(mShareProxyHttpPort, sharingEnabled);
            updateNetworkSharingTextPreference(mShareProxyUsername, sharingEnabled);
            updateNetworkSharingTextPreference(mShareProxyPassword, sharingEnabled);
        }

        private void updateNetworkSharingPortPreference(EditTextPreference preference, boolean sharingEnabled) {
            if (preference == null) {
                return;
            }
            preference.setVisible(sharingEnabled);
            preference.setEnabled(sharingEnabled);
            updateNetworkSharingPortSummary(preference);
        }

        private void updateNetworkSharingTextPreference(EditTextPreference preference, boolean sharingEnabled) {
            if (preference == null) {
                return;
            }
            preference.setVisible(sharingEnabled);
            preference.setEnabled(sharingEnabled);
            if (preference == mShareProxyPassword) {
                updateNetworkSharingPasswordSummary();
            } else {
                updateNetworkSharingUsernameSummary();
            }
        }

        private void updateNetworkSharingPortSummary(EditTextPreference preference) {
            String value = preference.getText();
            if (TextUtils.isEmpty(value)) {
                if (preference.getKey().equals(getString(R.string.shareProxyOnNetworkSocksPortPreference))) {
                    preference.setSummary(R.string.preference_share_proxy_socks_port_summary);
                } else {
                    preference.setSummary(R.string.preference_share_proxy_http_port_summary);
                }
            } else {
                preference.setSummary(value);
            }
        }

        private void updateNetworkSharingUsernameSummary() {
            if (mShareProxyUsername == null) {
                return;
            }
            String value = mShareProxyUsername.getText();
            if (TextUtils.isEmpty(value)) {
                mShareProxyUsername.setSummary(R.string.preference_share_proxy_username_summary);
            } else {
                mShareProxyUsername.setSummary(value);
            }
        }

        private void updateNetworkSharingPasswordSummary() {
            if (mShareProxyPassword == null) {
                return;
            }
            String value = mShareProxyPassword.getText();
            if (TextUtils.isEmpty(value)) {
                mShareProxyPassword.setSummary(R.string.preference_share_proxy_password_summary);
            } else {
                mShareProxyPassword.setSummary(value);
            }
        }

        private void restartTunnelForNetworkSharing() {
            TunnelServiceInteractor tunnelServiceInteractor =
                    ((LocalizedActivities.AppCompatActivity) requireActivity())
                            .getTunnelServiceInteractor();
            if (tunnelServiceInteractor != null) {
                tunnelServiceInteractor.scheduleVpnServiceRestart(requireContext());
            }
        }

        private void setupLanguageSelector(PreferenceScreen preferences) {
            // Get the preference view and create the locale manager with the app's context.
            // Cannot use this activity as the context as we also need MainActivity to pick up on it.
            mLanguageSelector = (ListPreference) preferences.findPreference(getString(R.string.preferenceLanguageSelection));

            // Collect the string array of <language name>,<language code>
            String[] locales = getResources().getStringArray(R.array.languages);
            CharSequence[] languageNames = new CharSequence[locales.length + 1];
            CharSequence[] languageCodes = new CharSequence[locales.length + 1];

            // Setup the "Default" locale
            languageNames[0] = getString(R.string.preference_language_default_language);
            languageCodes[0] = "";

            LocaleManager localeManager = LocaleManager.getInstance(getActivity());
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

        private void setupAbouts(PreferenceScreen preferences) {
            setupAbout(preferences.findPreference(getString(R.string.preferenceAbout)), EmbeddedValues.INFO_LINK_URL);
            setupAbout(preferences.findPreference(getString(R.string.preferenceAboutMalAware)), getString(R.string.AboutMalAwareLink));
            setupAboutShirOKhorshid(preferences.findPreference("preferenceAboutShirOKhorshid"));
        }

        private void setupAbout(Preference pref, String aboutURL) {
            if (pref == null) return;
            final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(aboutURL));
            pref.setOnPreferenceClickListener(preference -> {
                try {
                    requireContext().startActivity(browserIntent);
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            });
        }

        private void setupAboutShirOKhorshid(Preference pref) {
            if (pref == null) return;
            pref.setOnPreferenceClickListener(preference -> {
                SpannableString message = new SpannableString(
                        getString(R.string.unofficial_disclaimer_about_body));
                Linkify.addLinks(message, Linkify.WEB_URLS);

                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.unofficial_disclaimer_about_title))
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(getString(R.string.official_psiphon_link_label), (d, which) -> {
                            try {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.official_psiphon_link)));
                                requireContext().startActivity(browserIntent);
                            } catch (ActivityNotFoundException ignored) {
                            }
                        })
                        .create();
                dialog.show();

                // Make links clickable in the dialog message
                TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) {
                    messageView.setMovementMethod(LinkMovementMethod.getInstance());
                }
                return true;
            });
        }
    }
}
