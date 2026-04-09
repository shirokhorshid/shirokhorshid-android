package com.psiphon3.psiphonlibrary;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class TunnelNoticeSummaryBuilder {

    private static final Set<String> SKIP_TYPES = new HashSet<>(Arrays.asList(
            "BytesTransferred",
            "TotalBytesTransferred",
            "RemoteServerListResourceDownloadedBytes",
            "ClientUpgradeDownloadedBytes",
            "SLOKSeeded",
            "SessionId",
            "BuildInfo",
            "Debug",
            "PruneServerEntry",
            "Fragmentor",
            "Bursts",
            "LivenessTest",
            "InproxyProxyTotalActivity",
            "ServerTimestamp",
            "ActiveAuthorizationIDs",
            "TrafficRateLimits",
            "NetworkID",
            "BindToDevice"
    ));

    private static final int MAX_LINE = 420;
    private static final int MAX_MESSAGE = 220;

    private TunnelNoticeSummaryBuilder() {
    }

    public static boolean isSkippedNoticeType(@Nullable String noticeType) {
        return noticeType != null && SKIP_TYPES.contains(noticeType);
    }

    /**
     * Quickly extract the noticeType from a diagnostic message.
     *
     * PsiphonTunnel.java delivers messages in one of two formats:
     *   (a) Full JSON envelope:  {"noticeType":"ConnectingServer","data":{...},...}
     *   (b) Prefix format:       ConnectingServer: {"protocol":"OSSH",...}
     *                            Info: {"message":"beast mode active ..."}
     *                            Info: plain text (no JSON data)
     *
     * Returns the noticeType string, or null if unrecognisable.
     */
    @Nullable
    public static String extractNoticeTypeQuick(@Nullable String message) {
        if (message == null) {
            return null;
        }
        String t = message.trim();
        if (t.startsWith("{")) {
            int idx = t.indexOf("\"noticeType\"");
            if (idx < 0) return null;
            int colon = t.indexOf(':', idx + 12);
            if (colon < 0) return null;
            int q1 = t.indexOf('"', colon + 1);
            if (q1 < 0) return null;
            int q2 = t.indexOf('"', q1 + 1);
            if (q2 < 0) return null;
            return t.substring(q1 + 1, q2);
        }
        // Prefix format: "NoticeType: ..."
        int colon = t.indexOf(": ");
        if (colon > 0 && colon < 80) {
            String type = t.substring(0, colon);
            if (isValidNoticeTypeName(type)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static String buildFallbackRawLine(@Nullable String message) {
        if (message == null) {
            return null;
        }
        String type = extractNoticeTypeQuick(message);
        if (type == null || isSkippedNoticeType(type)) {
            return null;
        }
        String one = message.trim().replaceAll("\\s+", " ");
        if (one.length() > 400) {
            one = one.substring(0, 399) + "...";
        }
        return one;
    }

    /**
     * Parse a diagnostic message and return a concise human-readable status line,
     * or null if the notice type is unknown / should be suppressed.
     *
     * Handles both message formats delivered by PsiphonTunnel.java:
     *   (a) Full JSON envelope:  {"noticeType":"ConnectingServer","data":{...},...}
     *   (b) Prefix format:       ConnectingServer: {"protocol":"OSSH",...}
     */
    @Nullable
    public static String buildStatusLine(@Nullable String message) {
        if (message == null) {
            return null;
        }
        String t = message.trim();
        if (t.length() >= 1 && t.charAt(0) == '\ufeff') {
            t = t.substring(1);
        }
        if (t.isEmpty()) {
            return null;
        }

        String noticeType;
        JSONObject data = null;

        if (t.startsWith("{")) {
            // Full JSON envelope: {"noticeType":"...","data":{...},...}
            try {
                JSONObject root = new JSONObject(t);
                noticeType = root.optString("noticeType", "");
                if (noticeType.isEmpty()) return null;
                data = root.optJSONObject("data");
            } catch (JSONException e) {
                return null;
            }
        } else {
            // Prefix format: "NoticeType: <json_or_text>"
            int colon = t.indexOf(": ");
            if (colon <= 0 || colon >= 80) return null;
            noticeType = t.substring(0, colon);
            if (!isValidNoticeTypeName(noticeType)) return null;

            String rest = t.substring(colon + 2).trim();
            if (rest.startsWith("{")) {
                try {
                    data = new JSONObject(rest);
                } catch (JSONException e) {
                    // data stays null; fall through to plain-text handling below
                }
            }
            // If rest is plain text and data is null, we'll fall through to the
            // plain-text branch after the SKIP_TYPES check.
        }

        if (SKIP_TYPES.contains(noticeType)) {
            return null;
        }

        StringBuilder sb = new StringBuilder(noticeType);

        if (data == null) {
            // Plain text suffix (e.g. "Info: beast mode active ...")
            if (!t.startsWith("{")) {
                int colon = t.indexOf(": ");
                if (colon > 0) {
                    String body = t.substring(colon + 2).trim();
                    if (!body.isEmpty()) {
                        if (body.length() > MAX_MESSAGE) {
                            body = body.substring(0, MAX_MESSAGE - 1) + "…";
                        }
                        sb.append(" · ").append(body);
                    }
                }
            }
            return trim(sb);
        }

        appendTextField(sb, data, "message", MAX_MESSAGE);
        appendString(sb, data, "region");
        appendString(sb, data, "protocol");
        appendCandidateNumber(sb, data);
        appendInt(sb, data, "count");
        appendInt(sb, data, "initialCount");
        appendString(sb, data, "duration");
        appendString(sb, data, "diagnosticID");
        appendString(sb, data, "serverRegion");

        if ("CandidateServers".equals(noticeType)) {
            appendStringJoin(sb, data, "limitTunnelProtocols");
            appendStringJoin(sb, data, "initialLimitTunnelProtocols");
        }

        if (sb.length() <= noticeType.length()) {
            appendFallbackDataKeys(sb, data);
        }

        return trim(sb);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isValidNoticeTypeName(String s) {
        if (s == null || s.isEmpty() || s.length() > 80) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    private static void appendFallbackDataKeys(StringBuilder sb, JSONObject data) {
        Iterator<String> keys = data.keys();
        int n = 0;
        while (keys.hasNext() && n < 6) {
            String k = keys.next();
            if ("message".equals(k)) {
                continue;
            }
            try {
                Object v = data.get(k);
                String s = valueToShortString(v);
                if (s != null && !s.isEmpty()) {
                    sb.append(" · ").append(k).append("=").append(s);
                    n++;
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private static String valueToShortString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        if (s.length() > 80) {
            return s.substring(0, 77) + "...";
        }
        return s;
    }

    private static void appendTextField(StringBuilder sb, JSONObject data, String key, int maxLen) {
        String m = data.optString(key, "");
        if (m.isEmpty()) {
            return;
        }
        if (m.length() > maxLen) {
            m = m.substring(0, maxLen - 1) + "…";
        }
        sb.append(" · ").append(m);
    }

    private static void appendString(StringBuilder sb, JSONObject data, String key) {
        String v = data.optString(key, "");
        if (v.isEmpty()) {
            return;
        }
        sb.append(" · ").append(key).append("=").append(v);
    }

    private static void appendInt(StringBuilder sb, JSONObject data, String key) {
        if (!data.has(key)) {
            return;
        }
        try {
            int v = data.getInt(key);
            sb.append(" · ").append(key).append("=").append(v);
        } catch (JSONException e) {
            sb.append(" · ").append(key).append("=").append(data.opt(key));
        }
    }

    private static void appendCandidateNumber(StringBuilder sb, JSONObject data) {
        if (!data.has("candidateNumber")) {
            return;
        }
        Object v = data.opt("candidateNumber");
        if (v == null) {
            return;
        }
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Math.rint(d) == d) {
                sb.append(" · candidate#=").append((long) d);
            } else {
                sb.append(" · candidate#=").append(d);
            }
        } else {
            sb.append(" · candidate#=").append(v);
        }
    }

    private static void appendStringJoin(StringBuilder sb, JSONObject data, String key) {
        Object arr = data.opt(key);
        if (arr == null) {
            return;
        }
        String joined = String.valueOf(arr);
        if (joined.length() > 120) {
            joined = joined.substring(0, 117) + "...";
        }
        sb.append(" · ").append(key).append("=").append(joined);
    }

    private static String trim(StringBuilder sb) {
        String s = sb.toString().replaceAll("\\s+", " ").trim();
        if (s.length() > MAX_LINE) {
            return s.substring(0, MAX_LINE - 1) + "…";
        }
        return s;
    }
}
