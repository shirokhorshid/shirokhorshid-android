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
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

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
class InstalledAppsMultiSelectListPreference extends AlertDialog.Builder {
    InstalledAppsMultiSelectListPreference(Context context, LayoutInflater layoutInflater, boolean whitelist) {
        super(context);
        setTitle(getTitle(whitelist));
        setView(getView(context, layoutInflater, whitelist));
        setPositiveButton(R.string.preference_routing_exclude_apps_ok_button_text, null);
    }

    private int getTitle(boolean whitelist) {
        return whitelist ? R.string.preference_routing_include_apps_title : R.string.preference_routing_exclude_apps_title;
    }

    private String getPreferenceKey(Context context, boolean whitelist) {
        return context.getString(whitelist ? R.string.preferenceIncludeAppsInVpnString : R.string.preferenceExcludeAppsFromVpnString);
    }

    private View getView(Context context, LayoutInflater layoutInflater, boolean whitelist) {
        View view = layoutInflater.inflate(R.layout.dialog_select_installed_apps, null);

        List<AppEntry> installedApps = getInstalledApps(context);

        final AppPreferences appPreferences = new AppPreferences(context);
        final String preferenceKey = getPreferenceKey(context, whitelist);
        final Set<String> selectedApps = SharedPreferenceUtils.deserializeSet(appPreferences.getString(preferenceKey, ""));

        final InstalledAppsRecyclerViewAdapter adapter = new InstalledAppsRecyclerViewAdapter(
                context,
                installedApps,
                selectedApps);

        adapter.setClickListener(new InstalledAppsRecyclerViewAdapter.ItemClickListener() {
            @SuppressLint("ApplySharedPref")
            @Override
            public void onItemClick(View view, int position) {
                String app = adapter.getItem(position).getPackageId();

                // try to remove the app, if not able, i.e. it wasn't in the set, add it
                if (!selectedApps.remove(app)) {
                    selectedApps.add(app);
                }

                // store the selection immediately in shared app preferences
                appPreferences.put(preferenceKey, SharedPreferenceUtils.serializeSet(selectedApps));
            }
        });

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view_select_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        return view;
    }

    private List<AppEntry> getInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();

        List<AppEntry> apps = new ArrayList<>();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        String selfPackageName = context.getPackageName();

        for (int i = 0; i < packages.size(); i++) {
            PackageInfo p = packages.get(i);

            // The returned app list excludes:
            //  - Apps that don't require internet access
            // TODO: add Psiphon back to the list when we are able to send all Kin traffic via proxy.
            // That requires a change in Kin SDK.
            if (isInternetPermissionGranted(p) && !p.packageName.equals(selfPackageName)) {
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