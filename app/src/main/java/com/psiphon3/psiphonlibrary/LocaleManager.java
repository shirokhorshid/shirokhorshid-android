package com.psiphon3.psiphonlibrary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

/**
 * This class is based off YarikSOffice LanguageTest @ https://github.com/YarikSOffice/LanguageTest.
 * Small changes made for Psiphon's use.
 * <p>
 * YarikSOffice's Copyright:
 * MIT License
 * <p>
 * Copyright (c) 2017 Yaroslav Berezanskyi
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class LocaleManager {
    public static final String USE_SYSTEM_LANGUAGE_VAL = "system";

    private static final String LANGUAGE_KEY = "language_key";
    private static SharedPreferences m_preferences;

    public static void initialize(Context context) {
        m_preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static Context setLocale(Context context) {
        return updateResources(context, getLanguage());
    }

    static Context setNewLocale(Context context, String language) {
        persistLanguage(language);
        return updateResources(context, language);
    }

    static Context resetToSystemLocale(Context context) {
        return setNewLocale(context, USE_SYSTEM_LANGUAGE_VAL);
    }

    public static String getLanguage() {
        return m_preferences.getString(LANGUAGE_KEY, USE_SYSTEM_LANGUAGE_VAL);
    }

    public static boolean isSetToSystemLocale() {
        return USE_SYSTEM_LANGUAGE_VAL.equals(getLanguage());
    }

    @SuppressLint("ApplySharedPref")
    private static void persistLanguage(String language) {
        m_preferences.edit().putString(LANGUAGE_KEY, language).commit();
    }

    private static Context updateResources(Context context, String language) {
        Locale locale;
        if (language.equals(USE_SYSTEM_LANGUAGE_VAL)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = context.getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = context.getResources().getConfiguration().locale;
            }
        } else {
            locale = new Locale(language);
        }

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
