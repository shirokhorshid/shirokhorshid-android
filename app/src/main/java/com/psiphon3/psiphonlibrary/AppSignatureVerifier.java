package com.psiphon3.psiphonlibrary;/*
 * Copyright (c) 2024, Psiphon Inc.
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;

import com.psiphon3.log.MyLog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AppSignatureVerifier {

    private final PackageManager packageManager;

    public AppSignatureVerifier(Context context) {
        this.packageManager = context.getPackageManager();
    }

    // Verifies the signature for the given package name.
    // Note: caching was considered but deemed unnecessary for a small number of apps (1-3),
    // so signatures are checked directly from the package info.
    public String getSignatureHash(String packageName) {
        try {
            PackageInfo packageInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28 and above
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            } else { // API 27 and below
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            }

            return getSignatureHashFromPackageInfo(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.w("AppSignatureVerifier: Package not found: " + packageName);
            return null;
        }
    }

    public String getSignatureHashFromPackageInfo(PackageInfo packageInfo) {
        Signature[] signatures;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28 and above
            if (packageInfo.signingInfo != null) {
                signatures = packageInfo.signingInfo.getApkContentsSigners(); // Use signingInfo for API 28+
            } else {
                return null;
            }
        } else {
            signatures = packageInfo.signatures; // Use signatures for API 27 and below
        }

        if (signatures != null && signatures.length > 0) {
            return hashSignature(signatures[0]);
        }
        return null;
    }

    private String hashSignature(Signature signature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(signature.toByteArray());

            // Convert byte array to a hex string with colons
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < hashBytes.length; i++) {
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex.toUpperCase());

                // Add colon between bytes, but not after the last one
                if (i < hashBytes.length - 1) {
                    hexString.append(':');
                }
            }

            return hexString.toString(); // Return hex format with colons
        } catch (NoSuchAlgorithmException e) {
            MyLog.w("AppSignatureVerifier: Unable to hash signature: " + e.getMessage());
            return null;
        }
    }

    public boolean isSignatureValid(String packageName, String expectedHash) {
        String actualHash = getSignatureHash(packageName);
        return expectedHash != null && expectedHash.equals(actualHash);
    }
}
