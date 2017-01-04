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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
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
    private Set<String> excludedApps;

    InstalledAppsMultiSelectListPreferenceAdapter(Context context, int textViewResourceId, List<AppEntry> appList, Set<String> excludedApps) {
        this.mContext = context;
        this.appList = appList;
        this.excludedApps = excludedApps;
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
        return position;
    }

    @Override
    public View getView(int position, final View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(resLayout, parent, false);
        }

        final AppEntry app = appList.get(position);

        if (app != null) {
            final ImageView appIcon = (ImageView) row.findViewById(R.id.app_list_row_icon);
            final TextView appName = (TextView) row.findViewById(R.id.app_list_row_name);
            final CheckBox isExcluded = (CheckBox) row.findViewById(R.id.app_list_row_checkbox);

            appIcon.setImageDrawable(app.getIcon());
            appName.setText(app.getName());
            isExcluded.setChecked(excludedApps.contains(app.getPackageId()));

            isExcluded.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (excludedApps.contains(app.getPackageId())) {
                        excludedApps.remove(app.getPackageId());
                        isExcluded.setChecked(false);
                    } else {
                        excludedApps.add(app.getPackageId());
                        isExcluded.setChecked(true);
                    }
                }
            });

            row.setClickable(true);
            row.setId(position);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isExcluded.callOnClick();
                }
            });
        }

        return row;
    }

}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class InstalledAppsMultiSelectListPreference extends MultiSelectListPreference {
    private List<AppEntry> appList;

    public InstalledAppsMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

   @Override
   protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
       List<AppEntry> installedApps = getInstalledApps();
       this.appList = installedApps;

       LinkedHashMap<String, String> installedPackagesMap = new LinkedHashMap<>();
       for (AppEntry app : installedApps) {
           installedPackagesMap.put(app.getPackageId(), app.getName());
       }

       setEntries(installedPackagesMap.values().toArray(new String[installedPackagesMap.size()]));
       setEntryValues(installedPackagesMap.keySet().toArray(new String[installedPackagesMap.size()]));
       
       final InstalledAppsMultiSelectListPreferenceAdapter adapter = new InstalledAppsMultiSelectListPreferenceAdapter(
               getContext(),
               R.layout.preference_widget_applist_row,
               appList,
               getValues());

       builder.setPositiveButton(R.string.preference_routing_exclude_apps_ok_button_text, new DialogInterface.OnClickListener() {
           @Override
           public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor e = getSharedPreferences().edit();
                e.putString(getContext().getResources().getString(R.string.preferenceExcludeAppsFromVpnString), getValuesAsString());

                // Use commit (sync) instead of apply (async) to prevent possible race with restarting
                // the tunnel happening before the value is fully persisted to shared preferences
                e.commit();
           }
       });
       builder.setAdapter(adapter, null);
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
            //  - Apps that don't require internet access
            if (isInternetPermissionGranted(p)) {
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