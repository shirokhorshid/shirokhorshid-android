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

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class LogsMaintenanceWorker extends Worker {
    static String TAG_WORK = LogsMaintenanceWorker.class.getSimpleName();
    static int REPEAT_INTERVAL_HOURS = 6;
    static long DAY_IN_MS = 1000 * 60 * 60 * 24;
    static int DELETE_LOGS_AFTER_DAYS = 2;


    static public void schedule(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                LogsMaintenanceWorker.class, REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(TAG_WORK,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest);
    }

    public LogsMaintenanceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        LoggingRoomDatabase db = LoggingRoomDatabase.getDatabase(getApplicationContext());
        db.getQueryExecutor().execute(() ->
                db.deleteLogEntriesBefore(new Date().getTime() - DELETE_LOGS_AFTER_DAYS * DAY_IN_MS));
        return Result.success();
    }
}
