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

import com.jakewharton.rxrelay2.PublishRelay;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public class LogsLastEntryHelper {
    private final ContentResolver contentResolver;
    private final PublishRelay<LogEntry> lastLogEntryRelay = PublishRelay.create();

    public LogsLastEntryHelper(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public void fetchLatest() {
        Uri uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                .appendPath("status")
                .appendPath("last")
                .build();
        try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
            if (cursor.moveToFirst()) {
                final LogEntry logEntry = LoggingContentProvider.convertRows(cursor);
                lastLogEntryRelay.accept(logEntry);
            }
        }
    }

    public Flowable<LogEntry> getFlowable() {
        return lastLogEntryRelay.toFlowable(BackpressureStrategy.LATEST);
    }
}
