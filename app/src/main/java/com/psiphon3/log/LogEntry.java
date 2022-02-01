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


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "log", indices = {@Index("timestamp")})
public class LogEntry {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_ID")
    @NonNull
    private int id;

    @ColumnInfo(name = "logjson")
    @NonNull
    private String logJson;

    @ColumnInfo(name = "is_diagnostic")
    private boolean isDiagnostic;

    @ColumnInfo(name = "priority")
    private int priority;


    @ColumnInfo(name = "timestamp")
    private long timestamp;

    public LogEntry(@NonNull String logJson, boolean isDiagnostic, int priority, long timestamp) {
        this.logJson = logJson;
        this.isDiagnostic = isDiagnostic;
        this.priority = priority;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getLogJson() {
        return logJson;
    }

    public void setLogJson(@Nullable String logJson) {
        this.logJson = logJson;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDiagnostic() {
        return isDiagnostic;
    }

    public void setDiagnostic(boolean diagnostic) {
        isDiagnostic = diagnostic;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "id=" + id +
                ", logJson='" + logJson + '\'' +
                ", isDiagnostic=" + isDiagnostic +
                ", priority=" + priority +
                ", timestamp=" + timestamp +
                '}';
    }
}