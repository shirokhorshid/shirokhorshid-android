package com.psiphon3;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity;
import com.psiphon3.psiphonlibrary.ProxyOptionsPreferenceActivity;
import com.psiphon3.psiphonlibrary.PsiphonConstants;
import com.psiphon3.psiphonlibrary.PsiphonPreferenceFragmentCompat;
import com.psiphon3.psiphonlibrary.RegionListPreference;
import com.psiphon3.psiphonlibrary.UpstreamProxySettings;
import com.psiphon3.psiphonlibrary.Utils;
import com.psiphon3.psiphonlibrary.VpnAppsUtils;
import com.psiphon3.psiphonlibrary.VpnOptionsPreferenceActivity;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.SharedPreferencesImport;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static android.content.Context.MODE_PRIVATE;

public class OptionsTabFragment extends PsiphonPreferenceFragmentCompat {
    private static final int REQUEST_CODE_VPN_PREFERENCES = 100;
    private static final int REQUEST_CODE_PROXY_PREFERENCES = 101;
    private static final int REQUEST_CODE_MORE_PREFERENCES = 102;

    private RegionListPreference regionListPreference;
    private Preference vpnOptionsPreference;
    private Preference proxyOptionsPreference;
    private AppPreferences multiProcessPreferences;
    private MainActivityViewModel viewModel;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        super.onCreatePreferences(bundle, s);
        addPreferencesFromResource(R.xml.settings_preferences_screen);

        Context context = getPreferenceManager().getContext();
        multiProcessPreferences = new AppPreferences(context);

        regionListPreference = findPreference(getContext().getString(R.string.regionPreferenceKey));
        regionListPreference.setOnRegionSelectedListener(regionCode -> {
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                onRegionSelected(regionCode);
                regionListPreference.setCurrentRegionFromPreferences();
            }
        });

        Preference feedbackPreference = findPreference(getContext().getString(R.string.feedbackPreferenceKey));
        feedbackPreference.setIntent(new Intent(getActivity(), FeedbackActivity.class));

        Preference moreOptionsPreference = findPreference(getContext().getString(R.string.moreOptionsPreferenceKey));
        moreOptionsPreference.setOnPreferenceClickListener(__ -> {
            final FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                startActivityForResult(new Intent(getActivity(),
                        MoreOptionsPreferenceActivity.class), REQUEST_CODE_MORE_PREFERENCES);
            }
            return true;
        });

        vpnOptionsPreference = findPreference(getContext().getString(R.string.vpnOptionsPreferenceKey));
        if (Utils.supportsVpnExclusions()) {
            vpnOptionsPreference.setOnPreferenceClickListener(__ -> {
                final FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    startActivityForResult(new Intent(getActivity(),
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
                startActivityForResult(new Intent(getActivity(),
                        ProxyOptionsPreferenceActivity.class), REQUEST_CODE_PROXY_PREFERENCES);
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // onResume is called after onCreatePreferences and after onActivityResult
        // so this is a pretty good place to update displayed preferences summaries
        setSummaryFromPreferences();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);


        // Observe available regions set changes.
        compositeDisposable.add(viewModel.updateAvailableRegionsFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(__ -> regionListPreference.setCurrentRegionFromPreferences())
                .subscribe());

        // Observe 'Open VPN settings' signal from legacy BOM dialog clicks or from
        // deep link intent handler
        compositeDisposable.add(viewModel.openVpnSettingsFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(__ -> {
                    final FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        startActivityForResult(new Intent(getActivity(),
                                VpnOptionsPreferenceActivity.class), REQUEST_CODE_VPN_PREFERENCES);
                    }
                })
                .subscribe());

        // Observe 'Open Proxy settings' signal from deep link intent handler
        compositeDisposable.add(viewModel.openProxySettingsFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(__ -> {
                    final FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        startActivityForResult(new Intent(getActivity(),
                                ProxyOptionsPreferenceActivity.class), REQUEST_CODE_PROXY_PREFERENCES);
                    }
                })
                .subscribe());

        // Observe 'More Options' signal from deep link intent handler
        compositeDisposable.add(viewModel.openMoreOptionsFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(__ -> {
                    final FragmentActivity activity = getActivity();
                    if (activity != null && !activity.isFinishing()) {
                        startActivityForResult(new Intent(getActivity(),
                                MoreOptionsPreferenceActivity.class), REQUEST_CODE_MORE_PREFERENCES);
                    }
                })
                .subscribe());
    }

    private void onRegionSelected(String selectedRegionCode) {
        String egressRegionPreference = multiProcessPreferences
                .getString(getString(R.string.egressRegionPreference),
                        PsiphonConstants.REGION_CODE_ANY);
        if (selectedRegionCode.equals(egressRegionPreference)) {
            return;
        }

        // Store the selection in preferences
        multiProcessPreferences.put(getString(R.string.egressRegionPreference), selectedRegionCode);

        // NOTE: reconnects even when Any is selected: we could select a
        // faster server
        viewModel.restartTunnelService();
    }

    private void setSummaryFromPreferences() {
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

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        boolean shouldRestart = false;
        switch (request) {
            case REQUEST_CODE_VPN_PREFERENCES:
                shouldRestart = vpnSettingsRestartRequired();
                updateVpnSettingsFromPreferences();
                break;

            case REQUEST_CODE_PROXY_PREFERENCES:
                shouldRestart = proxySettingsRestartRequired();
                updateProxySettingsFromPreferences();
                break;

            case REQUEST_CODE_MORE_PREFERENCES:
                shouldRestart = moreSettingsRestartRequired();
                updateMoreSettingsFromPreferences();
                break;

            default:
                super.onActivityResult(request, result, data);
        }

        if (shouldRestart) {
            compositeDisposable.add(viewModel.tunnelStateFlowable()
                    .filter(tunnelState -> !tunnelState.isUnknown())
                    .firstOrError()
                    .doOnSuccess(state -> {
                        if (state.isRunning()) {
                            if (viewModel.validateCustomProxySettings()) {
                                viewModel.restartTunnelService();
                            } else {
                                viewModel.stopTunnelService();
                            }
                        }
                    })
                    .subscribe());
        }

        if (data != null && data.getBooleanExtra(MoreOptionsPreferenceActivity.INTENT_EXTRA_LANGUAGE_CHANGED, false)) {
            // Signal the service to update notifications with new language
            viewModel.sendLocaleChangedMessage();
            // This is a bit of a weird hack to cause a restart, but it works
            // Previous attempts to use the alarm manager or others caused a variable amount of wait (up to about a second)
            // before the activity would relaunch. This *seems* to provide the best functionality across phones.
            // Add a 1 second delay to give activity chance to restart the service if needed
            new Handler().postDelayed(() -> {
                requireActivity().finish();
                Intent intent = new Intent(requireActivity(), MainActivity.class);
                intent.putExtra(MainActivity.INTENT_EXTRA_PREVENT_AUTO_START, true);
                startActivity(intent);
                System.exit(1);
            }, shouldRestart ? 1000 : 0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

    private boolean vpnSettingsRestartRequired() {
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(getString(R.string.moreOptionsPreferencesName), MODE_PRIVATE);

        // check if selected routing preference has changed
        boolean tunnelAll = prefs.getBoolean(getString(R.string.preferenceIncludeAllAppsInVpn), true);
        boolean tunnelAllNew = multiProcessPreferences.getBoolean(getString(R.string.preferenceIncludeAllAppsInVpn), true);
        if (tunnelAll != tunnelAllNew) {
            return true;
        }

        boolean tunnelSelected = prefs.getBoolean(getString(R.string.preferenceIncludeAppsInVpn), false);
        boolean tunnelSelectedNew = multiProcessPreferences.getBoolean(getString(R.string.preferenceIncludeAppsInVpn), false);
        if (tunnelSelected != tunnelSelectedNew) {
            return true;
        }

        // check if the selected apps changed
        if (tunnelSelected) {
            String tunnelSelectedString = prefs.getString(getString(R.string.preferenceIncludeAppsInVpnString), "");
            String tunnelSelectedStringNew = multiProcessPreferences.getString(getString(R.string.preferenceIncludeAppsInVpnString), "");
            if (!tunnelSelectedString.equals(tunnelSelectedStringNew)) {
                return true;
            }
        }

        boolean tunnelNotSelected = prefs.getBoolean(getString(R.string.preferenceExcludeAppsFromVpn), false);
        boolean tunnelNotSelectedNew = multiProcessPreferences.getBoolean(getString(R.string.preferenceExcludeAppsFromVpn), false);
        if (tunnelNotSelected != tunnelNotSelectedNew) {
            return true;
        }

        // check if the selected apps changed
        if (tunnelNotSelected) {
            String tunnelNotSelectedString = prefs.getString(getString(R.string.preferenceExcludeAppsFromVpnString), "");
            String tunnelNotSelectedStringNew = multiProcessPreferences.getString(getString(R.string.preferenceExcludeAppsFromVpnString), "");
            if (!tunnelNotSelectedString.equals(tunnelNotSelectedStringNew)) {
                return true;
            }
        }
        return false;
    }

    private boolean proxySettingsRestartRequired() {
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(getString(R.string.moreOptionsPreferencesName), MODE_PRIVATE);

        // check if "use proxy" has changed
        boolean useHTTPProxyPreference = prefs.getBoolean(getString(R.string.useProxySettingsPreference),
                false);
        if (useHTTPProxyPreference != UpstreamProxySettings.getUseHTTPProxy(requireContext())) {
            return true;
        }

        // no further checking if "use proxy" is off and has not
        // changed
        if (!useHTTPProxyPreference) {
            return false;
        }

        // check if "use custom proxy settings"
        // radio has changed
        boolean useCustomProxySettingsPreference = prefs.getBoolean(
                getString(R.string.useCustomProxySettingsPreference), false);
        if (useCustomProxySettingsPreference != UpstreamProxySettings.getUseCustomProxySettings(requireContext())) {
            return true;
        }

        // no further checking if "use custom proxy" is off and has
        // not changed
        if (!useCustomProxySettingsPreference) {
            return false;
        }

        // "use custom proxy" is selected, check if
        // host || port have changed
        if (!prefs.getString(getString(R.string.useCustomProxySettingsHostPreference), "")
                .equals(UpstreamProxySettings.getCustomProxyHost(requireContext()))
                || !prefs.getString(getString(R.string.useCustomProxySettingsPortPreference), "")
                .equals(UpstreamProxySettings.getCustomProxyPort(requireContext()))) {
            return true;
        }

        // check if "use proxy authentication" has changed
        boolean useProxyAuthenticationPreference = prefs.getBoolean(
                getString(R.string.useProxyAuthenticationPreference), false);
        if (useProxyAuthenticationPreference != UpstreamProxySettings.getUseProxyAuthentication(requireContext())) {
            return true;
        }

        // no further checking if "use proxy authentication" is off
        // and has not changed
        if (!useProxyAuthenticationPreference) {
            return false;
        }

        // "use proxy authentication" is checked, check if
        // username || password || domain have changed
        return !prefs.getString(getString(R.string.useProxyUsernamePreference), "")
                .equals(UpstreamProxySettings.getProxyUsername(requireContext()))
                || !prefs.getString(getString(R.string.useProxyPasswordPreference), "")
                .equals(UpstreamProxySettings.getProxyPassword(requireContext()))
                || !prefs.getString(getString(R.string.useProxyDomainPreference), "")
                .equals(UpstreamProxySettings.getProxyDomain(requireContext()));
    }

    private boolean moreSettingsRestartRequired() {
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(getString(R.string.moreOptionsPreferencesName), MODE_PRIVATE);

        // check if disable timeouts setting has changed
        boolean disableTimeoutsNewPreference =
                prefs.getBoolean(getString(R.string.disableTimeoutsPreference), false);
        boolean disableTimeoutsCurrentPreference =
                multiProcessPreferences.getBoolean(getString(R.string.disableTimeoutsPreference), false);
        return disableTimeoutsCurrentPreference != disableTimeoutsNewPreference;
    }

    private void updateVpnSettingsFromPreferences() {
        // Import 'VPN Settings' values to tray preferences
        String prefName = getString(R.string.moreOptionsPreferencesName);
        multiProcessPreferences.migrate(
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceIncludeAllAppsInVpn), getString(R.string.preferenceIncludeAllAppsInVpn)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceIncludeAppsInVpn), getString(R.string.preferenceIncludeAppsInVpn)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceIncludeAppsInVpnString), getString(R.string.preferenceIncludeAppsInVpnString)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceExcludeAppsFromVpn), getString(R.string.preferenceExcludeAppsFromVpn)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceExcludeAppsFromVpnString), getString(R.string.preferenceExcludeAppsFromVpnString))
        );
    }

    private void updateProxySettingsFromPreferences() {
        // Import 'Proxy settings' values to tray preferences
        String prefName = getString(R.string.moreOptionsPreferencesName);
        multiProcessPreferences.migrate(
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useProxySettingsPreference), getString(R.string.useProxySettingsPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useSystemProxySettingsPreference), getString(R.string.useSystemProxySettingsPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useCustomProxySettingsPreference), getString(R.string.useCustomProxySettingsPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useCustomProxySettingsHostPreference), getString(R.string.useCustomProxySettingsHostPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useCustomProxySettingsPortPreference), getString(R.string.useCustomProxySettingsPortPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useProxyAuthenticationPreference), getString(R.string.useProxyAuthenticationPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useProxyUsernamePreference), getString(R.string.useProxyUsernamePreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useProxyPasswordPreference), getString(R.string.useProxyPasswordPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.useProxyDomainPreference), getString(R.string.useProxyDomainPreference))
        );
    }

    private void updateMoreSettingsFromPreferences() {
        // Import 'More Options' values to tray preferences
        String prefName = getString(R.string.moreOptionsPreferencesName);
        multiProcessPreferences.migrate(
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceNotificationsWithSound), getString(R.string.preferenceNotificationsWithSound)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.preferenceNotificationsWithVibrate), getString(R.string.preferenceNotificationsWithVibrate)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.downloadWifiOnlyPreference), getString(R.string.downloadWifiOnlyPreference)),
                new SharedPreferencesImport(requireContext(), prefName, getString(R.string.disableTimeoutsPreference), getString(R.string.disableTimeoutsPreference))
        );
    }
}
