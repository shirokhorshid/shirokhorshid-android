package com.psiphon3.psiphonlibrary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

import static android.os.Build.VERSION_CODES.*;

/**
 * This class is based off YarikSOffice LanguageTest @ https://github.com/YarikSOffice/LanguageTest.
 * Small changes made for Psiphon's use.
 */
public class LocaleManager {
    private static final String LANGUAGE_KEY = "language_key";
    private final SharedPreferences preferences;

    public LocaleManager(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public Context setLocale(Context context) {
        return updateResources(context, getLanguage());
    }

    public Context setNewLocale(Context context, String language) {
        persistLanguage(language);
        return updateResources(context, language);
    }

    public String getLanguage() {
        return preferences.getString(LANGUAGE_KEY, Locale.getDefault().getLanguage());
    }

    @SuppressLint("ApplySharedPref")
    private void persistLanguage(String language) {
        preferences.edit().putString(LANGUAGE_KEY, language).apply();
    }

    private Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }

        return context;
    }
}