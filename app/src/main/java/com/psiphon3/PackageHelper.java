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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psiphon3.log.MyLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PackageHelper {

    private static final String LOCK_FILE = "trusted_signatures.lock";
    private static final String TEMP_FILE = "trusted_signatures_temp.json";
    private static final String SIGNATURES_FILE = "trusted_signatures.json";

    // Unmodifiable map of trusted packages with their corresponding sets of SHA-256 signature hashes
    private static final Map<String, Set<String>> TRUSTED_PACKAGES;
    static {
        // Conduit package and its signature as SHA-256 hash using uppercase hex encoding, continuous (no separator)
        Map<String, Set<String>> map = new HashMap<>();
        map.put("ca.psiphon.conduit", new HashSet<>(Arrays.asList(
                "488C4B47902ECB48047E97FFFE1F19C4B00F3140D2E25706709523CFFE4DC4B3"
                // Add additional valid signatures for the package as needed:
                //"THEOTHERSIGNATUREHASHHERE"
        )));
        // Ryve package and its signatures as SHA-256 hashes using uppercase hex encoding, continuous (no separator)
        // app ID: network.ryve.app
        // SHA256: AE:2E:20:B1:DC:53:72:C2:60:73:58:A3:BA:46:1E:1C:A4:30:6F:A1:74:FF:57:42:7A:1C:F5:2B:34:3F:AE:A0
        map.put("network.ryve.app", Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                "AE2E20B1DC5372C2607358A3BA461E1CA4306FA174FF57427A1CF52B343FAEA0"
                // Add additional valid signatures for the package as needed:
                // "THEOTHERSIGNATUREHASHHERE"
        ))));
        TRUSTED_PACKAGES = Collections.unmodifiableMap(map);
    }

    private static final ConcurrentHashMap<String, Set<String>> RUNTIME_TRUSTED_PACKAGES = new ConcurrentHashMap<>();

    // Get the expected signature for a package
    @NonNull
    public static Set<String> getExpectedSignaturesForPackage(String packageName) {
        Set<String> signatures = new HashSet<>();
        Set<String> trustedSigs = TRUSTED_PACKAGES.get(packageName);
        if (trustedSigs != null) {
            signatures.addAll(trustedSigs);
        }
        Set<String> runtimeSigs = RUNTIME_TRUSTED_PACKAGES.get(packageName);
        if (runtimeSigs != null) {
            signatures.addAll(runtimeSigs);
        }
        return Collections.unmodifiableSet(signatures);
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

    // Saves the map of package signatures to a file in process-safe manner
    // Uses file locking to ensure only one process can write at a time.
    // Uses a temporary file and atomic rename to ensure data consistency.
    @SuppressWarnings("resource") // Pre-API 19: manual resource handling required instead of try-with-resources
    public static void saveTrustedSignaturesToFile(Context context, Map<String, Set<String>> signatures) {
        File tempFile = new File(context.getFilesDir(), TEMP_FILE);
        File finalFile = new File(context.getFilesDir(), SIGNATURES_FILE);
        File lockFile = new File(context.getFilesDir(), LOCK_FILE);

        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            channel = randomAccessFile.getChannel();

            // Block until we can acquire the lock
            // This ensures we don't miss writing important trusted signatures data
            try {
                lock = channel.lock();
            } catch (OverlappingFileLockException e) {
                // Lock is already held by another channel in this JVM
                MyLog.e("PackageHelper: Lock already held by this JVM: " + e);
                return;
            }

            try {
                // Write to temporary file first to ensure atomic update
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {

                    // Convert signatures map to JSON
                    JSONObject jsonObject = new JSONObject();
                    for (Map.Entry<String, Set<String>> entry : signatures.entrySet()) {
                        jsonObject.put(entry.getKey(), new JSONArray(entry.getValue()));
                    }

                    // Write and flush to ensure all data is written
                    writer.write(jsonObject.toString());
                    writer.flush();
                    // Force system to sync file to disk
                    fos.getFD().sync();
                }

                // Atomic rename operation - either completely succeeds or fails
                if (!tempFile.renameTo(finalFile)) {
                    MyLog.e("PackageHelper: Failed to rename temp file to final file.");
                }
            } finally {
                // Always try to clean up temp file if it exists
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

        } catch (IOException | JSONException e) {
            MyLog.e("PackageHelper: failed to save trusted signatures: " + e);
        } finally {
            // Always release the lock if we acquired it
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to release lock: " + e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to close channel: " + e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to close random access file: " + e);
                }
            }
        }
    }

    // Reads package signatures from file in a process-safe manner.
    // Uses shared file locking to allow multiple readers but prevent reading during writes.
    // Returns empty map if file doesn't exist or on any error.
    @SuppressWarnings("resource") // Pre-API 19: manual resource handling required instead of try-with-resources
    public static Map<String, Set<String>> readTrustedSignaturesFromFile(Context context) {
        File file = new File(context.getFilesDir(), SIGNATURES_FILE);
        File lockFile = new File(context.getFilesDir(), LOCK_FILE);
        Map<String, Set<String>> signatures = new HashMap<>();

        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            channel = randomAccessFile.getChannel();

            // Get shared lock - allows multiple readers but not during writes
            try {
                lock = channel.lock(0L, Long.MAX_VALUE, true);  // true = shared lock
            } catch (OverlappingFileLockException e) {
                MyLog.e("PackageHelper: Read lock already held by this JVM: " + e);
                return signatures;
            }

            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    // Read entire file into StringBuilder
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }

                    // Parse JSON and convert to signatures map
                    JSONObject jsonObject = new JSONObject(builder.toString());
                    Iterator<String> keys = jsonObject.keys();
                    while (keys.hasNext()) {
                        String packageName = keys.next();
                        JSONArray signatureArray = jsonObject.getJSONArray(packageName);
                        Set<String> signatureSet = new HashSet<>();
                        for (int i = 0; i < signatureArray.length(); i++) {
                            signatureSet.add(signatureArray.getString(i));
                        }
                        signatures.put(packageName, signatureSet);
                    }
                }
            }

        } catch (IOException | JSONException e) {
            MyLog.e("PackageHelper: failed to read trusted signatures: " + e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to release lock: " + e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to close channel: " + e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    MyLog.e("PackageHelper: failed to close random access file: " + e);
                }
            }
        }

        return signatures;
    }

    // Load runtime trusted signatures configuration
    // Make sure the map is immutable and the sets are unmodifiable
    public static void configureRuntimeTrustedSignatures(Map<String, Set<String>> signatures) {
        RUNTIME_TRUSTED_PACKAGES.clear();
        for (Map.Entry<String, Set<String>> entry : signatures.entrySet()) {
            RUNTIME_TRUSTED_PACKAGES.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new HashSet<>(entry.getValue()))
            );
        }
        MyLog.i("PackageHelper: loaded runtime signatures for " + signatures.size() + " packages");
    }
}
