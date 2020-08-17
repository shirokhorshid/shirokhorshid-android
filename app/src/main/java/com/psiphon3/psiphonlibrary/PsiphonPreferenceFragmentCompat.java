/*
 * Copyright (c) 2020, Psiphon Inc.
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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

public abstract class PsiphonPreferenceFragmentCompat extends PreferenceFragmentCompat {
    private PreferenceGetter preferenceGetter;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Store temporary preferences used in this activity in its own file
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(getString(R.string.moreOptionsPreferencesName));

        // Initialize with current shared preferences if restoring from configuration change,
        // otherwise initialize with tray preferences values.
        if (savedInstanceState != null && savedInstanceState.getBoolean("onSaveInstanceState", false)) {
            preferenceGetter = new SharedPreferencesWrapper(prefMgr.getSharedPreferences());
        } else {
            preferenceGetter = new AppPreferencesWrapper(new AppPreferences(getContext()));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("onSaveInstanceState", true);
        super.onSaveInstanceState(savedInstanceState);
    }

    public PreferenceGetter getPreferenceGetter() {
        return preferenceGetter;
    }

    protected interface PreferenceGetter {
        boolean getBoolean(@NonNull final String key, final boolean defaultValue);

        String getString(@NonNull final String key, final String defaultValue);
    }

    protected class AppPreferencesWrapper implements PreferenceGetter {
        AppPreferences prefs;

        public AppPreferencesWrapper(AppPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            return prefs.getBoolean(key, defaultValue);
        }

        @Override
        public String getString(@NonNull String key, String defaultValue) {
            return prefs.getString(key, defaultValue);
        }
    }

    protected class SharedPreferencesWrapper implements PreferenceGetter {
        SharedPreferences prefs;

        public SharedPreferencesWrapper(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            return prefs.getBoolean(key, defaultValue);
        }

        @Override
        public String getString(@NonNull String key, String defaultValue) {
            return prefs.getString(key, defaultValue);
        }
    }
}
