package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;

import androidx.core.os.ConfigurationCompat;

import net.grandcentrix.tray.AppPreferences;

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
    private static final String USE_SYSTEM_LANGUAGE_VAL = "system";
    private static final String LANGUAGE_KEY = "language_key";

    private static LocaleManager m_instance = null;

    private final AppPreferences m_preferences;

    private LocaleManager(Context context) {
        Context wrappedCtx = new ApplicationContextWrapper(context);
        m_preferences = new AppPreferences(wrappedCtx);

        // Migrate old shared preference language pref to multi-process preferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        if(sharedPrefs.contains(LANGUAGE_KEY)) {
            String oldLanguagePref = sharedPrefs.getString(LANGUAGE_KEY, null);
            if (oldLanguagePref != null && !oldLanguagePref.equals("")) {
                m_preferences.put(LANGUAGE_KEY, oldLanguagePref);
                sharedPrefs.edit().remove(LANGUAGE_KEY).apply();
            }
        }
    }

    public static LocaleManager getInstance(Context context) {
        if (m_instance == null) {
            m_instance = new LocaleManager(context);
        }

        return m_instance;
    }

    public Context setLocale(Context context) {
        return updateResources(context, getLanguage());
    }

    Context setNewLocale(Context context, String language) {
        persistLanguage(language);
        return updateResources(context, language);
    }

    Context resetToSystemLocale(Context context) {
        return setNewLocale(context, USE_SYSTEM_LANGUAGE_VAL);
    }

    public String getLanguage() {
        return m_preferences.getString(LANGUAGE_KEY, USE_SYSTEM_LANGUAGE_VAL);
    }

    public boolean isSetToSystemLocale() {
        return isSystemLocale(getLanguage());
    }

    public boolean isSystemLocale(String languageCode) {
        return USE_SYSTEM_LANGUAGE_VAL.equals(languageCode);
    }

    private void persistLanguage(String language) {
        m_preferences.put(LANGUAGE_KEY, language);
    }

    private static Locale fromLanguageCode(String languageCode) {
        // Handle language codes of the form "xx-rYY"
        if (languageCode.length() == 6 &&
                languageCode.substring(2,4).equalsIgnoreCase("-r")) {
            return new Locale(languageCode.substring(0,2), languageCode.substring(4,6));
        }
        return new Locale(languageCode);
    }

    private static Context updateResources(Context context, String language) {
        Locale locale;
        if (language.equals(USE_SYSTEM_LANGUAGE_VAL)) {
            locale = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration()).get(0);
        } else {
            locale = fromLanguageCode(language);
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

    private class ApplicationContextWrapper extends ContextWrapper {
        public ApplicationContextWrapper(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            Context appCtx = super.getApplicationContext();
            return appCtx != null ? appCtx : this;
        }
    }
}
