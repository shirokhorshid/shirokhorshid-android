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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.psiphon3.psiphonlibrary.Utils.MyLog;

import com.psiphon3.R;

public class Diagnostics
{
    /**
     * Create the diagnostic info package.
     * @param context
     * @param sendDiagnosticInfo
     * @param email
     * @param feedbackText
     * @param surveyResponsesJson
     * @return A String containing the diagnostic info, or `null` if there is
     *         an error.
     */
    static public String create(
                            Context context,
                            boolean sendDiagnosticInfo,
                            String email,
                            String feedbackText,
                            String surveyResponsesJson)
    {
        // Our attachment is JSON, which is then encrypted, and the
        // encryption elements stored in JSON.

        String diagnosticJSON;

        try
        {
            /*
             * Metadata
             */

            JSONObject metadata = new JSONObject();

            metadata.put("platform", "android");
            metadata.put("version", 4);

            SecureRandom rnd = new SecureRandom();
            byte[] id = new byte[8];
            rnd.nextBytes(id);
            metadata.put("id", Utils.byteArrayToHexString(id));

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

            for (StatusList.DiagnosticEntry item : StatusList.cloneDiagnosticHistory())
            {
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

            for (StatusList.StatusEntry internalEntry : StatusList.cloneStatusHistory())
            {
                // Don't send any sensitive logs or debug logs
                if (internalEntry.sensitivity() == MyLog.Sensitivity.SENSITIVE_LOG
                    || internalEntry.priority() == Log.DEBUG)
                {
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
                    && internalEntry.sensitivity() != MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS)
                {
                    JSONArray formatArgs = new JSONArray();
                    for (Object o : internalEntry.formatArgs())
                    {
                        formatArgs.put(o);
                    }
                    statusEntry.put("formatArgs", formatArgs);
                }

                if (internalEntry.throwable() != null)
                {
                    JSONObject throwable = new JSONObject();

                    throwable.put("message", internalEntry.throwable().toString());

                    JSONArray stack = new JSONArray();
                    for (StackTraceElement element : internalEntry.throwable().getStackTrace())
                    {
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

            if (sendDiagnosticInfo)
            {
                diagnosticObject.put("DiagnosticInfo", diagnosticInfo);
            }

            if (feedbackText.length() > 0 || surveyResponsesJson.length() > 0)
            {
                JSONObject feedbackInfo = new JSONObject();
                feedbackInfo.put("email", email);

                JSONObject feedbackMessageInfo = new JSONObject();
                feedbackMessageInfo.put("text", feedbackText);
                feedbackInfo.put("Message", feedbackMessageInfo);

                JSONObject feedbackSurveyInfo = new JSONObject();
                feedbackSurveyInfo.put("json", surveyResponsesJson);
                feedbackInfo.put("Survey", feedbackSurveyInfo);

                diagnosticObject.put("Feedback",  feedbackInfo);
            }

            diagnosticJSON = diagnosticObject.toString();
        }
        catch (JSONException e)
        {
            throw new RuntimeException(e);
        }

        assert(diagnosticJSON != null);

        // Encrypt the file contents
        Utils.RSAEncryptOutput rsaEncryptOutput = null;
        boolean encryptedOkay = false;
        try
        {
            rsaEncryptOutput = Utils.encryptWithRSA(
                    diagnosticJSON.getBytes("UTF-8"),
                    EmbeddedValues.FEEDBACK_ENCRYPTION_PUBLIC_KEY);
            encryptedOkay = true;
        }
        catch (GeneralSecurityException | UnsupportedEncodingException e)
        {
            MyLog.e(R.string.Diagnostics_EncryptedFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
        }

        String result = null;
        if (encryptedOkay)
        {
            StringBuilder encryptedContent = new StringBuilder();
            encryptedContent.append("{\n");
            encryptedContent.append("  \"contentCiphertext\": \"").append(Utils.Base64.encode(rsaEncryptOutput.mContentCiphertext)).append("\",\n");
            encryptedContent.append("  \"iv\": \"").append(Utils.Base64.encode(rsaEncryptOutput.mIv)).append("\",\n");
            encryptedContent.append("  \"wrappedEncryptionKey\": \"").append(Utils.Base64.encode(rsaEncryptOutput.mWrappedEncryptionKey)).append("\",\n");
            encryptedContent.append("  \"contentMac\": \"").append(Utils.Base64.encode(rsaEncryptOutput.mContentMac)).append("\",\n");
            encryptedContent.append("  \"wrappedMacKey\": \"").append(Utils.Base64.encode(rsaEncryptOutput.mWrappedMacKey)).append("\"\n");
            encryptedContent.append("}");

            result = encryptedContent.toString();
        }

        return result;
    }

    /**
     * Create the diagnostic data package and upload it.
     * @param context
     * @param sendDiagnosticInfo
     * @param email
     * @param feedbackText
     * @param surveyResponsesJson
     * @return
     */
    static public void send(
            Context context,
            boolean sendDiagnosticInfo,
            String email,
            String feedbackText,
            String surveyResponsesJson)
    {
        // Fire-and-forget thread.
        class FeedbackRequestThread extends Thread
        {
            private final Context mContext;
            private final boolean mSendDiagnosticInfo;
            private final String mEmail;
            private final String mFeedbackText;
            private final String mSurveyResponsesJson;

            FeedbackRequestThread(
                    Context context,
                    boolean sendDiagnosticInfo,
                    String email,
                    String feedbackText,
                    String surveyResponsesJson)
            {
                mContext = context;
                mSendDiagnosticInfo = sendDiagnosticInfo;
                mEmail = email;
                mFeedbackText = feedbackText;
                mSurveyResponsesJson = surveyResponsesJson;
            }

            @Override
            public void run()
            {
                String diagnosticData = Diagnostics.create(
                        mContext,
                        mSendDiagnosticInfo,
                        mEmail,
                        mFeedbackText,
                        mSurveyResponsesJson);

                if (diagnosticData == null)
                {
                    return;
                }

                byte[] diagnosticDataBytes;
                try
                {
                    diagnosticDataBytes = diagnosticData.getBytes("UTF-8");
                    diagnosticData = null;
                }
                catch (UnsupportedEncodingException e)
                {
                    MyLog.d("diagnosticData.getBytes failed", e);
                    // unrecoverable
                    return;
                }

                // Retry uploading data up to 5 times
                for (int i = 0; i < 5; i++)
                {
                    if (doFeedbackUpload(diagnosticDataBytes))
                    {
                        break;
                    }

                    // The upload request failed, so sleep and try again.
                    try
                    {
                        MyLog.g("Diagnostic data send fail; sleeping");
                        Thread.sleep(5 * 60 * 1000);
                    }
                    catch (InterruptedException e)
                    {
                        // Bail out of this thread if sleep is interrupted.
                        return;
                    }
                }
            }
        }

        FeedbackRequestThread thread = new FeedbackRequestThread(
                                            context,
                                            sendDiagnosticInfo,
                                            email,
                                            feedbackText,
                                            surveyResponsesJson);
        thread.start();
    }
    
    public final static int FEEDBACK_UPLOAD_TIMEOUT_MS = 30000;
    
    static private boolean doFeedbackUpload(byte[] feedbackData)
    {
        // NOTE: Won't succeed while VpnService routing is enabled but tunnel
        // is not connected.
        // TODO: In that situation, use the tunnel-core UrlProxy/direct mode.

        SecureRandom rnd = new SecureRandom();
        byte[] uploadId = new byte[8];
        rnd.nextBytes(uploadId);

        StringBuilder url = new StringBuilder();
        url.append("https://");
        url.append(EmbeddedValues.FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER);
        url.append(EmbeddedValues.FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_PATH);
        url.append(Utils.byteArrayToHexString(uploadId));

        HttpsURLConnection httpsConn = null;
        boolean success = false;
        try
        {
            httpsConn = (HttpsURLConnection) new URL(url.toString()).openConnection();

            // URLConnection timeouts are insufficient may be unreliable, so run a timeout
            // thread to ensure HTTPS connection is terminated after 30 seconds if it
            // has not already completed.
            // E.g., http://stackoverflow.com/questions/11329277/why-timeout-value-is-not-respected-by-android-httpurlconnection
            final HttpsURLConnection finalHttpsConn = httpsConn;
            new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        Thread.sleep(FEEDBACK_UPLOAD_TIMEOUT_MS);
                    }
                    catch (InterruptedException e)
                    {
                    }
                    finalHttpsConn.disconnect();
                }
            }).start();
            
            httpsConn.setDoOutput(true);
            httpsConn.setRequestMethod("PUT");
            // Note: assumes this is only a single header
            String[] headerPieces = EmbeddedValues.FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER_HEADERS.split(": ");
            httpsConn.setRequestProperty(headerPieces[0], headerPieces[1]);
            httpsConn.setFixedLengthStreamingMode(feedbackData.length);

            httpsConn.connect();
            httpsConn.getOutputStream().write(feedbackData);
            
            // getInputStream() checks response status code
            httpsConn.getInputStream();
            
            success = true;
        } catch (IOException e)
        {
            MyLog.g(String.format("Diagnostic doFeedbackUpload failed: %s", e.getMessage()));
        }
        finally
        {
            if (httpsConn != null)
            {
                httpsConn.disconnect();
            }
        }

        return success;
    }
}
