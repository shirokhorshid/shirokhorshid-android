/*
 *
 * Copyright (c) 2019, Psiphon Inc.
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

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.ArrayList;

@AutoValue
public abstract class TunnelState {
    public enum Status {
        RUNNING,
        STOPPED,
        UNKNOWN,
    }

    @AutoValue
    public static abstract class ConnectionData {
        public abstract boolean isConnected();

        public abstract String clientRegion();

        public abstract String clientVersion();

        public abstract String propagationChannelId();

        public abstract String sponsorId();

        public abstract int httpPort();

        @Nullable
        public abstract ArrayList<String> homePages();

        public static Builder builder() {
            return new AutoValue_TunnelState_ConnectionData.Builder()
                    .setIsConnected(false)
                    .setClientRegion("")
                    .setClientVersion("")
                    .setPropagationChannelId("")
                    .setSponsorId("")
                    .setHttpPort(0)
                    .setHomePages(null);
        }

        @AutoValue.Builder
        public static abstract class Builder {
            public abstract Builder setIsConnected(boolean value);

            public abstract Builder setClientRegion(String value);

            public abstract Builder setClientVersion(String value);

            public abstract Builder setPropagationChannelId(String value);

            public abstract Builder setSponsorId(String value);

            public abstract Builder setHttpPort(int port);

            public abstract Builder setHomePages(@Nullable ArrayList<String> homePages);

            public abstract ConnectionData build();
        }
    }

    public abstract Status status();

    @Nullable
    public abstract ConnectionData connectionData();

    public static TunnelState unknown() {
        return new AutoValue_TunnelState(Status.UNKNOWN, null);
    }

    public static TunnelState stopped() {
        return new AutoValue_TunnelState(Status.STOPPED, null);
    }

    public static TunnelState running(ConnectionData connectionData) {
        return new AutoValue_TunnelState(Status.RUNNING, connectionData);
    }

    public boolean isRunning() {
        return status() == Status.RUNNING;
    }

    public boolean isUnknown() {
        return status() == Status.UNKNOWN;
    }

    public boolean isStopped() {
        return status() == Status.STOPPED;
    }
}
