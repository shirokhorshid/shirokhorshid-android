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

package com.psiphon3.log;

import android.content.Context;
import android.util.Log;

import androidx.annotation.StringRes;

import com.psiphon3.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;

public class MyLog {
    private static final String TAG = "Psiphon";

    // It is expected that the logger implementation will be an Activity, so
    // we're only going to hold a weak reference to it -- we don't want to
    // interfere with it being destroyed in low memory situations. This class
    // can cope with the logger going away and being re-set later on.
    static private WeakReference<ILogger> logger = new WeakReference<>(null);


    public interface ILogger {
        Context getContext();
    }

    /**
     * Used to indicate the sensitivity level of the log. This will affect
     * log handling in some situations (like sending as diagnostic info).
     * "Sensitive" refers to info that might identify the user or their
     * activities.
     */
    public static class Sensitivity {
        /**
         * The log does not contain sensitive information.
         */
        public static final int NOT_SENSITIVE = 1;

        /**
         * The log message itself is sensitive information.
         */
        public static final int SENSITIVE_LOG = 2;

        /**
         * The format arguments to the log messages are sensitive, but the
         * log message itself is not.
         */
        public static final int SENSITIVE_FORMAT_ARGS = 4;
    }

    static public void setLogger(ILogger logger) {
        MyLog.logger = new WeakReference<>(logger);
    }

    // Status log with priority Log.VERBOSE
    // Displayed to the user and included in feedback if the user consents
    static public void v(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.VERBOSE, new Date());
    }

    // Diagnostic log with priority Log.VERBOSE
    // Internal only and included in feedback if the user consents
    static public void v(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.VERBOSE, new Date());
    }

    // Status log with priority Log.INFO
    // Displayed to the user and included in feedback if the user consents
    static public void i(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.INFO, new Date());
    }

    // Diagnostic log with priority Log.INFO
    // Internal only and included in feedback if the user consents
    static public void i(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.INFO, new Date());
    }

    // Status log with priority Log.WARN
    // Displayed to the user and included in feedback if the user consents
    static public void w(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.WARN, new Date());
    }

    // Diagnostic log with priority Log.WARN
    // Internal only and included in feedback if the user consents
    static public void w(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.WARN, new Date());
    }

    // Status log with priority Log.ERROR
    // Displayed to the user and included in feedback if the user consents
    static public void e(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.ERROR, new Date());
    }

    // Diagnostic log with priority Log.ERROR
    // Internal only and included in feedback if the user consents
    static public void e(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.ERROR, new Date());
    }

    private static void storeStatusLog(@StringRes int resId, Object[] formatArgs, int sensitivity,
                                       int priority, Date timestamp) {
        if (logger.get() == null) {
            return;
        }
        try {
            JSONObject logJsonObject = new JSONObject();

            String stringResourceName = logger.get().getContext().getResources().getResourceName(resId);
            logJsonObject.put("stringResourceName", stringResourceName);

            logJsonObject.put("sensitivity", sensitivity);

            JSONArray formatArgsJsonArray = new JSONArray();
            for (Object arg : formatArgs) {
                formatArgsJsonArray.put(arg);
            }

            logJsonObject.put("formatArgs", formatArgsJsonArray);

            storeLog(logJsonObject.toString(), false, priority, timestamp.getTime());
        } catch (JSONException ignored) {
            // fail silently
        }
    }

    private static void storeDiagnosticLog(String msg, Object[] nameValuePairs, int priority,
                                           Date timestamp) {
        if (logger.get() == null) {
            return;
        }
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments in nameValuePairs must divide by 2.");
        }
        JSONObject logJsonObject = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            for (int i = 0; i < nameValuePairs.length / 2; i++) {
                data.put(nameValuePairs[i * 2].toString(), nameValuePairs[i * 2 + 1]);
            }
            logJsonObject.put("msg", msg);
            logJsonObject.put("data", data);

            storeLog(logJsonObject.toString(), true, priority, timestamp.getTime());
        } catch (JSONException ignored) {
            // fail silently
        }
    }

    private static void storeLog(String logjson, boolean isDiagnostic, int priority, long timestamp) {
        if (logger.get() == null) {
            return;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(logger.get().getContext().getApplicationContext());
        db.getQueryExecutor().execute(() -> {
            db.insertLog(logjson, isDiagnostic, priority, timestamp);
        });
        if (BuildConfig.DEBUG) {
            if (isDiagnostic) {
                Log.println(priority, TAG, logjson.replaceAll("\\\\", ""));
            } else {
                Log.println(priority, TAG, getStatusLogMessageForDisplay(logjson, logger.get().getContext()));
            }
        }
    }

    public static String getStatusLogMessageForDisplay(String logjson, Context context) {
        try {
            JSONObject jsonObject = new JSONObject(logjson);
            JSONArray formatArgsJSONArray = jsonObject.getJSONArray("formatArgs");
            Object[] formatArgs = new Object[formatArgsJSONArray.length()];
            for (int i = 0; i < formatArgsJSONArray.length(); i++) {
                formatArgs[i] = formatArgsJSONArray.get(i);
            }

            int resourceID = context.getResources().getIdentifier(jsonObject.getString("stringResourceName"), null, null);
            if (resourceID == 0) {
                // Failed to convert from resource name to ID. This can happen if a
                // string resource has been renamed since the log entry was created.
                return "";
            }
            return context.getString(resourceID, formatArgs);
        } catch (JSONException e) {
            return "";
        }
    }
}