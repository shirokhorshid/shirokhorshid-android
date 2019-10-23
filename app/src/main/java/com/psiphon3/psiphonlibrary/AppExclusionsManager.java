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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppExclusionsManager {
    // note that the order here must match that in preference_routing_presets_entries
    static final CharSequence[] ROUTING_PRESETS = new CharSequence[]{
            Presets.WEB.value,
            Presets.REDDIT.value,
            Presets.SOCIAL.value,
    };

    // not comprehensive by any means but a list of a few popular social/media apps
    private final String[] knownSocialAppPackageIds = new String[]{
            "com.zhiliaoapp.musically", // TikTok
            "com.snapchat.android", // Snapchat
            "com.instagram.android", // Instagram
            "com.instagram.threadsapp", // Threads
            "com.instagram.igtv", // IG TV
            "com.facebook.katana", // Facebook
            "com.facebook.lite", // Facebook lite
            "com.facebook.mlite", // Messenger lite
            "com.facebook.orca", // Messenger
            "com.grindrapp.android", // Grindr
            "com.tinder", // Tinder
            "com.bumble.app", // Bumble
            "com.pof.android", // Plenty of fish
            "org.thunderdog.challegram", // Telegram X
            "org.telegram.messenger", // Telegram
            "com.whatsapp", // Whatsapp
            "com.discord", // Discord
            "com.skype.raider", // Skype
            "com.viber.voip", // Viber
            "com.google.android.talk", // Hangouts
            "com.tencent.mm", // Wechat
            "kik.android", // Kik
            "org.thoughtcrime.securesms", // Signal
            "com.google.android.youtube", // Youtube
            "com.google.android.apps.youtube.music", // Youtube Music
            "com.netflix.mediaclient", // Netflix
            "com.spotify.music", // Spotify
            "com.spotify.lite", // Spotify Lite
    };

    private final AppPreferences preferences;
    private final String includeAllAppsPreferenceKey;
    private final String includeAppsPreferenceKey;
    private final String includeAppsStringPreferenceKey;
    private final String excludeAppsPreferenceKey;
    private final String excludeAppsStringPreferenceKey;

    AppExclusionsManager(Context context) {
        this.preferences = new AppPreferences(context);
        includeAllAppsPreferenceKey = context.getString(R.string.preferenceIncludeAllAppsInVpn);
        includeAppsPreferenceKey = context.getString(R.string.preferenceIncludeAppsInVpn);
        includeAppsStringPreferenceKey = context.getString(R.string.preferenceIncludeAppsInVpnString);
        excludeAppsPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpn);
        excludeAppsStringPreferenceKey = context.getString(R.string.preferenceExcludeAppsFromVpnString);
    }

    @NonNull
    public Set<String> getInstalledWebBrowserPackageIds(PackageManager packageManager) {
        // web browsers should be registered to try and handle intents with URL data
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.psiphon3.com"));
        return getPackagesAbleToHandleIntent(packageManager, intent);
    }

    @NonNull
    public Set<String> getInstalledRedditAppPackageIds(PackageManager packageManager) {
        // reddit apps should be registered to try and handle intents with reddit as the data
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.reddit.com"));
        return getPackagesAbleToHandleIntent(packageManager, intent);
    }

    @NonNull
    public Set<String> getInstalledSocialAppPackageIds(PackageManager packageManager) {
        // start off with reddit app ids
        Set<String> packageIds = getInstalledRedditAppPackageIds(packageManager);

        // add any web browsers
        packageIds.addAll(getInstalledWebBrowserPackageIds(packageManager));

        // then go through the pre-built list of known package ids, adding installed ones
        for (String packageId : knownSocialAppPackageIds) {
            if (isPackageInstalled(packageManager, packageId)) {
                packageIds.add(packageId);
            }
        }

        return packageIds;
    }

    public Set<String> getPresetsPackageIds(PackageManager packageManager, String preset) {
        try {
            switch (Presets.valueOf(preset)) {
                case WEB:
                    return getInstalledWebBrowserPackageIds(packageManager);
                case REDDIT:
                    return getInstalledRedditAppPackageIds(packageManager);
                case SOCIAL:
                    return getInstalledSocialAppPackageIds(packageManager);
            }
        } catch (IllegalArgumentException e) {
            // pass
        }

        return new HashSet<>();
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

    public void allowAllAppsThroughVpn() {
        preferences.put(includeAllAppsPreferenceKey, true);
        preferences.put(includeAppsPreferenceKey, false);
        preferences.put(excludeAppsPreferenceKey, false);
    }

    public void allowSelectedAppsThroughVpn() {
        preferences.put(includeAllAppsPreferenceKey, false);
        preferences.put(includeAppsPreferenceKey, true);
        preferences.put(excludeAppsPreferenceKey, false);
    }

    public void excludeSelectedAppsFromVpn() {
        preferences.put(includeAllAppsPreferenceKey, false);
        preferences.put(includeAppsPreferenceKey, false);
        preferences.put(excludeAppsPreferenceKey, true);
    }

    private boolean isPackageInstalled(PackageManager packageManager, String packageId) {
        try {
            // check if the package is installed by trying to get info on it
            // if a name not found exception is thrown it's not installed, otherwise it is
            packageManager.getPackageInfo(packageId, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return true;
    }

    private Set<String> getPackagesAbleToHandleIntent(PackageManager packageManager, Intent intent) {
        // collect using a set rather than a list in case the browser has multiple activities which
        // are registered to accept URL's
        Set<String> packageIds = new HashSet<>();

        // determine the match criteria
        // DEFAULT_ONLY will return a single result, the default browser while ALL will
        // return all possible web browsers
        int matchFlags = PackageManager.MATCH_DEFAULT_ONLY;
        if (supportsMatchAll()) {
            matchFlags = PackageManager.MATCH_ALL;
        }

        // determine which activities are available to handle the intent
        List<ResolveInfo> matchingActivities = packageManager.queryIntentActivities(intent, matchFlags);
        for (ResolveInfo info : matchingActivities) {
            packageIds.add(info.activityInfo.packageName);
        }

        return packageIds;
    }

    private boolean supportsMatchAll() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    enum Presets {
        WEB("WEB"),
        REDDIT("REDDIT"),
        SOCIAL("SOCIAL");

        private final String value;

        Presets(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
