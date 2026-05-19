/*
 * Copyright (c) 2026, Shir o Khorshid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.psiphon3.psiphonlibrary;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class CdnFrontingScanner {
    static final int DEFAULT_MAX_EXPANDED_CANDIDATES = 200000;
    static final int DEFAULT_TOP_RESULT_LIMIT = 32;

    private static final Pattern COMMENT_START = Pattern.compile("(^|\\s)#");
    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "(?<![0-9A-Za-z])((?:25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9]))(?![0-9A-Za-z])");

    private static final TrustManager[] TRUST_ALL_CERTIFICATES = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    private CdnFrontingScanner() {
    }

    static List<String> parseCandidates(String text, int maxExpandedCandidates) {
        Set<String> candidates = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        int maxExpanded = Math.max(1, maxExpandedCandidates);
        for (String rawLine : text.split("\\r?\\n")) {
            String line = stripComment(rawLine);
            if (line.isEmpty()) {
                continue;
            }

            for (String rawToken : line.replace(',', ' ').replace(';', ' ').split("\\s+")) {
                String token = normalizeIpToken(rawToken);
                if (token.isEmpty()) {
                    continue;
                }

                int remainingCapacity = maxExpanded - candidates.size();
                if (remainingCapacity <= 0) {
                    throw new IllegalArgumentException("Input expands beyond " + maxExpanded + " IP candidates");
                }

                List<String> expanded = expandToken(token, remainingCapacity, maxExpanded);
                if (expanded.isEmpty()) {
                    expanded = extractIpv4Literals(rawToken);
                }
                if (candidates.size() + expanded.size() > maxExpanded) {
                    throw new IllegalArgumentException("Input expands beyond " + maxExpanded + " IP candidates");
                }
                candidates.addAll(expanded);
            }
        }
        return new ArrayList<>(candidates);
    }

    static List<String> selectTopIps(List<ScanResult> results, int limit) {
        List<ScanResult> successful = new ArrayList<>();
        for (ScanResult result : results) {
            if (result.responsive) {
                successful.add(result);
            }
        }
        Collections.sort(successful, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult left, ScanResult right) {
                int tlsCompare = Long.compare(latencyOrMax(left.tlsLatencyMs), latencyOrMax(right.tlsLatencyMs));
                if (tlsCompare != 0) {
                    return tlsCompare;
                }
                int tcpCompare = Long.compare(latencyOrMax(left.tcpLatencyMs), latencyOrMax(right.tcpLatencyMs));
                if (tcpCompare != 0) {
                    return tcpCompare;
                }
                return left.ip.compareTo(right.ip);
            }
        });

        int max = Math.max(0, limit);
        Set<String> selected = new LinkedHashSet<>();
        for (ScanResult result : successful) {
            if (selected.size() >= max) {
                break;
            }
            selected.add(result.ip);
        }
        return new ArrayList<>(selected);
    }

    static ScanResult probeRepeated(String ip, String customSni, int timeoutMs, int attempts) {
        int totalAttempts = Math.max(1, attempts);
        int requiredSuccesses = (int) Math.ceil(totalAttempts * 0.67d);
        List<ScanResult> successes = new ArrayList<>();
        ScanResult lastFailure = ScanResult.failed(ip, 0, "not-tested");

        for (int attempt = 0; attempt < totalAttempts; attempt++) {
            ScanResult result = probeOnce(ProbeConfig.forCandidate(ip, customSni, timeoutMs, totalAttempts));
            if (result.responsive) {
                successes.add(result);
            } else {
                lastFailure = result;
            }
        }

        if (successes.size() < requiredSuccesses) {
            return new ScanResult(
                    ip,
                    false,
                    lastFailure.tcpLatencyMs,
                    lastFailure.tlsLatencyMs,
                    successes.size(),
                    totalAttempts,
                    "success-rate-too-low");
        }

        return new ScanResult(
                ip,
                true,
                medianLatency(successes, true),
                medianLatency(successes, false),
                successes.size(),
                totalAttempts,
                "tls-ok");
    }

    private static ScanResult probeOnce(ProbeConfig config) {
        long start = System.nanoTime();
        Socket tcpSocket = null;
        SSLSocket tlsSocket = null;
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(config.ip, 443), config.timeoutMs);
            tcpSocket.setSoTimeout(config.timeoutMs);
            long tcpMs = elapsedMs(start);

            long tlsStart = System.nanoTime();
            SSLSocketFactory factory = newTrustAllSocketFactory();
            tlsSocket = (SSLSocket) factory.createSocket(
                    tcpSocket,
                    config.sniServerName,
                    443,
                    true);
            tcpSocket = null;
            tlsSocket.setSoTimeout(config.timeoutMs);
            tlsSocket.startHandshake();
            long tlsMs = elapsedMs(tlsStart);
            return ScanResult.success(config.ip, tcpMs, tlsMs, 1, 1);
        } catch (Exception e) {
            return ScanResult.failed(config.ip, elapsedMs(start), shortError(e));
        } finally {
            closeQuietly(tlsSocket);
            closeQuietly(tcpSocket);
        }
    }

    private static SSLSocketFactory newTrustAllSocketFactory() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, TRUST_ALL_CERTIFICATES, new SecureRandom());
        return context.getSocketFactory();
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String shortError(Exception e) {
        String name = e.getClass().getSimpleName();
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return name;
        }
        return name + ": " + message;
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1000000L);
    }

    private static long medianLatency(List<ScanResult> results, boolean tcp) {
        List<Long> values = new ArrayList<>();
        for (ScanResult result : results) {
            long latency = tcp ? result.tcpLatencyMs : result.tlsLatencyMs;
            if (latency >= 0) {
                values.add(latency);
            }
        }
        if (values.isEmpty()) {
            return -1;
        }
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    private static long latencyOrMax(long value) {
        return value >= 0 ? value : Long.MAX_VALUE;
    }

    private static List<String> extractIpv4Literals(String token) {
        List<String> found = new ArrayList<>();
        Matcher matcher = IPV4_LITERAL.matcher(token);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<String> expandToken(String token, int maxCount, int maxExpanded) {
        if (token.contains("/")) {
            return expandCidr(token, maxCount, maxExpanded);
        }
        if (token.contains("-")) {
            return expandRange(token, maxCount, maxExpanded);
        }
        if (ipToLong(token) >= 0) {
            return Collections.singletonList(token);
        }
        return Collections.emptyList();
    }

    private static String stripComment(String line) {
        Matcher matcher = COMMENT_START.matcher(line);
        if (matcher.find()) {
            return line.substring(0, matcher.start()).trim();
        }
        return line.trim();
    }

    private static String normalizeIpToken(String token) {
        if (token == null) {
            return "";
        }
        String normalized = token.trim();
        while (normalized.length() > 0 && isWrappingPunctuation(normalized.charAt(0))) {
            normalized = normalized.substring(1);
        }
        while (normalized.length() > 0 &&
                isWrappingPunctuation(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isWrappingPunctuation(char value) {
        return value == '[' || value == ']' ||
                value == '(' || value == ')' ||
                value == '{' || value == '}' ||
                value == '<' || value == '>' ||
                value == '"' || value == '\'' ||
                value == '`' || value == '\u060c' ||
                value == '\u061b' || value == '.';
    }

    private static List<String> expandCidr(String cidr, int maxCount, int maxExpanded) {
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
        if (count > maxCount) {
            throw new IllegalArgumentException("Input expands beyond " + maxExpanded + " IP candidates");
        }
        long networkMask = bits == 0 ? 0L : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
        long network = ip & networkMask;
        List<String> ips = new ArrayList<>();
        for (long offset = 0; offset < count; offset++) {
            ips.add(longToIp(network + offset));
        }
        return ips;
    }

    private static List<String> expandRange(String range, int maxCount, int maxExpanded) {
        String[] parts = range.split("-", -1);
        if (parts.length != 2) {
            return Collections.emptyList();
        }
        long start = ipToLong(parts[0].trim());
        long end = ipToLong(parts[1].trim());
        if (start < 0 || end < start) {
            return Collections.emptyList();
        }
        long count = end - start + 1;
        if (count > maxCount) {
            throw new IllegalArgumentException("Input expands beyond " + maxExpanded + " IP candidates");
        }
        List<String> ips = new ArrayList<>();
        for (long current = start; current <= end; current++) {
            ips.add(longToIp(current));
        }
        return ips;
    }

    private static long ipToLong(String ip) {
        if (ip == null) {
            return -1;
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return -1;
        }
        long result = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return -1;
            }
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

    static final class ProbeConfig {
        final String ip;
        final String sniServerName;
        final String httpHostHeader;
        final int timeoutMs;
        final int attempts;

        private ProbeConfig(String ip, String sniServerName, String httpHostHeader, int timeoutMs, int attempts) {
            this.ip = ip;
            this.sniServerName = sniServerName;
            this.httpHostHeader = httpHostHeader;
            this.timeoutMs = timeoutMs;
            this.attempts = attempts;
        }

        static ProbeConfig forCandidate(String ip, String customSni, int timeoutMs, int attempts) {
            String normalizedSni = normalizeHostname(customSni);
            String sni = normalizedSni.isEmpty() ? ip : normalizedSni;
            return new ProbeConfig(ip, sni, sni, Math.max(500, timeoutMs), Math.max(1, attempts));
        }
    }

    private static String normalizeHostname(String value) {
        if (value == null) {
            return "";
        }
        String hostname = value.trim().toLowerCase(Locale.US);
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        if (hostname.isEmpty() || ipToLong(hostname) >= 0 || hostname.length() > 253) {
            return "";
        }
        String[] labels = hostname.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 ||
                    label.startsWith("-") || label.endsWith("-")) {
                return "";
            }
            for (int i = 0; i < label.length(); i++) {
                char character = label.charAt(i);
                if (!Character.isLetterOrDigit(character) && character != '-') {
                    return "";
                }
            }
        }
        return hostname;
    }

    static final class ScanResult {
        final String ip;
        final boolean responsive;
        final long tcpLatencyMs;
        final long tlsLatencyMs;
        final int successCount;
        final int attempts;
        final String reason;

        private ScanResult(
                String ip,
                boolean responsive,
                long tcpLatencyMs,
                long tlsLatencyMs,
                int successCount,
                int attempts,
                String reason) {
            this.ip = ip;
            this.responsive = responsive;
            this.tcpLatencyMs = tcpLatencyMs;
            this.tlsLatencyMs = tlsLatencyMs;
            this.successCount = successCount;
            this.attempts = attempts;
            this.reason = reason;
        }

        static ScanResult success(String ip, long tcpLatencyMs, long tlsLatencyMs, int successCount, int attempts) {
            return new ScanResult(ip, true, tcpLatencyMs, tlsLatencyMs, successCount, attempts, "tls-ok");
        }

        static ScanResult failed(String ip, long tcpLatencyMs, String reason) {
            return new ScanResult(ip, false, tcpLatencyMs, -1, 0, 1, reason);
        }
    }
}
