/*
 * Copyright (c) 2015, Psiphon Inc.
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

import java.security.SecureRandom;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psiphon3.psiphonlibrary.Utils.MyLog;

public class Diagnostics {

    /**
     * Create a random feedback ID.
     *
     * @return 8 random bytes encoded as a 16 character hex String.
     */
    static public String generateFeedbackId() {
        SecureRandom rnd = new SecureRandom();
        byte[] id = new byte[8];
        rnd.nextBytes(id);
        return Utils.byteArrayToHexString(id);
    }

    /**
     * Create the diagnostic info package.
     *
     * @param context
     * @param sendDiagnosticInfo If true, the user has opted in to including diagnostics with their
     *                           feedback and diagnostics will be included in diagnostic info
     *                           package. Otherwise, diagnostics will be omitted.
     * @param email User email address.
     * @param feedbackText User feedback comment.
     * @param surveyResponsesJson User feedback responses.
     * @param feedbackId Random feedback ID created with generateFeedbackId().
     * @param diagnosticsBefore Optional Date which, if provided, results in only diagnostics which
     *                          occurred before it being including in the resulting diagnostics
     *                          package. This is determined by checking the timestamp of each
     *                          diagnostic entry.
     * @return A String containing the diagnostic info, or `null` if there is
     * an error.
     */
    static final public String create(
            @NonNull Context context,
            @NonNull boolean sendDiagnosticInfo,
            @NonNull String email,
            @NonNull String feedbackText,
            @NonNull String surveyResponsesJson,
            @NonNull String feedbackId,
            @Nullable Date diagnosticsBefore) throws Exception {
        // Our attachment is JSON, which is then encrypted, and the
        // encryption elements stored in JSON.

        String diagnosticJSON;

        try {
            /*
             * Metadata
             */

            JSONObject metadata = new JSONObject();

            metadata.put("platform", "android");
            metadata.put("version", 4);
            metadata.put("id", feedbackId);

            /*
             * System Information
             */

            JSONObject sysInfo_Build = new JSONObject();
            sysInfo_Build.put("BRAND", Build.BRAND);
            sysInfo_Build.put("CPU_ABI", Build.CPU_ABI);
            sysInfo_Build.put("MANUFACTURER", Build.MANUFACTURER);
            sysInfo_Build.put("MODEL", Build.MODEL);
            sysInfo_Build.put("DISPLAY", Build.DISPLAY);
            sysInfo_Build.put("TAGS", Build.TAGS);
            sysInfo_Build.put("VERSION__CODENAME", Build.VERSION.CODENAME);
            sysInfo_Build.put("VERSION__RELEASE", Build.VERSION.RELEASE);
            sysInfo_Build.put("VERSION__SDK_INT", Build.VERSION.SDK_INT);

            JSONObject sysInfo_psiphonEmbeddedValues = new JSONObject();
            sysInfo_psiphonEmbeddedValues.put("PROPAGATION_CHANNEL_ID", EmbeddedValues.PROPAGATION_CHANNEL_ID);
            sysInfo_psiphonEmbeddedValues.put("SPONSOR_ID", EmbeddedValues.SPONSOR_ID);
            sysInfo_psiphonEmbeddedValues.put("CLIENT_VERSION", EmbeddedValues.CLIENT_VERSION);

            JSONObject sysInfo = new JSONObject();
            sysInfo.put("isRooted", Utils.isRooted());
            sysInfo.put("isPlayStoreBuild", EmbeddedValues.IS_PLAY_STORE_BUILD);
            sysInfo.put("language", Locale.getDefault().getLanguage());
            sysInfo.put("networkTypeName", Utils.getNetworkTypeName(context));
            sysInfo.put("Build", sysInfo_Build);
            sysInfo.put("PsiphonInfo", sysInfo_psiphonEmbeddedValues);

            /*
             * Diagnostic History
             */

            JSONArray diagnosticHistory = new JSONArray();

            for (StatusList.DiagnosticEntry item : StatusList.cloneDiagnosticHistory()) {
                if (diagnosticsBefore != null && item.timestamp().after(diagnosticsBefore)) {
                    continue;
                }
                JSONObject entry = new JSONObject();
                entry.put("timestamp!!timestamp", Utils.getISO8601String(item.timestamp()));
                entry.put("msg", item.msg() == null ? JSONObject.NULL : item.msg());
                entry.put("data", item.data() == null ? JSONObject.NULL : item.data());
                diagnosticHistory.put(entry);
            }

            /*
             * Status History
             */

            JSONArray statusHistory = new JSONArray();

            for (StatusList.StatusEntry internalEntry : StatusList.cloneStatusHistory()) {
                // Don't send any sensitive logs or debug logs
                if (internalEntry.sensitivity() == MyLog.Sensitivity.SENSITIVE_LOG
                        || internalEntry.priority() == Log.DEBUG) {
                    continue;
                }

                if (diagnosticsBefore != null && internalEntry.timestamp().after(diagnosticsBefore)) {
                    continue;
                }

                JSONObject statusEntry = new JSONObject();

                String idName = context.getResources().getResourceEntryName(internalEntry.stringId());
                statusEntry.put("id", idName);
                statusEntry.put("timestamp!!timestamp", Utils.getISO8601String(internalEntry.timestamp()));
                statusEntry.put("priority", internalEntry.priority());
                statusEntry.put("formatArgs", JSONObject.NULL);
                statusEntry.put("throwable", JSONObject.NULL);

                if (internalEntry.formatArgs() != null && internalEntry.formatArgs().length > 0
                        // Don't send any sensitive format args
                        && internalEntry.sensitivity() != MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS) {
                    JSONArray formatArgs = new JSONArray();
                    for (Object o : internalEntry.formatArgs()) {
                        formatArgs.put(o);
                    }
                    statusEntry.put("formatArgs", formatArgs);
                }

                if (internalEntry.throwable() != null) {
                    JSONObject throwable = new JSONObject();

                    throwable.put("message", internalEntry.throwable().toString());

                    JSONArray stack = new JSONArray();
                    for (StackTraceElement element : internalEntry.throwable().getStackTrace()) {
                        stack.put(element.toString());
                    }
                    throwable.put("stack", stack);

                    statusEntry.put("throwable", throwable);
                }

                statusHistory.put(statusEntry);
            }

            /*
             * JSON-ify the diagnostic info
             */

            JSONObject diagnosticInfo = new JSONObject();
            diagnosticInfo.put("SystemInformation", sysInfo);
            diagnosticInfo.put("DiagnosticHistory", diagnosticHistory);
            diagnosticInfo.put("StatusHistory", statusHistory);

            JSONObject diagnosticObject = new JSONObject();
            diagnosticObject.put("Metadata", metadata);

            if (sendDiagnosticInfo) {
                diagnosticObject.put("DiagnosticInfo", diagnosticInfo);
            }

            if (feedbackText.length() > 0 || surveyResponsesJson.length() > 0) {
                JSONObject feedbackInfo = new JSONObject();
                feedbackInfo.put("email", email);

                JSONObject feedbackMessageInfo = new JSONObject();
                feedbackMessageInfo.put("text", feedbackText);
                feedbackInfo.put("Message", feedbackMessageInfo);

                JSONObject feedbackSurveyInfo = new JSONObject();
                feedbackSurveyInfo.put("json", surveyResponsesJson);
                feedbackInfo.put("Survey", feedbackSurveyInfo);

                diagnosticObject.put("Feedback", feedbackInfo);
            }

            diagnosticJSON = diagnosticObject.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (diagnosticJSON == null) {
            throw new AssertionError("diagnostics JSON null");
        }

        return diagnosticJSON;
    }
}
