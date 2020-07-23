package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class LocaleManagerTest {

    private final String mSystemLanguageVal = "system";
    private final String mLanguagePreferenceKey = "language_key";
    private final String mFrenchLanguage = "fr";
    private final String mEnglishLanguage = "en";

    private Context mContext;
    private SharedPreferences mPreferences;
    private LocaleManager mLocaleManager;

    @Before
    public void initialize() {
        mContext = InstrumentationRegistry.getTargetContext();
        mLocaleManager = LocaleManager.getInstance(mContext);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPreferences.edit().putString(mLanguagePreferenceKey, mEnglishLanguage).commit();
    }

    @Test
    public void localeManager_GetLanguage() {
        assertEquals(mEnglishLanguage, mLocaleManager.getLanguage());
    }

    @Test
    public void localeManager_SetLocale() {
        mPreferences.edit().putString(mLanguagePreferenceKey, mFrenchLanguage).commit();
        mLocaleManager.setLocale(mContext);

        assertEquals(mFrenchLanguage, mLocaleManager.getLanguage());
    }

    @Test
    public void localeManager_SetNewLocale() {
        mLocaleManager.setNewLocale(mContext, mFrenchLanguage);

        assertEquals(mFrenchLanguage, mLocaleManager.getLanguage());
    }

    @Test
    public void localeManager_ResetToDefaultLocale() {
        mLocaleManager.setNewLocale(mContext, mFrenchLanguage);

        assertEquals(mFrenchLanguage, mLocaleManager.getLanguage());

        mLocaleManager.resetToSystemLocale(mContext);

        assertEquals(mSystemLanguageVal, mLocaleManager.getLanguage());
    }
}
