package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.preference.MultiSelectListPreference;
import android.util.AttributeSet;

import com.psiphon3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class InstalledAppsMultiSelectListPreference extends MultiSelectListPreference {
    public InstalledAppsMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        List<AppEntry> installedApps = getInstalledApps();
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
        List<PackageInfo> packages = pm.getInstalledPackages(0);

        for (int i = 0; i < packages.size(); i++) {
            PackageInfo p = packages.get(i);
            if (!isSystemPackage(p)) {
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
}