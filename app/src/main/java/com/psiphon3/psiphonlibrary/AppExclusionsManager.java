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
import android.content.SharedPreferences;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.Set;

public class AppExclusionsManager {
    private final AppPreferences currentPreferences;
    private final SharedPreferences pendingPreferences;
    private final String includeAppsStringPreferenceKey;
    private final String excludeAppsStringPreferenceKey;

    AppExclusionsManager(Context context) {
        this.currentPreferences = new AppPreferences(context);
        includeAppsStringPreferenceKey = context.getString(R.string.preferenceIncludeAppsInVpnString);
        excludeAppsStringPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpnString);
        pendingPreferences = context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName), Context.MODE_PRIVATE);
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
}
