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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.paging.DataSource;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.Executors;

import io.reactivex.Flowable;

@Database(entities = {LogEntry.class,}, version = 3, exportSchema = false)
public abstract class LoggingRoomDatabase extends RoomDatabase {
    private static volatile LoggingRoomDatabase INSTANCE;

    public static LoggingRoomDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (LoggingRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            LoggingRoomDatabase.class, "loggingprovider.db")
                            // Here we are migrating from plain SQLiteOpenHelper to Room; we are not
                            // providing migration strategy, because in the previous version the logs
                            // table is fully truncated every time the app starts fresh.
                            .fallbackToDestructiveMigration()
                            .enableMultiInstanceInvalidation()
                            .setQueryExecutor(Executors.newFixedThreadPool(4))
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public void insertLog(String logjson, boolean isDiagnostic, int priority, long timestamp) {
        // Performance optimization
        if (isDiagnostic) {
            // We DO NOT want to trigger the log view update in case we are inserting a
            // diagnostic log since the users do not see them anyway; bypass the DAO insert
            // method and use underlying SupportSQLiteDatabase.insert() directly.

            ContentValues values = new ContentValues();
            values.put("logjson", logjson);
            values.put("is_diagnostic", true);
            values.put("priority", priority);
            values.put("timestamp", timestamp);

            SupportSQLiteDatabase db = getOpenHelper().getWritableDatabase();

            db.insert("log", SQLiteDatabase.CONFLICT_NONE, values);
        } else {
            // For status log entry use Room DAO insert method to trigger Rx emission in the log
            // view adapter, this is done via internal use of
            // https://developer.android.com/reference/androidx/room/InvalidationTracker

            logEntryDao().insert(new LogEntry(logjson, false, priority, timestamp));
        }
    }

    public void deleteLogEntriesBefore(long beforeDateMillis) {
        logEntryDao().deleteLogsBefore(beforeDateMillis);
    }

    public DataSource.Factory<Integer, LogEntry> getPagedStatusLogs() {
        return logEntryDao().getPagedLogs(false);
    }

    protected abstract LogEntryDao logEntryDao();

    public Flowable<LogEntry> getLastStatusLogEntry() {
        return logEntryDao().getLastStatusLogEntry();
    }

    public Cursor getLogsBeforeDate(long beforeDateMills) {
        return logEntryDao().getLogsBeforeDate(beforeDateMills);
    }
}