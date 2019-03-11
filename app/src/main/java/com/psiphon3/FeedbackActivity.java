/*
 * Copyright (c) 2012, Psiphon Inc.
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

package com.psiphon3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

import com.psiphon3.psiphonlibrary.LocalizedActivities;
import com.psiphon3.psiphonlibrary.MainBase;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.MailTo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.psiphon3.psiphonlibrary.Diagnostics;
import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

public class FeedbackActivity extends LocalizedActivities.Activity
{

    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState)
    {
        final Activity activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feedback);

        webView = (WebView)findViewById(R.id.feedbackWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                final String feedbackUrl = "feedback?";

                if (url.startsWith("mailto:"))
                {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("message/rfc822");
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[] {MailTo.parse(url).getTo()});

                    try
                    {
                        startActivity(intent);
                    }
                    catch (ActivityNotFoundException e)
                    {
                        // Do nothing
                    }

                    return true;
                }
                else if (url.contains(feedbackUrl))
                {
                    if (submitFeedback(url.substring(
                            url.indexOf(feedbackUrl) + feedbackUrl.length())))
                    {
                        Toast.makeText(activity,
                                getString(R.string.FeedbackActivity_Success),
                                Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                    else
                    {
                        Toast.makeText(activity,
                                getString(R.string.FeedbackActivity_Failure),
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                // Else... Open ordinary URLs (like our download page, FAQ, etc.)
                // in a standard browser. The feedback WebView is not useful
                // for normal webpages (and doesn't seem to support download).
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                try
                {
                    startActivity(browserIntent);
                }
                catch (ActivityNotFoundException e)
                {
                    // Do nothing
                }

                return true;
            }

            private boolean submitFeedback(String urlParameters)
            {
                final String formDataParameterName = "formdata=";
                if (!urlParameters.startsWith(formDataParameterName))
                {
                    MyLog.w(R.string.FeedbackActivity_InvalidURLParameters, MyLog.Sensitivity.NOT_SENSITIVE);
                    return false;
                }

                String email, feedbackText, surveyResponsesJson;
                boolean sendDiagnosticInfo;
                try
                {
                    String formDataJson = URLDecoder.decode(urlParameters.substring(formDataParameterName.length()), "utf-8");

                    JSONObject jsonObj = new JSONObject(formDataJson);
                    feedbackText = jsonObj.getString("feedback");
                    email = jsonObj.getString("email");
                    surveyResponsesJson = jsonObj.getString("responses");
                    sendDiagnosticInfo = jsonObj.getBoolean("sendDiagnosticInfo");
                }
                catch (UnsupportedEncodingException e)
                {
                    MyLog.w(R.string.FeedbackActivity_SubmitFeedbackFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
                    return false;
                }
                catch (JSONException e)
                {
                    MyLog.w(R.string.FeedbackActivity_SubmitFeedbackFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
                    return false;
                }

                Diagnostics.send(
                    activity,
                    sendDiagnosticInfo,
                    email,
                    feedbackText,
                    surveyResponsesJson);

                return true;
            }
        });

        // Load the feedback page
        String html = getHTMLContent();
        // Get the current locale
        String language = Locale.getDefault().getLanguage();

        StringBuilder argsBuilder = new StringBuilder();
        argsBuilder.append("{ ");
        // We are avoiding any possible conflict with the Play Store policy that apps cannot automatically self-upgrade:
        // "An app downloaded from Google Play may not modify, replace or update its own APK binary code using any
        // method other than Google Play's update mechanism."
        // (https://play.google.com/about/developer-content-policy.html)
        // These links, if followed, can be used to side-load upgrade the app, bypassing the Play Store upgrade
        // mechanism.  While it is arguable that this is in conflict with the Play Store policy, we are removing
        // these links to be cautious and to avoid any possible disruptions.
        if (EmbeddedValues.hasEverBeenSideLoaded(this))
        {
            argsBuilder.append("\"newVersionURL\":\"").append(EmbeddedValues.GET_NEW_VERSION_URL).append("\", ");
            argsBuilder.append("\"newVersionEmail\": \"").append(EmbeddedValues.GET_NEW_VERSION_EMAIL).append("\", ");
        }
        argsBuilder.append("\"faqURL\": \"").append(EmbeddedValues.FAQ_URL).append("\", ");
        argsBuilder.append("\"dataCollectionInfoURL\": \"").append(EmbeddedValues.DATA_COLLECTION_INFO_URL).append("\" }");
        String args = null;
        try {
            args = URLEncoder.encode(argsBuilder.toString(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            assert(false);
        }

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("file:///");
        if (args != null)
        {
            urlBuilder.append("?").append(args);
        }
        urlBuilder.append("#").append(language);

        webView.loadDataWithBaseURL(urlBuilder.toString(), html, "text/html", "utf-8", null);
    }

    private String getHTMLContent()
    {
        String html = "";

        try
        {
            InputStream stream = getAssets().open("feedback.html");
            html = streamToString(stream);
        }
        catch (IOException e)
        {
            MyLog.w(R.string.FeedbackActivity_GetHTMLContentFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);

            // Render the default text
            html = "<body>" + getString(R.string.FeedbackActivity_DefaultText) + "</body>";
        }

        return html;
    }

    private String streamToString(InputStream stream) throws IOException
    {
        try
        {
            Reader reader = new BufferedReader(new InputStreamReader(stream));
            Writer writer = new StringWriter();

            int n;
            char[] buffer = new char[2048];
            while ((n = reader.read(buffer)) != -1)
            {
                writer.write(buffer, 0, n);
            }
            return writer.toString();
        }
        finally
        {
            stream.close();
        }
    }
}
