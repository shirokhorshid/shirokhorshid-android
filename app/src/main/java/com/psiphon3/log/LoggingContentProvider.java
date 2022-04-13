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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.psiphon3.BuildConfig;

import java.util.concurrent.Executors;

public class LoggingContentProvider extends ContentProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + "." + LoggingContentProvider.class.getSimpleName();
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static final int STATUS_LOGS = 1;
    private static final int STATUS_LOGS_COUNT = 2;
    private static final int DELETE_LOGS_BEFORE = 3;
    private static final int STATUS_LOG_LAST = 4;
    private static final int ALL_LOGS_BEFORE = 5;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(AUTHORITY, "status/offset/#/limit/#", STATUS_LOGS);
        sUriMatcher.addURI(AUTHORITY, "status/count", STATUS_LOGS_COUNT);
        sUriMatcher.addURI(AUTHORITY, "delete/#", DELETE_LOGS_BEFORE);
        sUriMatcher.addURI(AUTHORITY, "status/last", STATUS_LOG_LAST);
        sUriMatcher.addURI(AUTHORITY, "all/#", ALL_LOGS_BEFORE);
    }

    public static LogEntry convertRows(Cursor cursor) {
        final int cursorIndexOfId = cursor.getColumnIndexOrThrow("_ID");
        final int cursorIndexOfLogJson = cursor.getColumnIndexOrThrow("logjson");
        final int cursorIndexOfIsDiagnostic = cursor.getColumnIndexOrThrow("is_diagnostic");
        final int cursorIndexOfPriority = cursor.getColumnIndexOrThrow("priority");
        final int cursorIndexOfTimestamp = cursor.getColumnIndexOrThrow("timestamp");

        final String tmpLogJson = cursor.getString(cursorIndexOfLogJson);
        final boolean tmpIsDiagnostic = cursor.getInt(cursorIndexOfIsDiagnostic) != 0;
        final int tmpPriority = cursor.getInt(cursorIndexOfPriority);
        final long tmpTimestamp = cursor.getLong(cursorIndexOfTimestamp);

        final LogEntry logEntry = new LogEntry(tmpLogJson, tmpIsDiagnostic, tmpPriority, tmpTimestamp);

        final int tmpId = cursor.getInt(cursorIndexOfId);
        logEntry.setId(tmpId);

        return logEntry;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int match = sUriMatcher.match(uri);

        switch (match) {
            case STATUS_LOGS_COUNT:
                return getCount();

            case STATUS_LOGS:
                int offset = Integer.parseInt(uri.getPathSegments().get(2));
                int limit = Integer.parseInt(uri.getPathSegments().get(4));
                return getStatusLogs(offset, limit);

            case STATUS_LOG_LAST:
                return getLastStatusLogEntry();

            case ALL_LOGS_BEFORE:
                long beforeMillis = Long.parseLong(uri.getPathSegments().get(1));
                return getAllLogsBefore(beforeMillis);

            default:
                return null;
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        final Context context = getContext();
        if (context == null || values == null) {
            return null;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        db.getQueryExecutor().execute(() -> {
            db.getOpenHelper().getWritableDatabase()
                    .insert("log", SQLiteDatabase.CONFLICT_NONE, values);
            if (!values.getAsBoolean("is_diagnostic")) {
                context.getContentResolver().notifyChange(uri, null);
            }
        });
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        final Context context = getContext();
        if (context == null) {
            return 0;
        }
        int match = sUriMatcher.match(uri);
        if (match != DELETE_LOGS_BEFORE) {
            return 0;
        }
        long beforeMillis = Long.parseLong(uri.getPathSegments().get(1));
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        db.getQueryExecutor().execute(() -> {
            int deletedRows = db.deleteLogEntriesBefore(beforeMillis);
            if (deletedRows > 0) {
                context.getContentResolver().notifyChange(uri, null);
            }
        });
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private Cursor getStatusLogs(int offset, int limit) {
        final Context context = getContext();
        if (context == null) {
            return null;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        return db.getStatusLogs(offset, limit);
    }

    private Cursor getCount() {
        final Context context = getContext();
        if (context == null) {
            return null;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        return db.getStatusLogsCount();
    }

    private Cursor getLastStatusLogEntry() {
        final Context context = getContext();
        if (context == null) {
            return null;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        return db.getLastStatusLogEntry();
    }

    private Cursor getAllLogsBefore(long beforeMillis) {
        final Context context = getContext();
        if (context == null) {
            return null;
        }
        LoggingRoomDatabase db =
                LoggingRoomDatabase.getDatabase(context.getApplicationContext());
        return db.getLogsBeforeDate(beforeMillis);
    }

    @Database(entities = {LogEntry.class,}, version = 3, exportSchema = false)
    public abstract static class LoggingRoomDatabase extends RoomDatabase {
        private static volatile LoggingRoomDatabase INSTANCE;

        private static LoggingRoomDatabase getDatabase(final Context context) {
            if (INSTANCE == null) {
                synchronized (LoggingRoomDatabase.class) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                LoggingRoomDatabase.class, "loggingprovider.db")
                                // Here we are migrating from plain SQLiteOpenHelper to Room; we are
                                // not providing migration strategy, because in the previous
                                // version(#2) the logs table is fully truncated every time the app
                                // starts fresh.
                                .fallbackToDestructiveMigration()
                                .setQueryExecutor(Executors.newSingleThreadExecutor())
                                .build();
                    }
                }
            }
            return INSTANCE;
        }

        protected abstract LogEntryDao logEntryDao();

        public int deleteLogEntriesBefore(long beforeDateMillis) {
            return logEntryDao().deleteLogsBefore(beforeDateMillis);
        }

        public Cursor getLastStatusLogEntry() {
            return logEntryDao().getLastStatusLogEntry();
        }

        public Cursor getLogsBeforeDate(long beforeDateMills) {
            return logEntryDao().getLogsBeforeDate(beforeDateMills);
        }

        public Cursor getStatusLogsCount() {
            return logEntryDao().getStatusLogsCount();
        }

        public Cursor getStatusLogs(int offset, int limit) {
            return logEntryDao().getStatusLogs(offset, limit);
        }
    }
}
