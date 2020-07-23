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

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VpnAppsUtils {
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
            if (getCurrentAppsExcludedFromVpn(context).isEmpty()) {
                prefs.put(context.getString(R.string.preferenceIncludeAllAppsInVpn), true);
            } else {
                prefs.put(context.getString(R.string.preferenceExcludeAppsFromVpn), true);
            }
        }
        // Check and prepopulate the include-only set if empty
        PackageManager pm = context.getPackageManager();
        if (getCurrentAppsIncludedInVpn(context).isEmpty()) {
            Set<String> appIds = getInstalledWebBrowserPackageIds(pm);
            // TODO: a better strategy of picking at least one app for VPN include only?
            if(appIds.size() > 0) {
                String serializedSet = SharedPreferenceUtils.serializeSet(appIds);
                prefs.put(context.getString(R.string.preferenceIncludeAppsInVpnString), serializedSet);
            }
        }
    }

    public static Set<String> getCurrentAppsIncludedInVpn(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = prefs.getString(context.getString(R.string.preferenceIncludeAppsInVpnString), "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public static Set<String> getCurrentAppsExcludedFromVpn(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = prefs.getString(context.getString(R.string.preferenceExcludeAppsFromVpnString), "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
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

    static void setCurrentAppsToIncludeInVpn(Context context, Set<String> includeApps) {
        AppPreferences prefs = new AppPreferences(context);
        String serializedSet = SharedPreferenceUtils.serializeSet(includeApps);
        prefs.put(context.getString(R.string.preferenceIncludeAppsInVpnString), serializedSet);
    }

    static void setCurrentAppsToExcludeFromVpn(Context context, Set<String> excludeApps) {
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

    public static boolean isTunneledAppId(Context context, String appId) {
        AppPreferences prefs = new AppPreferences(context);

        if(prefs.getBoolean(context.getString(R.string.preferenceExcludeAppsFromVpn), false)) {
            Set<String> untunneledApps = getCurrentAppsExcludedFromVpn(context);
            return !untunneledApps.contains(appId);
        }
        if(prefs.getBoolean(context.getString(R.string.preferenceIncludeAppsInVpn), false)) {
            Set<String> tunneledApps = getCurrentAppsIncludedInVpn(context);
            return tunneledApps.contains(appId);
        }
        // We must be tunneling all apps at this point
        return true;
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
