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

import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;

import com.psiphon3.psiphonlibrary.TunnelServiceInteractor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class PsiphonHostApduService extends HostApduService {
    private static final String TAG = "PsiphonNfc";


    // Track AID_SELECT state
    private boolean aidSelected;

    // Complete payload to send to the NFC reader when read binary command is received
    private byte[] payload;
    private TunnelServiceInteractor tunnelServiceInteractor;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public static final byte[] SELECT_AID = {
            (byte) 0x00, // CLA Class
            (byte) 0xA4, // INS Instruction
            (byte) 0x04, // P1  Parameter 1
            (byte) 0x00, // P2  Parameter 2
            (byte) 0x0A, // Length of AID
            (byte) 0x50, (byte) 0x73, (byte) 0x69, (byte) 0x70, (byte) 0x68, (byte) 0x6f, (byte) 0x6e, (byte) 0x4e, (byte) 0x66, (byte) 0x63, // AID ("PsiphonNfc" hexed)
            (byte) 0x00 // Le field
    };

    public static final byte[] A_OK = {
            (byte) 0x90, (byte) 0x00
    };

    public final byte[] A_ERROR = {
            (byte) 0x6A, (byte) 0x82
    };

    @Override
    public void onCreate() {
        super.onCreate();
        aidSelected = false;
        payload = null;
        tunnelServiceInteractor = new TunnelServiceInteractor(this, false);
        tunnelServiceInteractor.onStart(this);
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (Arrays.equals(SELECT_AID, commandApdu)) {
            aidSelected = true;
            return A_OK;
        } else if (aidSelected && commandApdu.length == 5 && commandApdu[0] == (byte)0x00 && commandApdu[1] == (byte)0xb0) {
            Single<byte[]> payLoadSingle;
            if (payload == null) {
                payLoadSingle =
                        tunnelServiceInteractor.exportNfcDataSingle()
                                .map(s -> {
                                    byte[] exportDataBytes = s.getBytes(StandardCharsets.US_ASCII);
                                    int len = exportDataBytes.length + 2;
                                    payload = new byte[len];
                                    payload[0] = (byte) (len >>> 8);
                                    payload[1] = (byte) len;
                                    System.arraycopy(exportDataBytes, 0, payload, 2, exportDataBytes.length);
                                    return payload;
                                });
            } else {
                payLoadSingle = Single.just(payload);
            }

            compositeDisposable.add(payLoadSingle
                    .doOnSuccess(pl -> {
                        // Read binary command received,
                        // get the offset an le (length) data
                        int offset = (0x00ff & commandApdu[2]) * 256 + (0x00ff & commandApdu[3]);
                        int le = 0x00ff & commandApdu[4];

                        byte[] responseApdu = new byte[le + A_OK.length];

                        if (offset + le <= payload.length) {
                            System.arraycopy(payload, offset, responseApdu, 0, le);
                            System.arraycopy(A_OK, 0, responseApdu, le, A_OK.length);
                            sendResponseApdu(responseApdu);
                        }
                    })
                    .subscribe());
            // Return null to not send any responseApdu, the responseApdu will be sent from the
            // payLoadSingle Rx subscription above.
            return null;
        }

        return A_ERROR;
    }

    @Override
    public void onDeactivated(int reason) {
        aidSelected = false;
        payload = null;
        compositeDisposable.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tunnelServiceInteractor.onStop(this);
        tunnelServiceInteractor.onDestroy(this);
        compositeDisposable.dispose();
    }
}
