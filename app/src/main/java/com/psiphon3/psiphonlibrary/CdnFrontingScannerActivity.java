/*
 * Copyright (c) 2026, Shir o Khorshid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.psiphon3.psiphonlibrary;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.psiphon3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CdnFrontingScannerActivity extends LocalizedActivities.AppCompatActivity {
    private static final int MAX_INPUT_CANDIDATES = CdnFrontingScanner.DEFAULT_MAX_EXPANDED_CANDIDATES;
    private static final int TOP_RESULTS_LIMIT = CdnFrontingScanner.DEFAULT_TOP_RESULT_LIMIT;
    private static final int MAX_RENDERED_RESULTS = 80;
    private static final String[] PRESET_RANGES = {
            "",
            "2.16.0.0/16",
            "2.17.0.0/16",
            "2.18.0.0/16",
            "23.32.0.0/16",
            "23.72.0.0/16",
            "23.192.0.0/16",
            "23.193.0.0/16",
            "104.64.0.0/16",
            "104.65.0.0/16",
            "104.103.0.0/16",
            "184.24.0.0/16",
            "184.84.0.0/16",
            "184.86.0.0/16"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean scanFinished = new AtomicBoolean(true);
    private final AtomicInteger tested = new AtomicInteger(0);
    private final AtomicInteger scanGeneration = new AtomicInteger(0);
    private final List<CdnFrontingScanner.ScanResult> scanResults =
            Collections.synchronizedList(new ArrayList<CdnFrontingScanner.ScanResult>());

    private ExecutorService executorService;
    private EditText inputView;
    private EditText timeoutView;
    private EditText concurrencyView;
    private EditText attemptsView;
    private EditText maxIpsView;
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView currentSniView;
    private TextView resultsView;
    private Button startButton;
    private Button stopButton;
    private Button applyButton;
    private Button copyButton;
    private int total;
    private String probeSni = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cdn_fronting_scanner);

        inputView = findViewById(R.id.cdnScannerInput);
        timeoutView = findViewById(R.id.cdnScannerTimeout);
        concurrencyView = findViewById(R.id.cdnScannerConcurrency);
        attemptsView = findViewById(R.id.cdnScannerAttempts);
        maxIpsView = findViewById(R.id.cdnScannerMaxIps);
        progressBar = findViewById(R.id.cdnScannerProgress);
        statusView = findViewById(R.id.cdnScannerStatus);
        currentSniView = findViewById(R.id.cdnScannerCurrentSni);
        resultsView = findViewById(R.id.cdnScannerResults);
        startButton = findViewById(R.id.cdnScannerStart);
        stopButton = findViewById(R.id.cdnScannerStop);
        applyButton = findViewById(R.id.cdnScannerApply);
        copyButton = findViewById(R.id.cdnScannerCopy);

        Spinner presetSpinner = findViewById(R.id.cdnScannerPresetSpinner);
        String[] presetLabels = PRESET_RANGES.clone();
        presetLabels[0] = getString(R.string.cdnFrontingScannerPresetNone);
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, presetLabels);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    appendInput(PRESET_RANGES[position]);
                    presetSpinner.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        startButton.setOnClickListener(v -> startScan());
        stopButton.setOnClickListener(v -> stopScan());
        applyButton.setOnClickListener(v -> applyResults());
        copyButton.setOnClickListener(v -> copyResults());

        refreshCurrentSni();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentSni();
    }

    @Override
    protected void onDestroy() {
        stopScan();
        super.onDestroy();
    }

    private void refreshCurrentSni() {
        probeSni = getMoreOptionsPreferences()
                .getString(getString(R.string.cdnFrontingCustomSniPreference), "");
        String displaySni = probeSni == null || probeSni.trim().isEmpty()
                ? getString(R.string.cdnFrontingScannerBlankSniBehavior)
                : probeSni.trim();
        currentSniView.setText(getString(R.string.cdnFrontingScannerCurrentSni, displaySni));
    }

    private SharedPreferences getMoreOptionsPreferences() {
        return getSharedPreferences(getString(R.string.moreOptionsPreferencesName), MODE_PRIVATE);
    }

    private void appendInput(String value) {
        String existing = inputView.getText().toString().trim();
        inputView.setText(existing.isEmpty() ? value : existing + "\n" + value);
        inputView.setSelection(inputView.length());
    }

    private void startScan() {
        List<String> ips;
        try {
            ips = CdnFrontingScanner.parseCandidates(inputView.getText().toString(), MAX_INPUT_CANDIDATES);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (ips.isEmpty()) {
            Toast.makeText(this, R.string.cdnFrontingScannerNoValidIps, Toast.LENGTH_SHORT).show();
            return;
        }

        int timeoutMs = clamp(parseInt(timeoutView, 3000), 500, 10000);
        int concurrency = clamp(parseInt(concurrencyView, 20), 1, 64);
        int attempts = clamp(parseInt(attemptsView, 3), 1, 5);
        int maxIps = clamp(parseInt(maxIpsView, 512), 1, 5000);
        if (ips.size() > maxIps) {
            Collections.shuffle(ips);
            ips = new ArrayList<>(ips.subList(0, maxIps));
            Toast.makeText(this,
                    getString(R.string.cdnFrontingScannerSampled, maxIps),
                    Toast.LENGTH_SHORT).show();
        }

        total = ips.size();
        tested.set(0);
        stopRequested.set(false);
        scanFinished.set(false);
        scanResults.clear();
        progressBar.setMax(total);
        progressBar.setProgress(0);
        resultsView.setText(R.string.cdnFrontingScannerResultsEmpty);
        applyButton.setEnabled(false);
        copyButton.setEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        updateStatus(false);

        executorService = Executors.newFixedThreadPool(concurrency);
        final AtomicInteger remaining = new AtomicInteger(total);
        final String configuredSni = probeSni == null ? "" : probeSni.trim();
        final int scanId = scanGeneration.incrementAndGet();
        for (String candidateIp : ips) {
            final String ip = candidateIp;
            executorService.execute(() -> {
                if (isActiveScan(scanId)) {
                    CdnFrontingScanner.ScanResult result =
                            CdnFrontingScanner.probeRepeated(ip, configuredSni, timeoutMs, attempts);
                    if (isActiveScan(scanId)) {
                        scanResults.add(result);
                        tested.incrementAndGet();
                        mainHandler.post(() -> {
                            if (isActiveScan(scanId)) {
                                progressBar.setProgress(tested.get());
                                updateStatus(false);
                                renderResults();
                            }
                        });
                    }
                }
                if (remaining.decrementAndGet() == 0 && scanGeneration.get() == scanId) {
                    mainHandler.post(this::finishScan);
                }
            });
        }
        executorService.shutdown();
    }

    private void stopScan() {
        stopRequested.set(true);
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (startButton != null && !startButton.isEnabled()) {
            finishScan();
        }
    }

    private boolean isActiveScan(int scanId) {
        return scanGeneration.get() == scanId && !stopRequested.get() && !scanFinished.get();
    }

    private void finishScan() {
        if (!scanFinished.compareAndSet(false, true)) {
            return;
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        boolean hasSelectedResults = !getSelectedIps().isEmpty();
        applyButton.setEnabled(hasSelectedResults);
        copyButton.setEnabled(hasSelectedResults);
        updateStatus(true);
        renderResults();
    }

    private void updateStatus(boolean finished) {
        int responsive = countResponsiveResults();
        if (finished) {
            int textRes = stopRequested.get()
                    ? R.string.cdnFrontingScannerStopped
                    : R.string.cdnFrontingScannerFinished;
            statusView.setText(getString(textRes, responsive, tested.get()));
        } else {
            statusView.setText(getString(
                    R.string.cdnFrontingScannerRunning, tested.get(), total, responsive));
        }
    }

    private int countResponsiveResults() {
        int count = 0;
        synchronized (scanResults) {
            for (CdnFrontingScanner.ScanResult result : scanResults) {
                if (result.responsive) {
                    count++;
                }
            }
        }
        return count;
    }

    private void renderResults() {
        List<CdnFrontingScanner.ScanResult> sorted = getSortedResponsiveResults();
        if (sorted.isEmpty()) {
            resultsView.setText(R.string.cdnFrontingScannerResultsEmpty);
            return;
        }

        StringBuilder builder = new StringBuilder();
        int rendered = 0;
        for (CdnFrontingScanner.ScanResult result : sorted) {
            if (rendered >= MAX_RENDERED_RESULTS) {
                builder.append(getString(R.string.cdnFrontingScannerMoreResults, sorted.size() - rendered));
                break;
            }
            builder.append(String.format(Locale.US, "%-15s  TLS %4d ms  %d/%d%n",
                    result.ip,
                    result.tlsLatencyMs,
                    result.successCount,
                    result.attempts));
            rendered++;
        }
        resultsView.setText(builder.toString().trim());
    }

    private void applyResults() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cdnFrontingScannerApplyTitle)
                .setMessage(R.string.cdnFrontingScannerApplyMessage)
                .setPositiveButton(R.string.cdnFrontingScannerReplace,
                        (dialog, which) -> saveResults(false))
                .setNegativeButton(R.string.cdnFrontingScannerAppend,
                        (dialog, which) -> saveResults(true))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void saveResults(boolean append) {
        List<String> selectedIps = getSelectedIps();
        if (selectedIps.isEmpty()) {
            return;
        }

        SharedPreferences prefs = getMoreOptionsPreferences();
        String preferenceKey = getString(R.string.cdnFrontingCustomIpListPreference);
        String formattedIps;
        int count;
        if (append) {
            Set<String> mergedIps = new LinkedHashSet<>();
            try {
                mergedIps.addAll(CdnFrontingScanner.parseCandidates(
                        prefs.getString(preferenceKey, ""), 1000));
            } catch (IllegalArgumentException ignored) {
            }
            int originalSize = mergedIps.size();
            mergedIps.addAll(selectedIps);
            formattedIps = joinIpsOnePerLine(new ArrayList<>(mergedIps));
            count = mergedIps.size() - originalSize;
        } else {
            formattedIps = joinIpsOnePerLine(selectedIps);
            count = selectedIps.size();
        }

        prefs.edit()
                .putString(preferenceKey, formattedIps)
                .apply();
        setResult(RESULT_OK);
        int messageRes = append
                ? R.string.cdnFrontingScannerAppended
                : R.string.cdnFrontingScannerApplied;
        Toast.makeText(this, getString(messageRes, count), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void copyResults() {
        List<String> selectedIps = getSelectedIps();
        String formattedIps = joinIpsOnePerLine(selectedIps);
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("CDN edge IPs", formattedIps));
            Toast.makeText(this,
                    getString(R.string.cdnFrontingScannerCopied, selectedIps.size()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> getSelectedIps() {
        synchronized (scanResults) {
            return CdnFrontingScanner.selectTopIps(
                    new ArrayList<>(scanResults), TOP_RESULTS_LIMIT);
        }
    }

    private List<CdnFrontingScanner.ScanResult> getSortedResponsiveResults() {
        List<CdnFrontingScanner.ScanResult> sorted = new ArrayList<>();
        synchronized (scanResults) {
            for (CdnFrontingScanner.ScanResult result : scanResults) {
                if (result.responsive) {
                    sorted.add(result);
                }
            }
        }
        Collections.sort(sorted, (left, right) -> {
            int tlsCompare = Long.compare(left.tlsLatencyMs, right.tlsLatencyMs);
            if (tlsCompare != 0) {
                return tlsCompare;
            }
            int tcpCompare = Long.compare(left.tcpLatencyMs, right.tcpLatencyMs);
            if (tcpCompare != 0) {
                return tcpCompare;
            }
            return left.ip.compareTo(right.ip);
        });
        return sorted;
    }

    private String joinIpsOnePerLine(List<String> ips) {
        StringBuilder builder = new StringBuilder();
        for (String ip : ips) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(ip);
        }
        return builder.toString();
    }

    private int parseInt(EditText view, int fallback) {
        try {
            return Integer.parseInt(view.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
