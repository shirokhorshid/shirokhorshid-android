package com.psiphon3.psiphonlibrary;

import android.content.Context;

// Activities that inherit from these activities will correctly handle locale changes
public abstract class LocalizedActivities {
    public static abstract class Activity extends android.app.Activity {
        @Override
        protected void attachBaseContext(Context newBase) {
            super.attachBaseContext(LocaleManager.setLocale(newBase));
        }
    }

    public static abstract class ListActivity extends android.app.ListActivity {
        @Override
        protected void attachBaseContext(Context newBase) {
            super.attachBaseContext(LocaleManager.setLocale(newBase));
        }
    }

    public static abstract class ExpandableListActivity extends android.app.ExpandableListActivity {
        @Override
        protected void attachBaseContext(Context newBase) {
            super.attachBaseContext(LocaleManager.setLocale(newBase));
        }
    }

    public static abstract class PreferenceActivity extends android.preference.PreferenceActivity {
        @Override
        protected void attachBaseContext(Context newBase) {
            super.attachBaseContext(LocaleManager.setLocale(newBase));
        }
    }

    public static abstract class TabActivity extends android.app.TabActivity {
        @Override
        protected void attachBaseContext(Context newBase) {
            super.attachBaseContext(LocaleManager.setLocale(newBase));
        }
    }
}
