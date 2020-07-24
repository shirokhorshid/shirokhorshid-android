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

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import android.widget.Toast;

import com.psiphon3.R;

import org.zirco.providers.ZircoBookmarksContentProvider;
import org.zirco.ui.runnables.XmlHistoryBookmarksExporter;
import org.zirco.utils.ApplicationUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MoreOptionsPreferenceActivity extends MainBase.Activity {
    public static final String INTENT_EXTRA_LANGUAGE_CHANGED = "com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity.LANGUAGE_CHANGED";
    private static final int ZIRCO_WRITE_EXTERNAL_PERMISSION_REQUEST_CODE = 12312;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new MoreOptionsPreferenceFragment())
                    .commit();
        }
    }

    public static class MoreOptionsPreferenceFragment extends PsiphonPreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private Cursor zircoExportCursor;
        ListPreference mLanguageSelector;

        public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferencesFix(savedInstanceState, rootKey);
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

            setupLanguageSelector(preferences);
            setupAbout(preferences);
            setupZircoBookmarksExport(preferences);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
            if (requestCode == ZIRCO_WRITE_EXTERNAL_PERMISSION_REQUEST_CODE &&
                    grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportZircoHistoryBookmarks();
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }

        private void setupZircoBookmarksExport(PreferenceScreen preferenceScreen) {
            zircoExportCursor = ZircoBookmarksContentProvider.getAllRecords(getContext().getContentResolver());
            Preference category = findPreference(getString(R.string.exportZircoPreferenceCategory));
            // Do not show preference if there is no data to export
            if (zircoExportCursor == null || zircoExportCursor.getCount() == 0) {
                preferenceScreen.removePreference(category);
                return;
            }

            category.setVisible(true);
            Preference pref = findPreference(getString(R.string.exportZircoPreference));

            pref.setOnPreferenceClickListener(preference -> {
                if (!ApplicationUtils.ensureWriteStoragePermissionGranted(getActivity(),
                        getString(R.string.PreferencesActivity_ExportHistoryBookmarksPermissionRequestReason),
                        ZIRCO_WRITE_EXTERNAL_PERMISSION_REQUEST_CODE
                )) {
                    Toast.makeText(getActivity(), R.string.Commons_NeedWritePermissions, Toast.LENGTH_LONG).show();
                    return true;
                }
                exportZircoHistoryBookmarks();
                return true;
            });
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

            // Finish back to the StatusActivity and inform the language has changed
            Intent data = new Intent();
            data.putExtra(INTENT_EXTRA_LANGUAGE_CHANGED, true);
            getActivity().setResult(RESULT_OK, data);
            getActivity().finish();
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

        private void setupAbout(PreferenceScreen preferences) {
            Preference pref = preferences.findPreference(getString(R.string.preferenceAbout));
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(EmbeddedValues.INFO_LINK_URL));
            pref.setIntent(browserIntent);
        }

        private void exportZircoHistoryBookmarks() {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);

            final String fileName = sdf.format(c.getTime()) + ".xml";
            final ProgressDialog progressDialog = ProgressDialog.show(getActivity(),
                    this.getResources().getString(R.string.Commons_PleaseWait),
                    this.getResources().getString(R.string.Commons_ExportingHistoryBookmarks));

            final XmlHistoryBookmarksExporter exporter = new XmlHistoryBookmarksExporter(getActivity(),
                    fileName,
                    zircoExportCursor,
                    progressDialog
            );
            new Thread(exporter).start();
        }
    }
}
