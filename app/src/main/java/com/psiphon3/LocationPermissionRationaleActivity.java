/*
 * Copyright (c) 2023, Psiphon Inc.
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

package com.psiphon3;


import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.psiphon3.psiphonlibrary.LocalizedActivities;

import net.grandcentrix.tray.AppPreferences;

public class LocationPermissionRationaleActivity extends LocalizedActivities.AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_rationale);
        setFinishOnTouchOutside(false);

        ((TextView) findViewById(R.id.alertTitle)).setText(R.string.location_permission_rationale_title);

        String str = String.format(getString(R.string.location_permission_rationale_text), getString(R.string.app_name));

        ((TextView) findViewById(R.id.messageTextView)).setText(str);

        findViewById(R.id.continue_btn).setOnClickListener(v -> {
            // Check and request location permission again.

            final AppPreferences mp = new AppPreferences(getApplicationContext());
            int deviceLocationPrecision =  mp.getInt(getString(R.string.deviceLocationPrecisionParameter), 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    deviceLocationPrecision > 0 && deviceLocationPrecision <= 12 &&
                    ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MainActivity.REQUEST_CODE_PERMISSIONS);
            } else {
                // If we are not requesting location permission, finish the activity.
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MainActivity.REQUEST_CODE_PERMISSIONS) {
            // If location permission is granted run location update.
            if (grantResults.length > 0 && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                Location.runCurrentLocationUpdate(this);
            }
            // Finish the activity regardless of whether the permission was granted or not.
            finish();
        }
    }
}
