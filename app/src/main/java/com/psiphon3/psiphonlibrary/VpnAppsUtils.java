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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import com.psiphon3.R;
import com.psiphon3.TunnelState;
import com.psiphon3.VpnRulesHelper;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VpnAppsUtils {

    public interface AppTunneledChecker {
        boolean isAppTunneled(String packageId);
    }

    public enum VpnAppsExclusionSetting {ALL_APPS, INCLUDE_APPS, EXCLUDE_APPS}

    public static VpnAppsExclusionSetting getVpnAppsExclusionMode(Context context) {
        if (Utils.supportsVpnExclusions()) {
            AppPreferences prefs = new AppPreferences(context);
            if (prefs.getBoolean(context.getString(R.string.preferenceExcludeAppsFromVpn), false)) {
                return VpnAppsExclusionSetting.EXCLUDE_APPS;
            }
            if (prefs.getBoolean(context.getString(R.string.preferenceIncludeAppsInVpn), false)) {
                return VpnAppsExclusionSetting.INCLUDE_APPS;
            }
        }
        return VpnAppsExclusionSetting.ALL_APPS;
    }

    static void migrate(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        try {
            prefs.getBoolean(context.getString(R.string.preferenceIncludeAllAppsInVpn));
        } catch (ItemNotFoundException e) {
            if (getUserAppsExcludedFromVpn(context).isEmpty()) {
                prefs.put(context.getString(R.string.preferenceIncludeAllAppsInVpn), true);
            } else {
                prefs.put(context.getString(R.string.preferenceExcludeAppsFromVpn), true);
            }
        }
        // Check and prepopulate the include-only set if empty
        PackageManager pm = context.getPackageManager();
        if (getUserAppsIncludedInVpn(context).isEmpty()) {
            Set<String> appIds = getInstalledWebBrowserPackageIds(pm);
            // TODO: a better strategy of picking at least one app for VPN include only?
            if(appIds.size() > 0) {
                String serializedSet = SharedPreferenceUtils.serializeSet(appIds);
                prefs.put(context.getString(R.string.preferenceIncludeAppsInVpnString), serializedSet);
            }
        }
    }

    static Set<String> getUserAppsIncludedInVpn(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = prefs.getString(context.getString(R.string.preferenceIncludeAppsInVpnString), "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    // Get user selections that don't match any VPN rules (these are user-controllable)
    public static Set<String> getUserControllableAppsIncludedInVpn(Context context) {
        Set<String> userApps = getUserAppsIncludedInVpn(context);
        PackageManager pm = context.getPackageManager();

        // Remove apps that match any rules - they're system-managed now
        java.util.Iterator<String> iterator = userApps.iterator();
        while (iterator.hasNext()) {
            String packageId = iterator.next();
            try {
                android.content.pm.PackageInfo packageInfo;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(packageId, android.content.pm.PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(packageId, 0);
                }
                int versionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ?
                    (int) packageInfo.getLongVersionCode() : packageInfo.versionCode;

                if (VpnRulesHelper.matchesAnyVpnRule(packageId, versionCode)) {
                    iterator.remove();
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                iterator.remove(); // Remove apps that aren't installed
            }
        }

        return userApps;
    }

    static Set<String> getUserAppsExcludedFromVpn(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = prefs.getString(context.getString(R.string.preferenceExcludeAppsFromVpnString), "");
        Set<String> excludedApps = SharedPreferenceUtils.deserializeSet(serializedSet);

        // Safeguard against excluding the self package
        excludedApps.remove(context.getPackageName());

        return excludedApps;
    }

    // Get user selections that don't match any VPN rules (these are user-controllable)
    public static Set<String> getUserControllableAppsExcludedFromVpn(Context context) {
        Set<String> userApps = getUserAppsExcludedFromVpn(context);
        PackageManager pm = context.getPackageManager();
        
        // Remove apps that match any rules - they're system-managed now
        java.util.Iterator<String> iterator = userApps.iterator();
        while (iterator.hasNext()) {
            String packageId = iterator.next();
            try {
                android.content.pm.PackageInfo packageInfo;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(packageId, android.content.pm.PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(packageId, 0);
                }
                int versionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ?
                    (int) packageInfo.getLongVersionCode() : packageInfo.versionCode;

                if (VpnRulesHelper.matchesAnyVpnRule(packageId, versionCode)) {
                    iterator.remove();
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                iterator.remove(); // Remove apps that aren't installed
            }
        }

        return userApps;
    }

    static void setPendingAppsToIncludeInVpn(Context context, Set<String> packageIds) {
        SharedPreferences pendingPreferences =
                context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName),
                        Context.MODE_PRIVATE);
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        pendingPreferences.edit().putString(context.getString(R.string.preferenceIncludeAppsInVpnString), serializedSet).apply();
    }

    static void setPendingAppsToExcludeFromVpn(Context context, Set<String> packageIds) {
        SharedPreferences pendingPreferences =
                context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName),
                        Context.MODE_PRIVATE);
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        pendingPreferences.edit().putString(context.getString(R.string.preferenceExcludeAppsFromVpnString), serializedSet).apply();
    }

    static void setUserAppsToIncludeInVpn(Context context, Set<String> includeApps) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = SharedPreferenceUtils.serializeSet(includeApps);
        prefs.put(context.getString(R.string.preferenceIncludeAppsInVpnString), serializedSet);
    }

    static void setUserAppsToExcludeFromVpn(Context context, Set<String> excludeApps) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = SharedPreferenceUtils.serializeSet(excludeApps);
        prefs.put(context.getString(R.string.preferenceExcludeAppsFromVpnString), serializedSet);
    }

    static Set<String> getPendingAppsIncludedInVpn(Context context) {
        SharedPreferences pendingPreferences =
                context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName),
                        Context.MODE_PRIVATE);
        String serializedSet = pendingPreferences.getString(context.getString(R.string.preferenceIncludeAppsInVpnString), "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    static Set<String> getPendingAppsExcludedFromVpn(Context context) {
        SharedPreferences pendingPreferences =
                context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName),
                        Context.MODE_PRIVATE);
        String serializedSet = pendingPreferences.getString(context.getString(R.string.preferenceExcludeAppsFromVpnString), "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public static AppTunneledChecker createAppTunneledChecker(VpnAppsExclusionSetting mode,
                                                              java.util.List<String> vpnApps) {
        return new AppTunneledChecker() {
            @Override
            public boolean isAppTunneled(String packageId) {
                switch (mode) {
                    case ALL_APPS:
                        return true;
                    case EXCLUDE_APPS:
                        return vpnApps == null || !vpnApps.contains(packageId);
                    case INCLUDE_APPS:
                        return vpnApps != null && vpnApps.contains(packageId);
                    default:
                        return true;
                }
            }
        };
    }

    public static AppTunneledChecker createAppTunneledCheckerFromTunnelState(TunnelState tunnelState) {
        if (tunnelState == null || !tunnelState.isRunning() || tunnelState.connectionData() == null) {
            // Default to all apps tunneled when tunnel is not running or no data available
            return createAppTunneledChecker(VpnAppsExclusionSetting.ALL_APPS, null);
        }
        TunnelState.ConnectionData connectionData = tunnelState.connectionData();
        return createAppTunneledChecker(connectionData.vpnMode(), connectionData.vpnApps());
    }

    @NonNull
    public static Set<String> getInstalledWebBrowserPackageIds(PackageManager packageManager) {
        // web browsers should be registered to try and handle intents with URL data
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.example.org"));
        return getPackagesAbleToHandleIntent(packageManager, intent);
    }

    @NonNull
    static Set<String> getPackagesAbleToHandleIntent(PackageManager packageManager, Intent intent) {
        // collect using a set rather than a list in case the browser has multiple activities which
        // are registered to accept URL's.
        // Note that we are using a LinkedHashSet here which yields FIFO order when iterated.
        Set<String> packageIds = new LinkedHashSet<>();

        // Try and put default package ID first by matching DEFAULT_ONLY
        List<ResolveInfo> matchingActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : matchingActivities) {
            packageIds.add(info.activityInfo.packageName);
        }

        // Next add all other packages able to handle the intent by matching ALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            matchingActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
            for (ResolveInfo info : matchingActivities) {
                packageIds.add(info.activityInfo.packageName);
            }
        }
        return packageIds;
    }
}
