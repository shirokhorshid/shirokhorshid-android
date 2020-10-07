package com.psiphon3.psiphonlibrary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.RxWorker;
import androidx.work.WorkerParameters;

import ca.psiphon.PsiphonTunnel;
import ca.psiphon.PsiphonTunnel.PsiphonTunnelFeedback;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import java.util.Date;

import net.grandcentrix.tray.AppPreferences;

/**
 * Feedback worker which securely uploads user submitted feedback to Psiphon Inc.
 *
 * FeedbackWorker.generateInputData(..) should be used to generate the input data for any work requests.
 *
 * Note: if an exception is thrown, then the work request will be marked as failed and will not be
 * rescheduled.
 */
public class FeedbackWorker extends RxWorker {
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
        dataBuilder.putString("feedbackId", Diagnostics.generateFeedbackId());
        return dataBuilder.build();
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
        MyLog.g("FeedbackUpload: worker stopped by system");
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
                MyLog.g("FeedbackUpload: disposed");
                psiphonTunnelFeedback.stopSendFeedback();
                // Remove the shutdown hook since the underlying resources have been cleaned up by
                // stopSendFeedback.
                if (this.shutdownHook != null) {
                    boolean removed = Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                    if (!removed) {
                        // Hook was either never registered or already de-registered
                        MyLog.g("FeedbackUpload: shutdown hook not de-registered");
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
                    MyLog.g("FeedbackUpload: shutdown hook done");
                }
            };
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);

            psiphonTunnelFeedback.startSendFeedback(
                    context,
                    new PsiphonTunnel.HostFeedbackHandler() {
                        public void sendFeedbackCompleted(java.lang.Exception e) {
                            if (!emitter.isDisposed()) {
                                MyLog.g("FeedbackUpload: completed");
                                if (e != null) {
                                    emitter.onError(e);
                                    return;
                                }
                                // Complete. This is the last callback invoked by PsiphonTunnel.
                                emitter.onComplete();
                            } else {
                                if (e != null) {
                                    MyLog.g("FeedbackUpload: completed with error but emitter disposed: "
                                            + e.getMessage());
                                    return;
                                }
                                MyLog.g("FeedbackUpload: completed but emitter disposed");
                            }
                        }
                    },
                    new PsiphonTunnel.HostLogger() {
                        public void onDiagnosticMessage(String message) {
                            if (!emitter.isDisposed()) {
                                MyLog.g("FeedbackUpload: " + message);
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
            MyLog.g("FeedbackUpload: failed, exceeded 10 attempts");
            return Single.just(Result.failure());
        }

        MyLog.g(String.format("FeedbackUpload: starting feedback upload work, attempt %d", this.getRunAttemptCount()));

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

                        MyLog.g("FeedbackUpload: uploading feedback");

                        Context context = getApplicationContext();

                        String diagnosticData = Diagnostics.create(
                                context,
                                sendDiagnosticInfo,
                                email,
                                feedbackText,
                                surveyResponsesJson,
                                feedbackId,
                                // Only include diagnostics logged before the feedback was submitted
                                new Date(feedbackSubmitTimeMillis));

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
                        return startSendFeedback(context, tunnelCoreConfig, diagnosticData,
                                "", "", Utils.getClientPlatformSuffix())
                                .andThen(Flowable.just(Result.success()));
                    }

                    MyLog.g("FeedbackUpload: waiting for tunnel to be disconnected or connected");
                    return Flowable.empty();
                })
                .firstOrError()
                .doOnSuccess(__ -> MyLog.g("FeedbackUpload: upload succeeded"))
                .onErrorReturn(error -> {
                    MyLog.g("FeedbackUpload: upload failed: " + error.getMessage());
                    return Result.failure();
                });
    }
}
