/*
 * Copyright (c) 2022, Psiphon Inc.
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.os.Build;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import de.schildbach.wallet.util.LinuxSecureRandom;


public class Utils {
    private static boolean m_initializedSecureRandom = false;

    public static void initializeSecureRandom() {
        // Installs a new SecureRandom SPI which directly uses /dev/urandom. This addresses a flaw in the default
        // SecureRandom documented here: http://armoredbarista.blogspot.com.au/2013/03/randomly-failed-weaknesses-in-java.html
        // NOTE: this is now the SPI for all versions of Android, including 4.2+ where the flaw was addressed.

        if (!m_initializedSecureRandom) {
            new LinuxSecureRandom();
            m_initializedSecureRandom = true;
        }
    }

    // from:
    // http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("len % 2 != 0");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
                    .digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    // from:
    // http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-l
    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v / 16];
            hexChars[j * 2 + 1] = hexArray[v % 16];
        }
        return new String(hexChars);
    }

    public static boolean supportsPsiphonBump(Context context) {
        AppPreferences mp = new AppPreferences(context);
        // Default to true
        return mp.getBoolean(context.getString(R.string.nfcBumpPreference), true) && supportsNfc(context);
    }

    public static boolean supportsNfc(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NfcAdapter nfcAdapter = null;
            PackageManager pm = context.getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(context);
            }
            return nfcAdapter != null;
        }
        return false;
    }

    /***************************************************************
     * Copyright (c) 1998, 1999 Nate Sammons <nate@protomatter.com> This library
     * is free software; you can redistribute it and/or modify it under the
     * terms of the GNU Library General Public License as published by the Free
     * Software Foundation; either version 2 of the License, or (at your option)
     * any later version.
     *
     * This library is distributed in the hope that it will be useful, but
     * WITHOUT ANY WARRANTY; without even the implied warranty of
     * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Library
     * General Public License for more details.
     *
     * You should have received a copy of the GNU Library General Public License
     * along with this library; if not, write to the Free Software Foundation,
     * Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
     *
     * Contact support@protomatter.com with your questions, comments, gripes,
     * praise, etc...
     ***************************************************************/

    /***************************************************************
     * - moved to the net.matuschek.util tree by Daniel Matuschek - replaced
     * deprecated getBytes() method in method decode - added String
     * encode(String) method to encode a String to base64
     ***************************************************************/

    /**
     * Base64 encoder/decoder. Does not stream, so be careful with using large
     * amounts of data
     *
     * @author Nate Sammons
     * @author Daniel Matuschek
     * @version $Id: Base64.java,v 1.4 2001/04/17 10:09:27 matuschd Exp $
     */
    public static class Base64 {

        private Base64() {
            super();
        }

        /**
         * Encode some data and return a String.
         */
        public final static String encode(byte[] d) {
            if (d == null)
                return null;
            byte data[] = new byte[d.length + 2];
            System.arraycopy(d, 0, data, 0, d.length);
            byte dest[] = new byte[(data.length / 3) * 4];

            // 3-byte to 4-byte conversion
            for (int sidx = 0, didx = 0; sidx < d.length; sidx += 3, didx += 4) {
                dest[didx] = (byte) ((data[sidx] >>> 2) & 077);
                dest[didx + 1] = (byte) ((data[sidx + 1] >>> 4) & 017 | (data[sidx] << 4) & 077);
                dest[didx + 2] = (byte) ((data[sidx + 2] >>> 6) & 003 | (data[sidx + 1] << 2) & 077);
                dest[didx + 3] = (byte) (data[sidx + 2] & 077);
            }

            // 0-63 to ascii printable conversion
            for (int idx = 0; idx < dest.length; idx++) {
                if (dest[idx] < 26)
                    dest[idx] = (byte) (dest[idx] + 'A');
                else if (dest[idx] < 52)
                    dest[idx] = (byte) (dest[idx] + 'a' - 26);
                else if (dest[idx] < 62)
                    dest[idx] = (byte) (dest[idx] + '0' - 52);
                else if (dest[idx] < 63)
                    dest[idx] = (byte) '+';
                else
                    dest[idx] = (byte) '/';
            }

            // add padding
            for (int idx = dest.length - 1; idx > (d.length * 4) / 3; idx--) {
                dest[idx] = (byte) '=';
            }
            return new String(dest);
        }

        /**
         * Encode a String using Base64 using the default platform encoding
         **/
        public final static String encode(String s) {
            return encode(s.getBytes());
        }

        /**
         * Decode data and return bytes.
         */
        public final static byte[] decode(String str) {
            if (str == null)
                return null;
            byte data[] = str.getBytes();
            return decode(data);
        }

        /**
         * Decode data and return bytes. Assumes that the data passed in is
         * ASCII text.
         */
        public final static byte[] decode(byte[] data) {
            int tail = data.length;
            while (data[tail - 1] == '=')
                tail--;
            byte dest[] = new byte[tail - data.length / 4];

            // ascii printable to 0-63 conversion
            for (int idx = 0; idx < data.length; idx++) {
                if (data[idx] == '=')
                    data[idx] = 0;
                else if (data[idx] == '/')
                    data[idx] = 63;
                else if (data[idx] == '+')
                    data[idx] = 62;
                else if (data[idx] >= '0' && data[idx] <= '9')
                    data[idx] = (byte) (data[idx] - ('0' - 52));
                else if (data[idx] >= 'a' && data[idx] <= 'z')
                    data[idx] = (byte) (data[idx] - ('a' - 26));
                else if (data[idx] >= 'A' && data[idx] <= 'Z')
                    data[idx] = (byte) (data[idx] - 'A');
            }

            // 4-byte to 3-byte conversion
            int sidx, didx;
            for (sidx = 0, didx = 0; didx < dest.length - 2; sidx += 4, didx += 3) {
                dest[didx] = (byte) (((data[sidx] << 2) & 255) | ((data[sidx + 1] >>> 4) & 3));
                dest[didx + 1] = (byte) (((data[sidx + 1] << 4) & 255) | ((data[sidx + 2] >>> 2) & 017));
                dest[didx + 2] = (byte) (((data[sidx + 2] << 6) & 255) | (data[sidx + 3] & 077));
            }
            if (didx < dest.length) {
                dest[didx] = (byte) (((data[sidx] << 2) & 255) | ((data[sidx + 1] >>> 4) & 3));
            }
            if (++didx < dest.length) {
                dest[didx] = (byte) (((data[sidx + 1] << 4) & 255) | ((data[sidx + 2] >>> 2) & 017));
            }
            return dest;
        }
    }

    // From:
    // http://abhinavasblog.blogspot.ca/2011/06/check-for-debuggable-flag-in-android.html
    /*
    Copyright [2011] [Abhinava Srivastava]

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    */
    public static boolean isDebugMode(Context context) {
        boolean debug = false;
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(
                    context.getApplicationContext().getPackageName(),
                    PackageManager.GET_CONFIGURATIONS);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo != null) {
            int flags = packageInfo.applicationInfo.flags;
            if ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                debug = true;
            } else {
                debug = false;
            }
        }
        return debug;
    }

    public static boolean isRooted() {
        //Method 1 check for presence of 'test-keys' in the build tags 
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true;
        }

        //Method 2 check for presence of Superuser app
        try {
            File file = new File("/system/app/Superuser.apk");
            if (file.exists()) {
                return true;
            }
        } catch (Exception e) {
        }

        //Method 3 check for presence of 'su' in the PATH
        String path = null;
        Map<String, String> env = System.getenv();

        if (env != null && (path = env.get("PATH")) != null) {
            String[] dirs = path.split(":");
            for (String dir : dirs) {
                String suPath = dir + "/" + "su";
                File suFile = new File(suPath);
                if (suFile != null && suFile.exists()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getClientPlatformSuffix() {
        String suffix = "";

        // Detect if device is rooted and append to the client_platform string
        if (Utils.isRooted()) {
            suffix += PsiphonConstants.ROOTED;
        }

        // Detect if this is a Play Store build
        if (EmbeddedValues.IS_PLAY_STORE_BUILD) {
            suffix += PsiphonConstants.PLAY_STORE_BUILD;
        }

        return suffix;
    }

    public static boolean supportsAlwaysOnVPN() {
        return Build.VERSION.SDK_INT >= 24;
    }

    public static boolean supportsVpnExclusions() {
        return Build.VERSION.SDK_INT >= 21;
    }

    public static boolean supportsNotificationSound() {
        return Build.VERSION.SDK_INT < 26;
    }

    public static String getLocalTimeString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        String dateStr = sdf.format(date);
        return dateStr;
    }

    public static String getISO8601String(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateStr = sdf.format(date);
        dateStr += "Z";
        return dateStr;
    }

    public static String getISO8601String() {
        return getISO8601String(new Date());
    }

    public static boolean isOnWiFi(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public static String getNetworkTypeName(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo == null ? "" : networkInfo.getTypeName();
    }

    public static String byteCountToDisplaySize(long bytes, boolean si) {
        // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java/3758880#3758880
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static String elapsedTimeToDisplay(long elapsedTimeMilliseconds) {
        long hours = 0;
        long minutes = 0;
        long seconds = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            // http://stackoverflow.com/questions/6710094/how-to-format-an-elapsed-time-interval-in-hhmmss-sss-format-in-java/6710604#6710604
            hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMilliseconds);
            minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMilliseconds - TimeUnit.HOURS.toMillis(hours));
            seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMilliseconds - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes));
        } else {
            hours = elapsedTimeMilliseconds / (1000 * 60 * 60);
            minutes = (elapsedTimeMilliseconds / (1000 * 60)) % 60;
            seconds = (elapsedTimeMilliseconds / (1000)) % 60;
        }

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    public static class RSAEncryptOutput {
        public final byte[] mContentCiphertext;
        public final byte[] mIv;
        public final byte[] mWrappedEncryptionKey;
        public final byte[] mContentMac;
        public final byte[] mWrappedMacKey;

        public RSAEncryptOutput(
                byte[] contentCiphertext, byte[] iv, byte[] wrappedEncryptionKey,
                byte[] contentMac, byte[] wrappedMacKey) {
            mContentCiphertext = contentCiphertext;
            mIv = iv;
            mWrappedEncryptionKey = wrappedEncryptionKey;
            mContentMac = contentMac;
            mWrappedMacKey = wrappedMacKey;
        }
    }

    public static RSAEncryptOutput encryptWithRSA(byte[] data, String rsaPublicKey)
            throws GeneralSecurityException, UnsupportedEncodingException {

        byte[] contentCiphertext = null;
        byte[] iv = null;
        byte[] wrappedEncryptionKey = null;
        byte[] contentMac = null;
        byte[] wrappedMacKey = null;

        int KEY_LENGTH = 128;

        //
        // Encrypt the cleartext content
        //

        KeyGenerator encryptionKeygen = KeyGenerator.getInstance("AES");
        encryptionKeygen.init(KEY_LENGTH);
        SecretKey encryptionKey = encryptionKeygen.generateKey();

        SecureRandom rng = new SecureRandom();
        iv = new byte[16];
        rng.nextBytes(iv);
        IvParameterSpec ivParamSpec = new IvParameterSpec(iv);

        // TODO: should be PCKS7Padding?
        // http://stackoverflow.com/questions/20770072/aes-cbc-pkcs5padding-vs-aes-cbc-pkcs7padding-with-256-key-size-performance-java/20770158#20770158
        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivParamSpec);

        contentCiphertext = aesCipher.doFinal(data);

        // Get the IV. (I don't know if it can be different from the
        // one generated above, but retrieving it here seems safest.)
        iv = aesCipher.getIV();

        //
        // Create a MAC (encrypt-then-MAC).
        //

        KeyGenerator macKeygen = KeyGenerator.getInstance("AES");
        macKeygen.init(KEY_LENGTH);
        SecretKey macKey = macKeygen.generateKey();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(macKey);
        // Include the IV in the MAC'd data, as per http://tools.ietf.org/html/draft-mcgrew-aead-aes-cbc-hmac-sha2-01
        mac.update(iv);
        contentMac = mac.doFinal(contentCiphertext);

        //
        // Ready the public key that we'll use to share keys
        //

        byte[] publicKeyBytes = Base64.decode(rsaPublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
        rsaCipher.init(Cipher.WRAP_MODE, publicKey);

        //
        // Wrap the symmetric keys
        //

        wrappedEncryptionKey = rsaCipher.wrap(encryptionKey);
        wrappedMacKey = rsaCipher.wrap(macKey);

        return new RSAEncryptOutput(contentCiphertext, iv, wrappedEncryptionKey, contentMac, wrappedMacKey);
    }

    public static boolean getUnsafeTrafficAlertsOptInState(Context context) {
        return new AppPreferences(context)
                .getBoolean(context.getString(R.string.unsafeTrafficAlertsPreference),
                        false);
    }
}
