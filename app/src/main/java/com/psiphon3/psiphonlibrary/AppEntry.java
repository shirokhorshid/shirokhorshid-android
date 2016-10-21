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

public class AppEntry implements Comparable<AppEntry> {

    private String name;
    private String packageId;
    private Drawable icon;

    public AppEntry(String name, String packageId, Drawable icon) {
        this.name = name;
        this.packageId = packageId;
        this.icon = icon;
    }

    public int compareTo(AppEntry other) {
        return this.getComparableName().compareTo(other.getComparableName());
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
    public Drawable getIcon() { return icon; }
}