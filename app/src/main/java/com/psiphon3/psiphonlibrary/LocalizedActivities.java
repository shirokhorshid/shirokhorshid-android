package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;

import static android.content.pm.PackageManager.GET_META_DATA;

// Activities that inherit from these activities will correctly handle locale changes
public abstract class LocalizedActivities {
    public static abstract class AppCompatActivity extends androidx.appcompat.app.AppCompatActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Utils.resetActivityTitle(this);
        }

        @Override
        protected void attachBaseContext(Context newBase) {
            LocaleManager localeManager = LocaleManager.getInstance(newBase);
            super.attachBaseContext(localeManager.setLocale(newBase));
        }
    }

    private static class Utils {
        static void resetActivityTitle(android.app.Activity activity) {
            try {
                ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), GET_META_DATA);
                if (info.labelRes != 0) {
                    activity.setTitle(info.labelRes);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
