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

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.psiphon3.MainActivityViewModel;
import com.psiphon3.R;

import java.util.Set;

public class VpnOptionsPreferenceActivity extends LocalizedActivities.AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new VpnOptionsPreferenceFragment())
                    .commit();
        }

        MainActivityViewModel viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication()))
                .get(MainActivityViewModel.class);
        getLifecycle().addObserver(viewModel);
    }

    public static class VpnOptionsPreferenceFragment extends PsiphonPreferenceFragmentCompat {
        // This is taken from https://developer.android.com/reference/android/provider/Settings#ACTION_VPN_SETTINGS
        // As we target to low of an SDK we cannot reference this constant directly
        private static final String ACTION_VPN_SETTINGS = "android.settings.VPN_SETTINGS";
        RadioButtonPreference mTunnelAllApps;
        RadioButtonPreference mTunnelSelectedApps;
        RadioButtonPreference mTunnelNotSelectedApps;
        Preference mSelectApps;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            if (!Utils.supportsVpnExclusions()) {
                FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    activity.finish();
                }
            }
            super.onCreatePreferences(savedInstanceState, rootKey);
            addPreferencesFromResource(R.xml.vpn_options_preferences);
            final PreferenceScreen preferenceScreen = getPreferenceScreen();
            final PreferenceGetter preferenceGetter = getPreferenceGetter();

            mTunnelAllApps = (RadioButtonPreference) preferenceScreen.findPreference(getString(R.string.preferenceIncludeAllAppsInVpn));
            mTunnelSelectedApps = (RadioButtonPreference) preferenceScreen.findPreference(getString(R.string.preferenceIncludeAppsInVpn));
            mTunnelNotSelectedApps = (RadioButtonPreference) preferenceScreen.findPreference(getString(R.string.preferenceExcludeAppsFromVpn));
            mSelectApps = preferenceScreen.findPreference(getString(R.string.preferenceSelectApps));

            final Preference alwaysOnVpnPref = preferenceScreen.findPreference(getString(R.string.preferenceNavigateToVPNSetting));
            if (Utils.supportsAlwaysOnVPN()) {
                final Intent vpnSettingsIntent = new Intent(ACTION_VPN_SETTINGS);
                alwaysOnVpnPref.setOnPreferenceClickListener(preference -> {
                    try {
                        requireContext().startActivity(vpnSettingsIntent);
                    } catch (ActivityNotFoundException ignored) {
                    }
                    return true;
                });
            } else {
                alwaysOnVpnPref.setEnabled(false);
                alwaysOnVpnPref.setSummary(R.string.vpn_always_on_preference_not_available_summary);
            }

            if (Utils.supportsVpnExclusions()) {
                setupTunnelConfiguration(preferenceScreen, preferenceGetter);
            } else {
                mTunnelAllApps.setEnabled(false);
                mTunnelSelectedApps.setEnabled(false);
                mTunnelNotSelectedApps.setEnabled(false);
                mSelectApps.setEnabled(false);
            }
        }

        private void setupTunnelConfiguration(PreferenceScreen preferences, PreferenceGetter preferenceGetter) {
            // Migrate old VPN exclusions preferences if any
            VpnAppsUtils.migrate(getActivity().getApplicationContext());

            // Also create a snapshot of current VPN exclusion sets. We need this because tunnel restart
            // logic when we return back to main activity from this screen will compare the preferences
            // set in this screen with currently stored preferences in order to make decision if the
            // preferences change needs to trigger a tunnel restart.
            SharedPreferences sharedPreferences = preferences.getPreferenceManager().getSharedPreferences();
            String currentIncludeAppsString = preferenceGetter.getString(getString(R.string.preferenceIncludeAppsInVpnString), "");

            sharedPreferences.edit().putString(getString(R.string.preferenceIncludeAppsInVpnString), currentIncludeAppsString).apply();
            String currentExcludeAppsString = preferenceGetter.getString(getString(R.string.preferenceExcludeAppsFromVpnString), "");
            sharedPreferences.edit().putString(getString(R.string.preferenceExcludeAppsFromVpnString), currentExcludeAppsString).apply();

            if (preferenceGetter.getBoolean(getString(R.string.preferenceIncludeAllAppsInVpn), false)) {
                tunnelAllApps();
            } else if (preferenceGetter.getBoolean(getString(R.string.preferenceIncludeAppsInVpn), false)) {
                tunnelSelectedApps();
            } else {
                tunnelNotSelectedApps();
            }

            mTunnelAllApps.setOnPreferenceClickListener(preference -> {
                tunnelAllApps();
                return true;
            });

            mTunnelSelectedApps.setOnPreferenceClickListener(preference -> {
                tunnelSelectedApps();
                return true;
            });

            mTunnelNotSelectedApps.setOnPreferenceClickListener(preference -> {
                tunnelNotSelectedApps();
                return true;
            });

            mSelectApps.setOnPreferenceClickListener(preference -> {
                final InstalledAppsMultiSelectListPreference installedAppsMultiSelectListPreference =
                        new InstalledAppsMultiSelectListPreference(getActivity(),
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
                                VpnAppsUtils.setPendingAppsToIncludeInVpn(getActivity().getApplicationContext(), selectedApps);
                            } else {
                                new AlertDialog.Builder(getActivity())
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
                                VpnAppsUtils.setPendingAppsToExcludeFromVpn(getActivity().getApplicationContext(), selectedApps);
                            } else {
                                new AlertDialog.Builder(getActivity())
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
            int count = VpnAppsUtils.getPendingAppsIncludedInVpn(getActivity().getApplicationContext()).size();
            String summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_include_summary, count, count);
            mSelectApps.setSummary(summary);
        }

        private void tunnelNotSelectedApps() {
            mTunnelAllApps.setChecked(false);
            mTunnelSelectedApps.setChecked(false);
            mTunnelNotSelectedApps.setChecked(true);
            mSelectApps.setEnabled(true);
            int count = VpnAppsUtils.getPendingAppsExcludedFromVpn(getActivity().getApplicationContext()).size();
            String summary = getResources().getQuantityString(R.plurals.preference_routing_select_apps_to_exclude_summary, count, count);
            mSelectApps.setSummary(summary);
        }
    }
}
