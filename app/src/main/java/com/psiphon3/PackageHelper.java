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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PackageHelper {
    // Map of trusted packages with their corresponding sets of SHA-256 signature hashes
    private static final Map<String, Set<String>> TRUSTED_PACKAGES = new HashMap<String, Set<String>>() {{
        // Psiphon Conduit package and its signatures
        put("ca.psiphon.conduit", new HashSet<>(Arrays.asList(
                "48:8C:4B:47:90:2E:CB:48:04:7E:97:FF:FE:1F:19:C4:B0:0F:31:40:D2:E2:57:06:70:95:23:CF:FE:4D:C4:B3"
                // Add additional valid signatures for the package as needed:
                // "THE:OTHER:SIGNATURE:HASH:HERE"
                )));
    }};

    // Debug mode configuration
    private static boolean DEBUG_MODE = false;
    private static final Map<String, Set<String>> DEBUG_TRUSTED_PACKAGES = new HashMap<>();

    // Enable or disable debug mode to allow additional debug signatures at runtime
    // Note: This should be disabled in production builds
    public static void enableDebugMode(boolean enable) {
        DEBUG_MODE = enable;
        if (!enable) {
            DEBUG_TRUSTED_PACKAGES.clear();
        }
        MyLog.i("PackageHelper: debug mode " + (enable ? "enabled" : "disabled"));
    }

    // Add a trusted package with its signature for debug purposes at runtime
    // Note: This should be used only in debug mode with enableDebugMode(true)
    public static void addDebugTrustedSignature(String packageName, String signature) {
        if (!DEBUG_MODE) {
            MyLog.w("PackageHelper: attempted to add debug signature while not in debug mode");
            return;
        }
        Set<String> signatures = DEBUG_TRUSTED_PACKAGES.get(packageName);
        if (signatures == null) {
            signatures = new HashSet<>();
            DEBUG_TRUSTED_PACKAGES.put(packageName, signatures);
        }
        signatures.add(signature);
        MyLog.i("PackageHelper: added debug signature for package " + packageName);
    }

    // Get the expected signature for a package
    @NonNull
    public static Set<String> getExpectedSignaturesForPackage(String packageName) {
        Set<String> signatures = new HashSet<>();
        Set<String> trustedSigs = TRUSTED_PACKAGES.get(packageName);
        if (trustedSigs != null) {
            signatures.addAll(trustedSigs);
        }
        if (DEBUG_MODE) {
            Set<String> debugSigs = DEBUG_TRUSTED_PACKAGES.get(packageName);
            if (debugSigs != null) {
                signatures.addAll(debugSigs);
            }
        }
        return signatures;
    }

    // Verify if a package is trusted
    public static boolean verifyTrustedPackage(PackageManager packageManager, String packageName) {
        Set<String> expectedSignatures = getExpectedSignaturesForPackage(packageName);
        if (expectedSignatures.isEmpty()) {
            MyLog.w("PackageHelper: no trusted signatures found for package " + packageName);
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
            if (actualSignature != null && expectedSignatures.contains(actualSignature)) {
                if (DEBUG_MODE && DEBUG_TRUSTED_PACKAGES.containsKey(packageName)) {
                    MyLog.w("PackageHelper: package " + packageName + " verified using debug signature");
                }
                return true;
            } else {
                MyLog.w("PackageHelper: verification failed for package " + packageName + ", signature mismatch");
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.w("PackageHelper: verification failed for package " + packageName + ", package not found");
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
