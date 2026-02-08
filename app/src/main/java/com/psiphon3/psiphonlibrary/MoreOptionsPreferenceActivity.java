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
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.psiphon3.MainActivityViewModel;
import com.psiphon3.R;

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
            protocolSelectionList.setOnPreferenceChangeListener((preference, newValue) -> {
                updateProtocolSelectionSummary((ListPreference) preference, (String) newValue);
                return true;
            });

            setupLanguageSelector(preferences);
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
                case "direct":
                    preference.setSummary(getString(R.string.protocolSelectionSummaryDirect));
                    break;
                case "auto":
                default:
                    preference.setSummary(getString(R.string.protocolSelectionSummaryAuto));
                    break;
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
