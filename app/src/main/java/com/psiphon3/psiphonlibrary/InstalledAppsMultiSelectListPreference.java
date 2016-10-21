/*
 * Copyright (c) 2016, Psiphon Inc.
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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Build;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.psiphon3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

class InstalledAppsMultiSelectListPreferenceAdapter extends BaseAdapter {
    private Context mContext;
    private int resLayout;
    private List<AppEntry> appList;

    public InstalledAppsMultiSelectListPreferenceAdapter(Context context, int textViewResourceId, List<AppEntry> appList) {
        this.mContext = context;
        this.appList = appList;
        resLayout = textViewResourceId;
    }

    @Override
    public int getCount() {
        return appList.size();
    }

    @Override
    public Object getItem(int position) {
        return appList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
       View row = convertView;
        if (row == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(resLayout, parent, false);
        }

        AppEntry item = appList.get(position); // Produce a row for each Team.

        if (item != null) {
            TextView appName = (TextView) row.findViewById(R.id.app_list_row_name);
            TextView packageId = (TextView) row.findViewById(R.id.app_list_row_package_id);
            ImageView appIcon = (ImageView) row.findViewById(R.id.app_list_row_icon);

            appName.setText(item.getName());
            packageId.setText(item.getPackageId());
            appIcon.setImageDrawable(item.getIcon());
        }

        return row;
    }

}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class InstalledAppsMultiSelectListPreference extends MultiSelectListPreference {
    List<AppEntry> appList;

    public InstalledAppsMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        List<AppEntry> installedApps = getInstalledApps();
        this.appList = installedApps;

        LinkedHashMap<String, String> installedPackagesMap = new LinkedHashMap<>();
        for (AppEntry app : installedApps) {
            installedPackagesMap.put(app.getPackageId(), app.getName());
        }

        setEntries(installedPackagesMap.values().toArray(new String[installedPackagesMap.size()]));
        setEntryValues(installedPackagesMap.keySet().toArray(new String[installedPackagesMap.size()]));
    }

    public InstalledAppsMultiSelectListPreference(Context context) {
        this(context, null);
    }

   @Override
   protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
       InstalledAppsMultiSelectListPreferenceAdapter adapter = new InstalledAppsMultiSelectListPreferenceAdapter(getContext(), R.layout.preference_widget_applist_row, appList);
       builder.setAdapter(adapter);
   }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            SharedPreferences.Editor e = getSharedPreferences().edit();
            e.putString(getContext().getResources().getString(R.string.preferenceExcludeAppsFromVpnString), getValuesAsString());
            // Use commit (sync) instead of apply (async) to prevent possible race with restarting
            // the tunnel happening before the value is fully persisted to shared preferences
            e.commit();
        }
    }

    private String getValuesAsString() {
        Set<String> excludedApps = getValues();
        StringBuilder excludedAppsString = new StringBuilder();

        int i = 0;
        for (String app : excludedApps) {
            excludedAppsString.append(app);
            if (i != excludedApps.size() - 1) {
                excludedAppsString.append(",");
            }
            i++;
        }

        return excludedAppsString.toString();
    }

    private List<AppEntry> getInstalledApps() {
        PackageManager pm = getContext().getPackageManager();

        List<AppEntry> apps = new ArrayList<>();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (int i = 0; i < packages.size(); i++) {
            PackageInfo p = packages.get(i);

            // The returned app list excludes:
            //  - Android's 'system' packages to make the list more manageable to read and work with
            //  - Apps that don't require internet access
            if (!isSystemPackage(p) && isInternetPermissionGranted(p)) {
                String appName = p.applicationInfo.loadLabel(pm).toString();
                String packageId = p.packageName;
                Drawable icon = p.applicationInfo.loadIcon(pm);
                apps.add(new AppEntry(appName, packageId, icon));
            }
        }

        Collections.sort(apps);
        return apps;
    }

    private boolean isSystemPackage(PackageInfo pkgInfo) {
        return ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }

    private boolean isInternetPermissionGranted(PackageInfo pkgInfo) {
        if (pkgInfo.requestedPermissions != null) {
            for (String permission : pkgInfo.requestedPermissions) {
                if (Manifest.permission.INTERNET.equals(permission)) {
                    return true;
                }
            }
        }
        return false;
    }
}