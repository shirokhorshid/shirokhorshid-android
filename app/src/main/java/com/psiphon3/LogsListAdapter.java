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


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.psiphon3.log.LogEntry;
import com.psiphon3.log.MyLog;
import com.psiphon3.psiphonlibrary.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class LogsListAdapter extends PagedListAdapter<LogEntry, LogsListAdapter.LogEntryViewHolder> {

    private Context context;

    public LogsListAdapter(@NonNull DiffUtil.ItemCallback<LogEntry> diffCallback) {
        super(diffCallback);
    }

    @NonNull
    @Override
    public LogEntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_row, parent, false);
        return new LogEntryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull LogEntryViewHolder holder, int position) {
        LogEntry item = getItem(position);
        if (item == null) {
            return;
        }
        if (item.isDiagnostic()) {
            try {
                JSONObject jsonObj = new JSONObject(item.getLogJson());
                Date timestamp = new Date(item.getTimestamp());
                String msg = jsonObj.getString("msg");
                JSONObject data = jsonObj.optJSONObject("data");
                String msgStr = data == null ? msg : msg + ":" + data.toString();
                holder.bind(timestamp, msgStr);
            } catch (JSONException ignored) {
            }
        } else {
                String msg = MyLog.getStatusLogMessageForDisplay(item.getLogJson(), context);
                holder.bind(new Date(item.getTimestamp()), msg);
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        context = recyclerView.getContext();
    }

    static class LogEntryViewHolder extends RecyclerView.ViewHolder {
        private final TextView timestampView;
        private final TextView messageView;

        LogEntryViewHolder(View itemView) {
            super(itemView);
            timestampView = itemView.findViewById(R.id.MessageRow_Timestamp);
            messageView = itemView.findViewById(R.id.MessageRow_Text);
        }

        public void bind(Date timestamp, String msg) {
            if (timestamp != null && msg != null) {
                timestampView.setText(Utils.getLocalTimeString(timestamp));
                messageView.setText(msg);
            }
        }
    }

    public static class LogEntryComparator extends DiffUtil.ItemCallback<LogEntry> {
        @Override
        public boolean areItemsTheSame(@NonNull LogEntry oldItem,
                                       @NonNull LogEntry newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogEntry oldItem,
                                          @NonNull LogEntry newItem) {
            return oldItem.getLogJson().equals(newItem.getLogJson()) &&
                    (oldItem.getTimestamp() == newItem.getTimestamp());
        }
    }
}