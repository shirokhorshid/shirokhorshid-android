/*
 * Copyright (c) 2019, Psiphon Inc.
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

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Parcelable;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * A set of utils for exchanging server connection info between devices
 */
public class ConnectionInfoExchangeUtils {

    // The mime type to be used for psiphon NFC exchanges
    public static final String NFC_MIME_TYPE = "psiphon://nfc";

    // The charset to use when encoding and decoding connection info payloads for NFC exchange
    private static final String NFC_EXCHANGE_CHARSET = Charset.defaultCharset().name();


    /**
     * @param intent an intent to check
     * @return true if the intents action is for NFC discovered; otherwise false
     */
    public static boolean isNfcDiscoveredIntent(Intent intent) {
        // ACTION_NDEF_DISCOVERED wasn't added until SDK 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            return NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction());
        }

        return false;
    }

    /**
     * Convert the connection info from bytes to a string.
     * Useful for NFC exchanges.
     *
     * @param connectionInfo the connectionInfo to be converted back to a string
     * @return connectionInfo encoded as US-ASCII; "" if unable to encode
     */
    public static String encodeConnectionInfo(byte[] connectionInfo) {
        try {
            // Try to encode the raw bytes
            return new String(connectionInfo, ConnectionInfoExchangeUtils.NFC_EXCHANGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            Utils.MyLog.d("unable to get connection info string", e);
            return "";
        }
    }

    /**
     * Convert the connection info from a string to bytes.
     * Useful for NFC exchanges.
     *
     * @param connectionInfo the connectionInfo to be converted to raw bytes
     * @return connectionInfo decoded as US-ASCII; {} if unable to decode
     */
    public static byte[] decodeConnectionInfo(String connectionInfo) {
        try {
            // Try to decode the string
            return connectionInfo.getBytes(ConnectionInfoExchangeUtils.NFC_EXCHANGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            Utils.MyLog.d("unable to get connection info bytes", e);
            return new byte[]{};
        }
    }

    /**
     * Gets the connection info payload string from an NFC bump intent
     *
     * @param intent an intent created by a NFC bump to exchange server connection info
     * @return the connection info string encoded in the intent; "" if unable to retrieve properly
     */
    public static String getConnectionInfoPayloadFromNfcIntent(Intent intent) {
        Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

        // Only one message should have been sent during NFC beam so retrieve that
        NdefMessage msg = (NdefMessage) messages[0];
        byte[] payload = msg.getRecords()[0].getPayload();
        return encodeConnectionInfo(payload);
    }

    /**
     * @return true if NFC is supported by the android version
     */
    public static Boolean isNfcSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }
}
