/*
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

package com.psiphon3;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psiphon3.log.MyLog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PackageHelper {
    // Map of trusted packages with their corresponding SHA-256 signature hashes
    private static final Map<String, String> TRUSTED_PACKAGES = new HashMap<String, String>() {{
        // Psiphon Conduit package and its signature
        put("ca.psiphon.conduit", "48:8C:4B:47:90:2E:CB:48:04:7E:97:FF:FE:1F:19:C4:B0:0F:31:40:D2:E2:57:06:70:95:23:CF:FE:4D:C4:B3");
        // Add other trusted packages here following the same pattern
    }};

    @NonNull
    public static Set<String> getTrustedPackages() {
        return new HashSet<>(TRUSTED_PACKAGES.keySet());
    }

    // Get the expected signature for a package
    @Nullable
    public static String getExpectedSignatureForPackage(String packageName) {
        return TRUSTED_PACKAGES.get(packageName);
    }

    // Verify if a package is trusted
    public static boolean verifyTrustedPackage(PackageManager packageManager, String packageName) {
        if (!TRUSTED_PACKAGES.containsKey(packageName)) {
            return false;
        }

        try {
            PackageInfo packageInfo;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            }

            String actualSignature = getPackageSignature(packageInfo);
            String expectedSignature = TRUSTED_PACKAGES.get(packageName);

            if (actualSignature != null && actualSignature.equals(expectedSignature)) {
                return true;
            } else {
                MyLog.w("TrustedPackages: Signature mismatch for package: " + packageName);
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.w("TrustedPackages: Package not found: " + packageName);
            return false;
        }
    }

    // Get the SHA-256 signature hash of a package
    @Nullable
    private static String getPackageSignature(PackageInfo packageInfo) {
        try {
            Signature[] signatures;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                signatures = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                signatures = packageInfo.signatures;
            }

            byte[] cert = signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    // Check if a package is installed
    public static boolean isPackageInstalled(PackageManager packageManager, String packageName) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
