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

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.psiphon3.psiphonlibrary.LocalizedActivities;
import com.psiphon3.psiphonlibrary.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PsiphonBumpNfcReaderActivity extends LocalizedActivities.AppCompatActivity implements NfcAdapter.ReaderCallback {
    private static final String PREFS_NAME = "PsiphonBumpNfcReaderActivityPrefs";
    private NfcAdapter nfcAdapter;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private TextView messageTextView;
    private ProgressBar progressBar;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Utils.supportsPsiphonBump(this)) {
            finish();
            return;
        }

        setContentView(R.layout.psiphon_nfc_reader_layout);
        setFinishOnTouchOutside(false);
        findViewById(R.id.close_btn).setOnClickListener(v -> finish());
        findViewById(R.id.help_btn).setOnClickListener(v -> openHelp());
        messageTextView = findViewById(R.id.messageTextView);
        messageTextView.setText(getString(R.string.nfc_reader_default_message));
        progressBar = findViewById(R.id.progressBar);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    private void openHelp() {
        Intent intent = new Intent(this, PsiphonBumpHelpActivity.class);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check SharedPreferences and open PsiphonBumpHelpActivity if the user has not seen the help
        // screen yet
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!preferences.getBoolean("help_shown", false)) {
            openHelp();
            preferences.edit().putBoolean("help_shown", true).apply();
            return;
        }

        // Open the NFC settings if NFC is not enabled
        if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this, getString(R.string.enable_nfc_in_settings), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            startActivity(intent);
            finish();
            return;
        }

        // Enable reader mode
        Bundle options = new Bundle();
        // Workaround for Nfc implementations that poll the card too fast
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
        nfcAdapter.enableReaderMode(this,
                this,
                // Type A, B, and skip system NDEF check
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                options);

        // Observe tunnel state changes and close the activity when the tunnel is connected
        compositeDisposable.add(getTunnelServiceInteractor().tunnelStateFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(tunnelState -> {
                            if (tunnelState.isRunning() && tunnelState.connectionData().isConnected())
                                finish();
                        }
                )
                .subscribe());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
        compositeDisposable.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            progressBar.setProgress(0, true);
        } else {
            progressBar.setProgress(0);
        }
        try {
            IsoDep isoDep = IsoDep.get(tag);

            if (isoDep != null) {
                isoDep.connect();
                isoDep.setTimeout(10000);
                byte[] commandApdu = PsiphonHostApduService.SELECT_AID;
                byte[] responseApdu = isoDep.transceive(commandApdu);

                if (!rApduSuccess(responseApdu)) {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(0, true);
                        }
                        else {
                            progressBar.setProgress(0);
                        }
                        messageTextView.setText(R.string.nfc_export_unsupported_tag);
                    });
                    isoDep.close();
                    return;
                }

                byte[] readBinaryHeader = new byte[]{(byte) 0x00, (byte) 0xB0};
                commandApdu = Arrays.copyOf(readBinaryHeader, readBinaryHeader.length + 3);

                int offset = 0;
                int length = 2;

                commandApdu[2] = (byte) (offset >>> 8);
                commandApdu[3] = (byte) (offset & 0xFF);

                // First we read the NLEN of the payload
                commandApdu[4] = (byte) (length & 0xFF);

                responseApdu = isoDep.transceive(commandApdu);
                if (!rApduSuccess(responseApdu)) {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(0, true);
                        }
                        else {
                            progressBar.setProgress(0);
                        }
                        messageTextView.setText(R.string.nfc_export_bad_data);
                    });
                    isoDep.close();
                    return;
                }
                byte[] nlenBytes = Arrays.copyOfRange(responseApdu, 0, 2);
                short payloadLength = (short) ((nlenBytes[0] & 0xFF) << 8 | nlenBytes[1] & 0xFF);

                // Now we read the payload
                byte[] data = new byte[payloadLength - 2];

                runOnUiThread(() -> {
                    progressBar.setMax(payloadLength - 2);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        progressBar.setProgress(0, true);
                    }
                    else {
                        progressBar.setProgress(0);
                    }
                    messageTextView.setText(R.string.nfc_export_reading_data);
                });

                offset += 2;
                while (offset < payloadLength) {
                    commandApdu[2] = (byte) (offset >> 8);
                    commandApdu[3] = (byte) (offset & 0xFF);
                    commandApdu[4] = clampIntToByte(payloadLength - offset);
                    responseApdu = isoDep.transceive(commandApdu);
                    if (!rApduSuccess(responseApdu)) {
                        runOnUiThread(() -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                progressBar.setProgress(0, true);
                            }
                            else {
                                progressBar.setProgress(0);
                            }
                            messageTextView.setText(R.string.nfc_export_bad_data);
                        });
                        isoDep.close();
                        return;
                    }
                    int dataLength = responseApdu.length - 2;
                    final int progress = offset - 2;
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(progress, true);
                        }
                        else {
                            progressBar.setProgress(progress);
                        }});
                    System.arraycopy(responseApdu, 0, data, offset - 2, dataLength);
                    offset += dataLength;
                }
                String dataString = new String(data, StandardCharsets.US_ASCII);
                if (TextUtils.isEmpty(dataString)) {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(0, true);
                        }
                        else {
                            progressBar.setProgress(0);
                        }
                        messageTextView.setText(R.string.nfc_export_data_empty);
                    });
                } else {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(payloadLength - 2, true);
                        }
                        else {
                            progressBar.setProgress(payloadLength - 2);
                        }
                        messageTextView.setText(R.string.nfc_export_success);
                    });
                }
                getTunnelServiceInteractor().importNfcData(dataString);
                isoDep.close();
            } else {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        progressBar.setProgress(0, true);
                    }
                    else {
                        progressBar.setProgress(0);
                    }
                    messageTextView.setText(R.string.nfc_export_unsupported_tag);
                });
            }
        } catch (IOException e) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(0, true);
                }
                else {
                    progressBar.setProgress(0);
                }
                messageTextView.setText(R.string.nfc_export_lost_tag);
            });
        }
    }

    private byte clampIntToByte(int length) {
        // Some devices are only able to transmit 253 bytes at a time
        if (length > 0xFD) {
            return (byte) 0xFD;
        }
        return (byte) length;
    }

    public boolean rApduSuccess(final byte[] pByte) {
        byte[] resultValue = Arrays.copyOfRange(pByte, pByte.length - 2, pByte.length);
        return Arrays.equals(resultValue, PsiphonHostApduService.A_OK);
    }
}
