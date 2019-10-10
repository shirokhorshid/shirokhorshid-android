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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.DialogPreference;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.psiphon3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class InstalledAppsMultiSelectListPreference extends DialogPreference {

    public InstalledAppsMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setNegativeButtonText(null);
    }

   @Override
   protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
       Context context = getContext();
       View view = View.inflate(context, R.layout.dialog_exclude_apps,null);
       builder.setView(view);

       List<AppEntry> installedApps = getInstalledApps();

       final SharedPreferences sharedPreferences = context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName), Context.MODE_PRIVATE);
       final String sharedPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpnString);
       final Set<String> excludedApps = SharedPreferenceUtils.deserializeSet(sharedPreferences.getString(sharedPreferenceKey, ""));

       final InstalledAppsRecyclerViewAdapter adapter = new InstalledAppsRecyclerViewAdapter(
               context,
               installedApps,
               excludedApps);

       adapter.setClickListener(new InstalledAppsRecyclerViewAdapter.ItemClickListener() {
           @SuppressLint("ApplySharedPref")
           @Override
           public void onItemClick(View view, int position) {
                AppEntry appEntry = adapter.getItem(position);

               if (excludedApps.contains(appEntry.getPackageId())) {
                   excludedApps.remove(appEntry.getPackageId());
               } else {
                   excludedApps.add(appEntry.getPackageId());
               }

               // Store the selection immediately in shared preferences
               SharedPreferences.Editor editor = sharedPreferences.edit();
               editor.putString(sharedPreferenceKey, SharedPreferenceUtils.serializeSet(excludedApps));

               // Use commit (sync) instead of apply (async) to prevent possible race with restarting
               // the tunnel happening before the value is fully persisted to shared preferences
               editor.commit();
           }
       });

       RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view_exclude_apps);
       recyclerView.setLayoutManager(new LinearLayoutManager(context));
       recyclerView.setAdapter(adapter);

       builder.setPositiveButton(R.string.label_done, null);
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
                if (!emitter.isDisposed()) {
                    emitter.onSuccess(icon);
                }
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
                // run on io as we're reading off disk
                .subscribeOn(Schedulers.io())
                // observe on ui
                .observeOn(AndroidSchedulers.mainThread())
                // cache so we can subscribe off the bat to start the op + subscribe when drawing
                .cache();
    }
}