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

package com.psiphon3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.psiphon3.log.MyLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import ru.ivanarh.jndcrash.NDCrashService;

public class PsiphonCrashService extends NDCrashService {
    private static final String NOTIFICATION_NATIVE_CRASH_CHANNEL_ID = "notificationNativeCrashChannelId";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    NOTIFICATION_NATIVE_CRASH_CHANNEL_ID, getText(R.string.psiphon_native_crash_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.createNotificationChannel(notificationChannel);
        }

        // Also check if an older crash report file exists when starting the service.
        checkNotify();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }


    // Shows 'Psiphon crashed' notification if crash report file exists;
    // clicking the notification should open the feedback activity.
    private void checkNotify() {
        File finalReportFile = new File(getFinalCrashReportPath(this));
        if (finalReportFile.exists()) {
            Intent intent = new Intent(this, FeedbackActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_NATIVE_CRASH_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_psiphon_alert_notification)
                    .setContentTitle(getString(R.string.psiphon_native_crash_notification_title))
                    .setContentText(getString(R.string.psiphon_native_crash_notification_msg))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.psiphon_native_crash_notification_msg_long)))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

            notificationManager.notify(R.id.notification_id_native_crash_report_available, notification);
        }
    }

    public static String getStdRedirectPath(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/stderr.tmp";
    }

    public static String getTempCrashReportPath(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/crashreport.tmp";
    }

    public static String getFinalCrashReportPath(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/crash.txt";
    }

    @Override
    public void onCrash(String reportPath) {
        super.onCrash(reportPath);

        MyLog.e("PsiphonCrashService: received new native crash report.");

        // Rename temporary crash report file
        File tmpCrashReportFile = new File(getTempCrashReportPath(this));
        File finalReportFile = new File(getFinalCrashReportPath(this));
        if (tmpCrashReportFile.exists()) {
            tmpCrashReportFile.renameTo(finalReportFile);
        }

        // Append contents of the sderr redirect file to the final report.
        File stdErrFile = new File(getStdRedirectPath(this));
        if (stdErrFile.exists() && finalReportFile.exists()) {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(finalReportFile, true));
                BufferedReader in = new BufferedReader(new FileReader(stdErrFile));
                String str;

                out.write("=================================================================\n");
                out.write("                              STDERR                             \n");
                out.write("=================================================================\n");

                while ((str = in.readLine()) != null) {
                    out.write(str);
                    out.newLine();
                }
                out.flush();
                out.close();

                in.close();

                stdErrFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Show the 'Psiphon crashed' notification if final crash report has been created successfully.
        checkNotify();
    }
}
