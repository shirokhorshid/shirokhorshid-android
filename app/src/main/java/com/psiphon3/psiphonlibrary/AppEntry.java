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

import android.graphics.drawable.Drawable;

import io.reactivex.Single;

public class AppEntry implements Comparable<AppEntry> {
    private final String name;
    private final String packageId;
    private final Single<Drawable> iconLoader;
    private final String comparableName;
    private final int versionCode;

    public AppEntry(String name, String packageId, Single<Drawable> iconLoader, int versionCode) {
        this.name = name;
        this.packageId = packageId;
        this.iconLoader = iconLoader;
        this.versionCode = versionCode;
        comparableName = getComparableName();
    }

    public int compareTo(AppEntry other) {
        return this.comparableName.compareTo(other.comparableName);
    }

    private String getComparableName() {
        return name.toLowerCase().replaceAll(" ", "");
    }

    public String getName() {
        return name;
    }
    public String getPackageId() {
        return packageId;
    }
    public Single<Drawable> getIconLoader() { return iconLoader; }
    public int getVersionCode() { return versionCode; }
}
