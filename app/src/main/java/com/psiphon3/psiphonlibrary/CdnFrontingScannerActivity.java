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

import net.grandcentrix.tray.AppPreferences;

import java.net.InetSocketAddress;
import java.net.Socket;
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
    private static final int MAX_EXPANDED_RANGE_SIZE = 65536;
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
    private ExecutorService executorService;

    private EditText inputView;
    private EditText timeoutView;
    private EditText concurrencyView;
    private EditText maxIpsView;
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView resultsView;
    private Button startButton;
    private Button stopButton;
    private Button applyButton;
    private Button copyButton;

    private final List<ScanResult> responsiveResults = Collections.synchronizedList(new ArrayList<>());
    private int total;
    private final AtomicInteger tested = new AtomicInteger(0);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cdn_fronting_scanner);

        inputView = findViewById(R.id.cdnScannerInput);
        timeoutView = findViewById(R.id.cdnScannerTimeout);
        concurrencyView = findViewById(R.id.cdnScannerConcurrency);
        maxIpsView = findViewById(R.id.cdnScannerMaxIps);
        progressBar = findViewById(R.id.cdnScannerProgress);
        statusView = findViewById(R.id.cdnScannerStatus);
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
    }

    @Override
    protected void onDestroy() {
        stopScan();
        super.onDestroy();
    }

    private void appendInput(String value) {
        String existing = inputView.getText().toString().trim();
        inputView.setText(existing.isEmpty() ? value : existing + "\n" + value);
        inputView.setSelection(inputView.length());
    }

    private void startScan() {
        List<String> ips = parseInput(inputView.getText().toString());
        if (ips.isEmpty()) {
            Toast.makeText(this, R.string.cdnFrontingScannerNoValidIps, Toast.LENGTH_SHORT).show();
            return;
        }

        int timeoutMs = clamp(parseInt(timeoutView, 3000), 500, 10000);
        int concurrency = clamp(parseInt(concurrencyView, 20), 1, 100);
        int maxIps = clamp(parseInt(maxIpsView, 256), 1, 2000);
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
        responsiveResults.clear();
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
        for (String ip : ips) {
            executorService.execute(() -> {
                if (!stopRequested.get()) {
                    ScanResult result = testIp(ip, timeoutMs);
                    if (result.responsive) {
                        responsiveResults.add(result);
                    }
                    tested.incrementAndGet();
                    mainHandler.post(() -> {
                        progressBar.setProgress(tested.get());
                        updateStatus(false);
                        renderResults();
                    });
                }
                if (remaining.decrementAndGet() == 0) {
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

    private void finishScan() {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        applyButton.setEnabled(!responsiveResults.isEmpty());
        copyButton.setEnabled(!responsiveResults.isEmpty());
        updateStatus(true);
        renderResults();
    }

    private void updateStatus(boolean finished) {
        if (finished) {
            int textRes = stopRequested.get()
                    ? R.string.cdnFrontingScannerStopped
                    : R.string.cdnFrontingScannerFinished;
            statusView.setText(getString(textRes, responsiveResults.size(), tested.get()));
        } else {
            statusView.setText(getString(
                    R.string.cdnFrontingScannerRunning, tested.get(), total));
        }
    }

    private void renderResults() {
        List<ScanResult> sorted = new ArrayList<>(responsiveResults);
        Collections.sort(sorted, (left, right) -> Long.compare(left.latencyMs, right.latencyMs));
        if (sorted.isEmpty()) {
            resultsView.setText(R.string.cdnFrontingScannerResultsEmpty);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (ScanResult result : sorted) {
            builder.append(String.format(Locale.US, "%-15s  %d ms%n", result.ip, result.latencyMs));
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
        List<ScanResult> sorted = getSortedResponsiveResults();
        SharedPreferences prefs = getSharedPreferences(
                getString(R.string.moreOptionsPreferencesName), MODE_PRIVATE);
        AppPreferences appPreferences = new AppPreferences(this);
        String preferenceKey = getString(R.string.cdnFrontingCustomIpListPreference);
        String formattedIps;
        int count;
        if (append) {
            Set<String> mergedIps = parseIpEntries(appPreferences.getString(preferenceKey, ""));
            int originalSize = mergedIps.size();
            for (ScanResult result : sorted) {
                mergedIps.add(result.ip);
            }
            formattedIps = joinIpsOnePerLine(mergedIps);
            count = mergedIps.size() - originalSize;
        } else {
            formattedIps = formatIpsOnePerLine(sorted);
            count = sorted.size();
        }
        prefs.edit()
                .putString(preferenceKey, formattedIps)
                .apply();
        appPreferences.put(preferenceKey, formattedIps);
        int messageRes = append
                ? R.string.cdnFrontingScannerAppended
                : R.string.cdnFrontingScannerApplied;
        Toast.makeText(this, getString(messageRes, count), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void copyResults() {
        List<ScanResult> sorted = getSortedResponsiveResults();
        String formattedIps = formatIpsOnePerLine(sorted);
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("CDN edge IPs", formattedIps));
            Toast.makeText(this,
                    getString(R.string.cdnFrontingScannerCopied, sorted.size()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private List<ScanResult> getSortedResponsiveResults() {
        List<ScanResult> sorted = new ArrayList<>(responsiveResults);
        Collections.sort(sorted, (left, right) -> Long.compare(left.latencyMs, right.latencyMs));
        return sorted;
    }

    private String formatIpsOnePerLine(List<ScanResult> sorted) {
        Set<String> ips = new LinkedHashSet<>();
        for (ScanResult result : sorted) {
            ips.add(result.ip);
        }
        return joinIpsOnePerLine(ips);
    }

    private Set<String> parseIpEntries(String text) {
        Set<String> ips = new LinkedHashSet<>();
        for (String entry : text.split("[\\s,;]+")) {
            if (ipToLong(entry.trim()) >= 0) {
                ips.add(entry.trim());
            }
        }
        return ips;
    }

    private String joinIpsOnePerLine(Set<String> ips) {
        StringBuilder builder = new StringBuilder();
        for (String ip : ips) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(ip);
        }
        return builder.toString();
    }

    private ScanResult testIp(String ip, int timeoutMs) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, 443), timeoutMs);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return new ScanResult(ip, true, latencyMs);
        } catch (Exception ignored) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return new ScanResult(ip, false, latencyMs);
        }
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

    static List<String> parseInput(String text) {
        Set<String> results = new LinkedHashSet<>();
        for (String rawLine : text.split("\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.contains("/")) {
                results.addAll(expandCidr(line));
            } else if (line.contains("-")) {
                results.addAll(expandRange(line));
            } else if (ipToLong(line) >= 0) {
                results.add(line);
            }
        }
        return new ArrayList<>(results);
    }

    private static List<String> expandCidr(String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            return Collections.emptyList();
        }
        long ip = ipToLong(parts[0]);
        int bits;
        try {
            bits = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
        if (ip < 0 || bits < 0 || bits > 32) {
            return Collections.emptyList();
        }
        long count = 1L << (32 - bits);
        if (count > MAX_EXPANDED_RANGE_SIZE) {
            return Collections.emptyList();
        }
        long network = ip & (0xFFFFFFFFL << (32 - bits));
        List<String> ips = new ArrayList<>();
        for (long offset = 0; offset < count; offset++) {
            ips.add(longToIp(network + offset));
        }
        return ips;
    }

    private static List<String> expandRange(String range) {
        String[] parts = range.split("-", -1);
        if (parts.length != 2) {
            return Collections.emptyList();
        }
        long start = ipToLong(parts[0].trim());
        long end = ipToLong(parts[1].trim());
        if (start < 0 || end < start) {
            return Collections.emptyList();
        }
        if ((end - start + 1) > MAX_EXPANDED_RANGE_SIZE) {
            return Collections.emptyList();
        }
        List<String> ips = new ArrayList<>();
        for (long current = start; current <= end; current++) {
            ips.add(longToIp(current));
        }
        return ips;
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return -1;
        }
        long result = 0;
        for (String part : parts) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (value < 0 || value > 255) {
                return -1;
            }
            result = (result << 8) | value;
        }
        return result;
    }

    private static String longToIp(long value) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                (value >> 24) & 255,
                (value >> 16) & 255,
                (value >> 8) & 255,
                value & 255);
    }

    private static class ScanResult {
        final String ip;
        final boolean responsive;
        final long latencyMs;

        ScanResult(String ip, boolean responsive, long latencyMs) {
            this.ip = ip;
            this.responsive = responsive;
            this.latencyMs = latencyMs;
        }
    }
}
