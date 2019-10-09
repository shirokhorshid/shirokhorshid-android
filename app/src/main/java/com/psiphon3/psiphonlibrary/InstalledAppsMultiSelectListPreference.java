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

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

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

            app.getIconLoader()
                    .doOnSuccess(new Consumer<Drawable>() {
                        @Override
                        public void accept(Drawable icon) {
                            appIcon.setImageDrawable(icon);
                        }
                    })
                    .subscribe();
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
                    // Store the selection immediately in shared preferences
                    SharedPreferences.Editor e = mContext.getSharedPreferences(mContext.getString(R.string.moreOptionsPreferencesName),Context.MODE_PRIVATE).edit();
                    e.putString(mContext.getResources().getString(R.string.preferenceExcludeAppsFromVpnString), getValuesAsString());

                    // Use commit (sync) instead of apply (async) to prevent possible race with restarting
                    // the tunnel happening before the value is fully persisted to shared preferences
                    e.commit();
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

    private String getValuesAsString() {
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
}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class InstalledAppsMultiSelectListPreference extends MultiSelectListPreference {

    public InstalledAppsMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setNegativeButtonText(null);
    }

   @Override
   protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
       List<AppEntry> installedApps = getInstalledApps();

       LinkedHashMap<String, String> installedPackagesMap = new LinkedHashMap<>();
       for (AppEntry app : installedApps) {
           installedPackagesMap.put(app.getPackageId(), app.getName());
       }

       setEntries(installedPackagesMap.values().toArray(new String[0]));
       setEntryValues(installedPackagesMap.keySet().toArray(new String[0]));

       final InstalledAppsMultiSelectListPreferenceAdapter adapter = new InstalledAppsMultiSelectListPreferenceAdapter(
               getContext(),
               R.layout.preference_widget_applist_row,
               installedApps,
               getValues());

       builder.setPositiveButton(R.string.label_done, null);

       builder.setAdapter(adapter, null);
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
                // This takes a bit of time, but since we want the apps sorted by displayed name
                // its best to do synchronously
                String appName = p.applicationInfo.loadLabel(pm).toString();
                String packageId = p.packageName;
                Single<Drawable> iconLoader = getIconLoader(p.applicationInfo, pm);
                apps.add(new AppEntry(appName, packageId, iconLoader));
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

    private Single<Drawable> getIconLoader(final ApplicationInfo applicationInfo, final PackageManager packageManager) {
        Single<Drawable> single = Single.create(new SingleOnSubscribe<Drawable>() {
            @Override
            public void subscribe(SingleEmitter<Drawable> emitter) {
                Drawable icon = applicationInfo.loadIcon(packageManager);
                emitter.onSuccess(icon);
            }
        });

        return single
                // shouldn't ever get an error but handle it just in case
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) {
                        Utils.MyLog.g("failed to load icon for " + applicationInfo.packageName + " " + e);
                    }
                })
                // run on io
                .subscribeOn(Schedulers.io())
                // observe on ui
                .observeOn(AndroidSchedulers.mainThread())
                // cache so we can subscribe off the bat to start the op + subscribe when drawing
                .cache();
    }
}