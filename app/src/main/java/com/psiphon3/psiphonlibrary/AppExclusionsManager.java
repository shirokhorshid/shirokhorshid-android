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

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.Set;

public class AppExclusionsManager {
    private final AppPreferences preferences;
    private final String includeAppsStringPreferenceKey;
    private final String excludeAppsStringPreferenceKey;

    AppExclusionsManager(Context context) {
        this.preferences = new AppPreferences(context);
        includeAppsStringPreferenceKey = context.getString(R.string.preferenceIncludeAppsInVpnString);
        excludeAppsStringPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpnString);
    }

    public Set<String> getAppsIncludedInVpn() {
        String serializedSet = preferences.getString(includeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public Set<String> getAppsExcludedFromVpn() {
        String serializedSet = preferences.getString(excludeAppsStringPreferenceKey, "");
        return SharedPreferenceUtils.deserializeSet(serializedSet);
    }

    public void setAppsToIncludeInVpn(Set<String> packageIds) {
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        preferences.put(includeAppsStringPreferenceKey, serializedSet);
    }

    public void setAppsToExcludeFromVpn(Set<String> packageIds) {
        String serializedSet = SharedPreferenceUtils.serializeSet(packageIds);
        preferences.put(excludeAppsStringPreferenceKey, serializedSet);
    }
}
