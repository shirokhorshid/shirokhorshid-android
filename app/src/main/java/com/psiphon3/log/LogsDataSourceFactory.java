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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogsDataSourceFactory extends DataSource.Factory<Integer, LogEntry> {
    private final ContentResolver contentResolver;
    private LogsDataSource dataSource;

    public LogsDataSourceFactory(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    @NonNull
    @Override
    public DataSource<Integer, LogEntry> create() {
        dataSource = new LogsDataSource(contentResolver);
        return dataSource;
    }

    public void invalidateDataSource() {
        if (dataSource != null) {
            dataSource.invalidate();
        }
    }

    private static class LogsDataSource extends PositionalDataSource<LogEntry> {
        private final ContentResolver contentResolver;

        public LogsDataSource(ContentResolver contentResolver) {
            this.contentResolver = contentResolver;
        }

        @Override
        public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<LogEntry> callback) {
            int firstLoadPosition = 0;
            int firstLoadSize = 0;
            int totalCount = getStatusLogsCount();

            if (totalCount != 0) {
                firstLoadPosition = computeInitialLoadPosition(params, totalCount);
                firstLoadSize = computeInitialLoadSize(params, firstLoadPosition, totalCount);
            }
            callback.onResult(getStatusLogs(firstLoadPosition, firstLoadSize), firstLoadPosition, totalCount);
        }

        private int getStatusLogsCount() {
            int count = 0;
            Uri uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                    .appendPath("status")
                    .appendPath("count")
                    .build();

            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
            }
            return count;
        }

        @Override
        public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<LogEntry> callback) {
            callback.onResult(getStatusLogs(params.startPosition, params.loadSize));
        }

        private List<LogEntry> getStatusLogs(int offset, int limit) {
            Uri uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                    .appendPath("status")
                    .appendPath("offset")
                    .appendPath(String.valueOf(offset))
                    .appendPath("limit")
                    .appendPath(String.valueOf(limit))
                    .build();
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor == null) {
                    return Collections.emptyList();
                }
                final List<LogEntry> logEntryList = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    final LogEntry logEntry = LoggingContentProvider.convertRows(cursor);
                    logEntryList.add(logEntry);
                }
                return logEntryList;
            }
        }
    }
}
