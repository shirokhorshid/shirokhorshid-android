package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

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

    private final String mLanguagePreferenceKey = "language_key";
    private final String mFrenchLanguage = "fr";
    private final String mEnglishLanguage = "en";

    private Context mContext;
    private SharedPreferences mPreferences;

    @Before
    public void initialize() {
        mContext = InstrumentationRegistry.getTargetContext();
        LocaleManager.initialize(mContext);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPreferences.edit().putString(mLanguagePreferenceKey, mEnglishLanguage).commit();
    }

    @Test
    public void localeManager_GetLanguage() {
        assertEquals(mEnglishLanguage, LocaleManager.getLanguage());
    }

    @Test
    public void localeManager_SetLocale() {
        mPreferences.edit().putString(mLanguagePreferenceKey, mFrenchLanguage).commit();
        LocaleManager.setLocale(mContext);

        assertEquals(mFrenchLanguage, LocaleManager.getLanguage());
    }

    @Test
    public void localeManager_SetNewLocale() {
        LocaleManager.setNewLocale(mContext, mFrenchLanguage);

        assertEquals(mFrenchLanguage, LocaleManager.getLanguage());
    }

    @Test
    public void localeManager_ResetToDefaultLocale() {
        LocaleManager.setNewLocale(mContext, mFrenchLanguage);

        assertEquals(mFrenchLanguage, LocaleManager.getLanguage());

        LocaleManager.resetToDefaultLocale(mContext);

        assertEquals(mEnglishLanguage, LocaleManager.getLanguage());
    }
}
