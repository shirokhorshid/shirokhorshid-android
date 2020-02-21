/*
 * Copyright (c) 2019, Psiphon Inc.
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
import android.support.annotation.NonNull;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppExclusionsManager {
    private final AppPreferences currentPreferences;
    private final SharedPreferences pendingPreferences;
    private final String includeAppsStringPreferenceKey;
    private final String excludeAppsStringPreferenceKey;

    public AppExclusionsManager(Context context) {
        this.currentPreferences = new AppPreferences(context);
        includeAppsStringPreferenceKey = context.getString(R.string.preferenceIncludeAppsInVpnString);
        excludeAppsStringPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpnString);
        pendingPreferences = context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName), Context.MODE_PRIVATE);

        // Check and prepopulate the include-only set if empty
        PackageManager pm = context.getPackageManager();
        if (getCurrentAppsIncludedInVpn().isEmpty()) {
            // TODO: come up with better strategy of picking up at least one app for VPN include only
            // if getInstalledWebBrowserPackageIds returns an empty set
            Set<String> appIds = getInstalledWebBrowserPackageIds(pm);
            String serializedSet = SharedPreferenceUtils.serializeSet(appIds);
            currentPreferences.put(includeAppsStringPreferenceKey, serializedSet);
        }

        // Also migrate existing preferences
        try {
            currentPreferences.getBoolean(context.getString(R.string.preferenceIncludeAllAppsInVpn));
        } catch (ItemNotFoundException e) {
            if (getCurrentAppsExcludedFromVpn().isEmpty()) {
                currentPreferences.put(context.getString(R.string.preferenceIncludeAllAppsInVpn), true);
            } else {
                currentPreferences.put(context.getString(R.string.preferenceExcludeAppsFromVpn), true);
            }
        }
    }

    public Set<String> getCurrentAppsIncludedInVpn() {
        String serializedSet = currentPreferences.getString(includeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public Set<String> getCurrentAppsExcludedFromVpn() {
        String serializedSet = currentPreferences.getString(excludeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public void setPendingAppsToIncludeInVpn(Set<String> packageIds) {
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        pendingPreferences.edit().putString(includeAppsStringPreferenceKey, serializedSet).apply();
    }

    public void setPendingAppsToExcludeFromVpn(Set<String> packageIds) {
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        pendingPreferences.edit().putString(excludeAppsStringPreferenceKey, serializedSet).apply();
    }

    public Set<String> getPendingAppsIncludedInVpn() {
        String serializedSet = pendingPreferences.getString(includeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public Set<String> getPendingAppsExcludedFromVpn() {
        String serializedSet = pendingPreferences.getString(excludeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public boolean isTunneledAppId(Context context, String appId) {
        if(currentPreferences.getBoolean(context.getString(R.string.preferenceExcludeAppsFromVpn), false)) {
            Set<String> untunneledApps = getCurrentAppsExcludedFromVpn();
            return !untunneledApps.contains(appId);
        }
        if(currentPreferences.getBoolean(context.getString(R.string.preferenceIncludeAppsInVpn), false)) {
            Set<String> tunneledApps = getCurrentAppsIncludedInVpn();
            return tunneledApps.contains(appId);
        }
        // We must be tunneling all apps at this point
        return true;
    }

    @NonNull
    public Set<String> getInstalledWebBrowserPackageIds(PackageManager packageManager) {
        // web browsers should be registered to try and handle intents with URL data
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.example.org"));
        return getPackagesAbleToHandleIntent(packageManager, intent);
    }

    @NonNull
    private Set<String> getPackagesAbleToHandleIntent(PackageManager packageManager, Intent intent) {
        // collect using a set rather than a list in case the browser has multiple activities which
        // are registered to accept URL's
        Set<String> packageIds = new HashSet<>();

        // determine the match criteria
        // DEFAULT_ONLY will return a single result, the default browser while ALL will
        // return all possible web browsers
        int matchFlags = PackageManager.MATCH_DEFAULT_ONLY;

        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            matchFlags = PackageManager.MATCH_ALL;
        }
        // determine which activities are available to handle the intent
        List<ResolveInfo> matchingActivities = packageManager.queryIntentActivities(intent, matchFlags);
        for (ResolveInfo info : matchingActivities) {
            packageIds.add(info.activityInfo.packageName);
        }

        return packageIds;
    }
}
