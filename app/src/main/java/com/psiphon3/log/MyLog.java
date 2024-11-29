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

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.StringRes;

import com.psiphon3.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyLog {
    private static final String TAG = MyLog.class.getSimpleName();
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static WeakReference<Context> contextRef;
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final Object initLock = new Object();

    // Retry config
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {100, 200, 500}; // backoff delays in ms

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

    // Initialize early in the application lifecycle with the application context
    public static void init(Context context) {
        // If already initialized and we have a valid context, do nothing
        if (isInitialized.get() && contextRef != null && contextRef.get() != null) {
            return;
        }
        // Only initialize if we have a valid context
        if (context != null) {
            synchronized (initLock) {
                // Double check the initialization state
                if (!isInitialized.get() || contextRef == null || contextRef.get() == null) {
                    contextRef = new WeakReference<>(context.getApplicationContext());
                    isInitialized.set(true);
                }
            }
        }
    }

    // Shuts down the logger, cancelling any pending retries and preventing further logging
    // Should be called when the application is shutting down
    public static void shutdown() {
        synchronized (initLock) {
            if (isShutdown.getAndSet(true)) {
                return;  // Already shut down
            }

            // Clear context ref and initialized state
            contextRef = null;
            isInitialized.set(false);

            // Shutdown executor service immediately to cancel pending retries
            executorService.shutdownNow();
        }
    }

    // Status log with priority Log.VERBOSE
    // Displayed to the user and included in feedback if the user consents
    public static void v(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.VERBOSE, new Date());
    }

    // Diagnostic log with priority Log.VERBOSE
    // Internal only and included in feedback if the user consents
    public static void v(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.VERBOSE, new Date());
    }

    // Status log with priority Log.INFO
    // Displayed to the user and included in feedback if the user consents
    public static void i(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.INFO, new Date());
    }

    // Diagnostic log with priority Log.INFO that also takes timestamp
    // Temporarily used for tunnel core notices logging in the TunnelManager.onDiagnosticMessage
    // for the purpose of getting a more accurate timestamp.
    public static void i(Date timestamp, String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.INFO, timestamp);
    }

    // Diagnostic log with priority Log.INFO
    // Internal only and included in feedback if the user consents
    public static void i(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.INFO, new Date());
    }

    // Status log with priority Log.WARN
    // Displayed to the user and included in feedback if the user consents
    public static void w(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.WARN, new Date());
    }

    // Diagnostic log with priority Log.WARN
    // Internal only and included in feedback if the user consents
    public static void w(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.WARN, new Date());
    }

    // Status log with priority Log.ERROR
    // Displayed to the user and included in feedback if the user consents
    public static void e(@StringRes int resId, int sensitivity, Object... formatArgs) {
        storeStatusLog(resId, formatArgs, sensitivity, Log.ERROR, new Date());
    }

    // Diagnostic log with priority Log.ERROR
    // Internal only and included in feedback if the user consents
    public static void e(String msg, Object... nameValuePairs) {
        storeDiagnosticLog(msg, nameValuePairs, Log.ERROR, new Date());
    }

    private static void storeStatusLog(@StringRes int resId, Object[] formatArgs, int sensitivity, int priority, Date timestamp) {
        // Get context and check initialization
        final Context context;
        synchronized (initLock) {
            context = (contextRef != null) ? contextRef.get() : null;
            if (!isInitialized.get() || context == null) {
                throw new IllegalStateException("MyLog not properly initialized before logging");
            }
        }

        try {
            JSONObject logJsonObject = new JSONObject();

            String stringResourceName = context.getResources().getResourceName(resId);
            logJsonObject.put("stringResourceName", stringResourceName);

            logJsonObject.put("sensitivity", sensitivity);

            JSONArray formatArgsJsonArray = new JSONArray();
            for (Object arg : formatArgs) {
                formatArgsJsonArray.put(arg);
            }

            logJsonObject.put("formatArgs", formatArgsJsonArray.length() == 0 ?
                    JSONObject.NULL : formatArgsJsonArray);

            storeLog(logJsonObject.toString(), false, priority, timestamp.getTime());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON log object: " + e.getMessage());
        }
    }

    private static void storeDiagnosticLog(String msg, Object[] nameValuePairs, int priority, Date timestamp) {
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
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create diagnostic JSON log object: " + e.getMessage());
        }
    }

    private static void storeLog(String logjson, boolean isDiagnostic, int priority, long timestamp) {
        // Capture context and check initialization state
        final Context context;
        synchronized (initLock) {
            context = (contextRef != null) ? contextRef.get() : null;

            if (!isInitialized.get() || context == null) {
                throw new IllegalStateException(String.format(Locale.US,
                        "MyLog not properly initialized. Context: %s, Initialized: %b",
                        context == null ? "null" : "valid",
                        isInitialized.get()));
            }
        }

        ContentValues values = new ContentValues();
        values.put("logjson", logjson);
        values.put("is_diagnostic", isDiagnostic);
        values.put("priority", priority);
        values.put("timestamp", timestamp);

        executorService.execute(() -> insertWithRetry(context, LoggingContentProvider.CONTENT_URI, values, priority, 0));

        if (BuildConfig.DEBUG) {
            if (isDiagnostic) {
                Log.println(priority, TAG, logjson.replaceAll("\\\\", ""));
            } else {
                Log.println(priority, TAG, getStatusLogMessageForDisplay(logjson, context));
            }
        }
    }

    private static void insertWithRetry(Context context, Uri uri, ContentValues values, int priority, int attempt) {
        try {
            // Will return uri on success or throw on failure
            Uri result = context.getContentResolver().insert(uri, values);

            if (result == null) {
                throw new IllegalStateException("Insert returned null result");
            }
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, String.format(Locale.US, "Insert failed (attempt %d): %s",
                    attempt + 1, e.getMessage()));

            if (attempt < MAX_RETRIES) {
                scheduleRetry(context, uri, values, priority, attempt + 1);
                return;
            }

            // No more retries left, ensure ERROR logs still get to logcat
            if (priority >= Log.ERROR) {
                Log.e(TAG, values.getAsString("logjson"));
            }
        }
    }

    private static void scheduleRetry(Context context, Uri uri, ContentValues values, int priority, int nextAttempt) {
        long delay = RETRY_DELAYS_MS[nextAttempt - 1];

        executorService.execute(() -> {
            try {
                Thread.sleep(delay);
                insertWithRetry(context, uri, values, priority, nextAttempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // If interrupted, make sure ERROR logs still get to logcat
                if (priority >= Log.ERROR) {
                    Log.e(TAG, values.getAsString("logjson"));
                }
            }
        });
    }

    public static String getStatusLogMessageForDisplay(String logjson, Context context) {
        try {
            JSONObject jsonObject = new JSONObject(logjson);
            JSONArray formatArgsJSONArray = jsonObject.optJSONArray("formatArgs");
            Object[] formatArgs = null;
            if (formatArgsJSONArray != null) {
                formatArgs = new Object[formatArgsJSONArray.length()];
                for (int i = 0; i < formatArgsJSONArray.length(); i++) {
                    formatArgs[i] = formatArgsJSONArray.get(i);
                }
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