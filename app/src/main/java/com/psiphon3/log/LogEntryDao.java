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

import android.database.Cursor;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Query;

import io.reactivex.Flowable;

@Dao
public abstract class LogEntryDao {
    @Query("SELECT COUNT(*) FROM log WHERE is_diagnostic = 0")
    abstract Cursor getStatusLogsCount();

    @Query("SELECT * FROM log WHERE timestamp < :beforeDateMillis ORDER BY timestamp DESC")
    abstract Cursor getLogsBeforeDate(long beforeDateMillis);

    @Query("DELETE FROM log WHERE timestamp < :beforeDateMillis")
    abstract int deleteLogsBefore(long beforeDateMillis);

    @Query("SELECT * FROM log WHERE is_diagnostic = 0 ORDER BY timestamp DESC LIMIT 1")
    public abstract Cursor getLastStatusLogEntry();

    @Query("SELECT * FROM log WHERE is_diagnostic = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    public abstract Cursor getStatusLogs(int offset, int limit);
}
