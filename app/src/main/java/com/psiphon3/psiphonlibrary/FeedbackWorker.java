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

package com.psiphon3.psiphonlibrary;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.RxWorker;
import androidx.work.WorkerParameters;

import com.psiphon3.PsiphonCrashService;
import com.psiphon3.R;
import com.psiphon3.log.LogEntry;
import com.psiphon3.log.LoggingContentProvider;
import com.psiphon3.log.MyLog;

import net.grandcentrix.tray.AppPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Locale;

import ca.psiphon.PsiphonTunnel;
import ca.psiphon.PsiphonTunnel.PsiphonTunnelFeedback;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

/**
 * Feedback worker which securely uploads user submitted feedback to Psiphon Inc.
 *
 * FeedbackWorker.generateInputData(..) should be used to generate the input data for any work requests.
 *
 * Note: if an exception is thrown, then the work request will be marked as failed and will not be
 * rescheduled.
 */
public class FeedbackWorker extends RxWorker {
    // log JSON max size to read from logs DB
    private final static int MAX_LOG_SOURCE_JSON_SIZE_BYTES = 1 << 20; // 1MB

    private final TunnelServiceInteractor tunnelServiceInteractor;
    private final PsiphonTunnelFeedback psiphonTunnelFeedback;
    private final boolean sendDiagnosticInfo;
    private final String email;
    private final String feedbackText;
    private final String surveyResponsesJson;
    private final String feedbackId;
    private final long feedbackSubmitTimeMillis;

    private Thread shutdownHook;

    /**
     * Create the input data for a feedback upload work request.
     *
     * @param sendDiagnosticInfo If true, the user has opted in to including diagnostics with their
     *                           feedback and diagnostics will be included in uploaded feedback.
     *                           Otherwise, diagnostics will be omitted.
     * @param email User email address.
     * @param feedbackText User feedback comment.
     * @param surveyResponsesJson User feedback responses.
     * @return Input data for a work request to FeedbackWorker.
     */
    public static @NonNull Data generateInputData(boolean sendDiagnosticInfo,
                                                  @NonNull String email,
                                                  @NonNull String feedbackText,
                                                  @NonNull String surveyResponsesJson) {
        Data.Builder dataBuilder = new Data.Builder();
        dataBuilder.putBoolean("sendDiagnosticInfo", sendDiagnosticInfo);
        dataBuilder.putString("email", email);
        dataBuilder.putString("feedbackText", feedbackText);
        dataBuilder.putString("surveyResponsesJson", surveyResponsesJson);
        dataBuilder.putLong("submitTimeMillis", new Date().getTime());
        dataBuilder.putString("feedbackId", generateFeedbackId());
        return dataBuilder.build();
    }

    private static String generateFeedbackId() {
        SecureRandom rnd = new SecureRandom();
        byte[] id = new byte[8];
        rnd.nextBytes(id);
        return Utils.byteArrayToHexString(id);
    }

    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    public FeedbackWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);

        tunnelServiceInteractor = new TunnelServiceInteractor(getApplicationContext(), false);

        psiphonTunnelFeedback = new PsiphonTunnelFeedback();

        sendDiagnosticInfo = workerParams.getInputData().getBoolean("sendDiagnosticInfo", false);
        email = workerParams.getInputData().getString("email");
        if (email == null) {
            throw new AssertionError("feedback email null");
        }
        feedbackText = workerParams.getInputData().getString("feedbackText");
        if (feedbackText == null) {
            throw new AssertionError("feedback text null");
        }
        surveyResponsesJson = workerParams.getInputData().getString("surveyResponsesJson");
        if (surveyResponsesJson == null) {
            throw new AssertionError("survey response null");
        }
        feedbackSubmitTimeMillis = workerParams.getInputData().getLong("submitTimeMillis", new Date().getTime());

        // Using the same feedback ID for each upload attempt makes it easier to identify when a
        // feedback has been uploaded more than once. E.g. the upload succeeds but is retried
        // because: the connection with the server is disrupted before the response is received by
        // the client; or WorkManager reschedules the work and disposes of the signal returned by
        // createWork() before it can emit its result.
        feedbackId = workerParams.getInputData().getString("feedbackId");
        if (feedbackId == null) {
            throw new AssertionError("feedback ID null");
        }
    }

    @Override
    public void onStopped() {
        MyLog.i("FeedbackUpload: " + feedbackId + " worker stopped by system");
        super.onStopped();
    }

    /**
     * Upload the feedback. Only one upload is supported at a time and the returned signal must
     * complete or be disposed of before calling this function again.
     * @return A cold signal which will attempt to upload the provided diagnostics JSON once observed.
     * The signal will complete if the upload is successful or emit an error in the event of a failure.
     * Disposing of the signal will interrupt the upload if it is ongoing.
     */
    @NonNull
    public Completable
        startSendFeedback(@NonNull Context context, @NonNull String feedbackConfigJson,
                          @NonNull String diagnosticsJson, @NonNull String uploadPath,
                          @NonNull String clientPlatformPrefix, @NonNull String clientPlatformSuffix) {

        return Completable.create(emitter -> {

            emitter.setCancellable(() -> {
                MyLog.i("FeedbackUpload: " + feedbackId + " disposed");
                psiphonTunnelFeedback.stopSendFeedback();
                // Remove the shutdown hook since the underlying resources have been cleaned up by
                // stopSendFeedback.
                if (this.shutdownHook != null) {
                    boolean removed = Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                    if (!removed) {
                        // Hook was either never registered or already de-registered
                        MyLog.i("FeedbackUpload: shutdown hook not de-registered");
                    }
                    this.shutdownHook = null;
                }
            });

            // Create a shutdown hook which stops the feedback upload operation to ensure that any
            // underlying resources are cleaned up in the event that the JVM is shutdown. This is
            // required to prevent possible data store corruption.
            this.shutdownHook = new Thread() {
                @Override
                public void run() {
                    super.run();
                    psiphonTunnelFeedback.stopSendFeedback();
                    MyLog.i("FeedbackUpload: shutdown hook done");
                }
            };
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);

            psiphonTunnelFeedback.startSendFeedback(
                    context,
                    new PsiphonTunnel.HostFeedbackHandler() {
                        public void sendFeedbackCompleted(java.lang.Exception e) {
                            if (!emitter.isDisposed()) {
                                MyLog.i("FeedbackUpload: " + feedbackId + " completed");
                                if (e != null) {
                                    emitter.onError(e);
                                    return;
                                }
                                // Complete. This is the last callback invoked by PsiphonTunnel.
                                emitter.onComplete();
                            } else {
                                if (e != null) {
                                    MyLog.w("FeedbackUpload: " + feedbackId + " completed with error but emitter disposed: " + e);
                                    return;
                                }
                                MyLog.i("FeedbackUpload: " + feedbackId + " completed but emitter disposed");
                            }
                        }
                    },
                    new PsiphonTunnel.HostLogger() {
                        public void onDiagnosticMessage(String message) {
                            if (!emitter.isDisposed()) {
                                MyLog.i("FeedbackUpload: tunnel diagnostic: " + message);
                            }
                        }
                    },
                    feedbackConfigJson, diagnosticsJson, uploadPath, clientPlatformPrefix,
                    clientPlatformSuffix);
        });
    }

    @NonNull
    @Override
    public Single<Result> createWork() {

        // Guard against the upload being retried indefinitely if it continues to exceed the max
        // execution time limit of 10 minutes.
        if (this.getRunAttemptCount() > 10) {
            MyLog.e("FeedbackUpload: " + feedbackId + " failed, exceeded 10 attempts");
            return Single.just(Result.failure());
        }

        MyLog.i(String.format(Locale.US, "FeedbackUpload: starting feedback upload work " + feedbackId + ", attempt %d", this.getRunAttemptCount()));

        // Note: this method is called on the main thread despite documentation stating that work
        // will be scheduled on a background thread. All work should be done off the main thread in
        // the returned signal to prevent stalling the main thread. The returned signal will be
        // disposed by the system if the work should be cancelled, e.g. the 10 minute time limit
        // elapsed. Since cleanup work is handled in the signal itself there is no need override the
        // `onStopped` method this purpose.

        // Note: this works because FeedbackWorker runs in the same process as the main activity and
        // therefore this `tunnelServiceInteractor` will receive the process-wide broadcast when
        // the VPN Service is started and subsequently bind to the VPN Service. This ensures that
        // all tunnel state changes are propagated through its tunnel state signal.
        tunnelServiceInteractor.onStart(getApplicationContext());

        // Signal which encapsulates the feedback upload operation.
        //
        // The upload will be started once there is network connectivity and VPN is
        // disconnected or connected. If the state changes while an upload is ongoing (e.g.
        // VPN state changes from connected to disconnecting, or the subscription status
        // changes), then the ongoing upload is cancelled and automatically retried by
        // restarting the upload when the required preconditions are met again.
        //
        // Warning: If the state keeps changing before the upload completes, then
        // the upload could be retried indefinitely. In the future it could be desirable to
        // restrict the number of retries in this signal, or propagate the retry number to
        // the feedback upload provider and allow the implementer to decide when to stop
        // retrying.
        return tunnelServiceInteractor
                .tunnelStateFlowable()
                .observeOn(Schedulers.io())
                .distinctUntilChanged() // Note: called upstream
                .switchMap(tunnelState -> {

                    // Note: when a new tunnel state is emitted from upstream and a previous inner
                    // signal was returned from this block, the previously returned signal will be
                    // disposed. This is the functionality that `switchMap` provides. In our case,
                    // the inner signal represents a feedback upload operation that will be
                    // cancelled if the tunnel state changes, i.e. a new value is emitted from
                    // upstream.

                    if (tunnelState.isStopped() || (tunnelState.isRunning() && tunnelState.connectionData().isConnected())) {
                        // Send feedback.

                        MyLog.i("FeedbackUpload: uploading feedback " + feedbackId);

                        Context context = getApplicationContext();
                        String feedbackJsonString = createFeedbackData(
                                context,
                                sendDiagnosticInfo,
                                email,
                                feedbackText,
                                surveyResponsesJson,
                                feedbackId,
                                // Only include diagnostics logged before the feedback was submitted
                                feedbackSubmitTimeMillis);

                        // Build a temporary tunnel config to use
                        TunnelManager.Config tunnelManagerConfig = new TunnelManager.Config();
                        final AppPreferences multiProcessPreferences = new AppPreferences(context);
                        tunnelManagerConfig.disableTimeouts = multiProcessPreferences.getBoolean(
                                context.getString(R.string.disableTimeoutsPreference), false);

                        String tunnelCoreConfig = TunnelManager.buildTunnelCoreConfig(
                                context,
                                tunnelManagerConfig,
                                tunnelState.isStopped(),
                                null);
                        if (tunnelCoreConfig == null) {
                            return Flowable.error(new Exception("tunnel-core config null"));
                        }

                        // Note: It is possible that the upload could succeed at the same moment one
                        // of the trigger signals (VPN state change, etc.) changes or the work
                        // request is rescheduled by WorkManager. Then there would be a race between
                        // this signal emitting a value and it being disposed of, which would result
                        // in the value being ignored. If this happens, the feedback upload will be
                        // attempted again even though it already succeeded. The same feedback ID is
                        // used for all upload attempts, which provides visibility into these
                        // occurrences and allows for mitigation.
                        return startSendFeedback(context, tunnelCoreConfig, feedbackJsonString,
                                "", "", Utils.getClientPlatformSuffix())
                                .andThen(Flowable.just(Result.success()));
                    }

                    MyLog.i("FeedbackUpload: " + feedbackId + " waiting for tunnel to be disconnected or connected");
                    return Flowable.empty();
                })
                .firstOrError()
                .doOnSuccess(__ -> MyLog.i("FeedbackUpload: " + feedbackId + " upload succeeded"))
                .onErrorReturn(error -> {
                    MyLog.w("FeedbackUpload: " + feedbackId + " upload failed: " + error.getMessage());
                    return Result.failure();
                });
    }

    private static @NonNull String createFeedbackData(Context context,
                              boolean shouldIncludeDiagnostics,
                              String email,
                              String feedbackText,
                              String surveyResponsesJson,
                              String feedbackId,
                              long beforeTimeMillis) throws JSONException {
        // Top level json object
        JSONObject feedbackJsonObject = new JSONObject();

        // Add metadata
        JSONObject metadata = new JSONObject();

        metadata.put("platform", "android");
        metadata.put("version", 4);
        metadata.put("id", feedbackId);

        feedbackJsonObject.put("Metadata", metadata);


        // Add feedback text and / or surveyResponses
        if (feedbackText.length() > 0 || surveyResponsesJson.length() > 0) {
            JSONObject feedbackInfo = new JSONObject();
            feedbackInfo.put("email", email);

            JSONObject feedbackMessageInfo = new JSONObject();
            feedbackMessageInfo.put("text", feedbackText);
            feedbackInfo.put("Message", feedbackMessageInfo);

            JSONObject feedbackSurveyInfo = new JSONObject();
            feedbackSurveyInfo.put("json", surveyResponsesJson);
            feedbackInfo.put("Survey", feedbackSurveyInfo);

            feedbackJsonObject.put("Feedback", feedbackInfo);
        }

        if (shouldIncludeDiagnostics) {
            JSONObject diagnosticInfo = new JSONObject();

            JSONObject sysInfo = new JSONObject();
            sysInfo.put("isRooted", Utils.isRooted());
            sysInfo.put("isPlayStoreBuild", EmbeddedValues.IS_PLAY_STORE_BUILD);
            sysInfo.put("language", Locale.getDefault().getLanguage());
            sysInfo.put("networkTypeName", Utils.getNetworkTypeName(context));

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

            sysInfo.put("Build", sysInfo_Build);

            JSONObject sysInfo_psiphonEmbeddedValues = new JSONObject();
            sysInfo_psiphonEmbeddedValues.put("PROPAGATION_CHANNEL_ID", EmbeddedValues.PROPAGATION_CHANNEL_ID);
            sysInfo_psiphonEmbeddedValues.put("SPONSOR_ID", EmbeddedValues.SPONSOR_ID);
            sysInfo_psiphonEmbeddedValues.put("CLIENT_VERSION", EmbeddedValues.CLIENT_VERSION);

            sysInfo.put("PsiphonInfo", sysInfo_psiphonEmbeddedValues);

            diagnosticInfo.put("SystemInformation", sysInfo);


            JSONArray diagnosticHistory = new JSONArray();
            JSONArray statusHistory = new JSONArray();

            int totalBytesRead = 0;

            // Read up to MAX_LOG_SOURCE_JSON_SIZE_BYTES from the logs database
            // and add to diagnostic / status info
            Uri uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                    .appendPath("all")
                    .appendPath(String.valueOf(beforeTimeMillis))
                    .build();
            ContentResolver contentResolver = context.getContentResolver();
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                while (totalBytesRead < MAX_LOG_SOURCE_JSON_SIZE_BYTES && cursor.moveToNext()) {
                    final LogEntry logEntry = LoggingContentProvider.convertRows(cursor);
                    totalBytesRead += logEntry.getLogJson().length();

                    JSONObject entry = new JSONObject();
                    entry.put("timestamp!!timestamp", Utils.getISO8601String(new Date(logEntry.getTimestamp())));

                    JSONObject logJsonObject = new JSONObject(logEntry.getLogJson());

                    if (logEntry.isDiagnostic()) {
                        Object msg = logJsonObject.opt("msg");
                        Object data = logJsonObject.opt("data");
                        entry.put("msg", msg == null ? JSONObject.NULL : msg);
                        entry.put("data", data == null ? JSONObject.NULL : data);

                        diagnosticHistory.put(entry);
                    } else {
                        int sensitivity = logJsonObject.optInt("sensitivity", 0);
                        if (sensitivity == MyLog.Sensitivity.SENSITIVE_LOG) {
                            // Skip sensitive logs
                            continue;
                        }
                        int resourceID = context.getResources().getIdentifier(logJsonObject
                                .getString("stringResourceName"), null, null);
                        entry.put("id", resourceID == 0 ?
                                "" : context.getResources().getResourceEntryName(resourceID));

                        entry.put("priority", logEntry.getPriority());
                        entry.put("formatArgs", JSONObject.NULL);
                        entry.put("throwable", JSONObject.NULL);

                        if (sensitivity != MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS) {
                            JSONArray formatArgsJsonArray = logJsonObject.optJSONArray("formatArgs");
                            if (formatArgsJsonArray != null && formatArgsJsonArray.length() > 0) {
                                entry.put("formatArgs", formatArgsJsonArray);
                            }
                        }

                        statusHistory.put(entry);
                    }
                }
                diagnosticInfo.put("DiagnosticHistory", diagnosticHistory);
                diagnosticInfo.put("StatusHistory", statusHistory);
            }
            // Check if we have native crash data to include
            File crashReportFile = new File(PsiphonCrashService.getFinalCrashReportPath(context));
            if (crashReportFile.exists()) {
                JSONArray crashHistory = new JSONArray();
                try {
                    BufferedReader in;
                    String str;
                    in = new BufferedReader(new FileReader(crashReportFile));
                    while ((str = in.readLine()) != null) {
                        crashHistory.put(str);
                    }
                    in.close();

                } catch (IOException ignored) {
                }

                crashReportFile.delete();
                if (crashHistory.length() > 0) {
                    diagnosticInfo.put("CrashHistory", crashHistory);
                }
            }
            feedbackJsonObject.put("DiagnosticInfo", diagnosticInfo);
        }

        return feedbackJsonObject.toString();
    }
}
