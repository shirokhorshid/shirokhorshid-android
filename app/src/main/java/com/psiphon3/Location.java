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
import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.fonfon.geohash.GeoHash;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import io.reactivex.Single;

public class Location {
    public static boolean isAvailable(Context context) {
        // check if location permission is granted
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
            return false;
        }
        // check if Google Play services available
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    public static Single<String> getGeoHashSingle(Context context, int geoHashLength, int timeout) {
        return Single.<String>create(emitter -> {
                    // If geoHashLength is 0, emit empty string
                    if (geoHashLength == 0) {
                        if (!emitter.isDisposed()) {
                            emitter.onSuccess("");
                        }
                        return;
                    }
                    if (geoHashLength < 1 || geoHashLength > 12) {
                        if (!emitter.isDisposed()) {
                            emitter.onError(new Exception("Invalid geoHashLength"));
                        }
                        return;
                    }
                    if (!isAvailable(context)) {
                        // If location is not available, emit empty string
                        if (!emitter.isDisposed()) {
                            emitter.onSuccess("");
                        }
                        return;
                    }

                    FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (!emitter.isDisposed()) {
                            if (location == null) {
                                //historical location is not available, emit empty string
                                emitter.onSuccess("");
                            } else {
                                emitter.onSuccess(GeoHash.fromLocation(location, geoHashLength).toString());
                            }
                        }
                    }).addOnFailureListener(e -> {
                        if (!emitter.isDisposed()) {
                            emitter.onError(e);
                        }
                    });
                })
                .timeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void runCurrentLocationUpdate(Context context) {
        if (!isAvailable(context)) {
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null);
    }
}
