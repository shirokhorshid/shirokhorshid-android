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