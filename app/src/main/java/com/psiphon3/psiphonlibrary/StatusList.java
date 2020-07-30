/*
 * Copyright (c) 2013, Psiphon Inc.
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

package com.psiphon3.psiphonlibrary;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.psiphon3.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

/*
 * Adapted from the sample code here: http://developer.android.com/reference/android/content/AsyncTaskLoader.html
 */

public class StatusList {
    public static DiagnosticEntry getDiagnosticEntry(int index) {
        synchronized(m_diagnosticHistory)
        {
            if (index < 0)
            {
                // index is negative, so this is subtracting...
                index = m_diagnosticHistory.size() + index;
                // Note that index is still negative if the array is empty or if
                // the negative value was too large.
            }

            if (index >= m_diagnosticHistory.size() || index < 0)
            {
                return null;
            }

            return m_diagnosticHistory.get(index);
        }
    }
    /*
     * Status Message History support
     */

    public static class StatusEntry
    {
        private Date timestamp;
        private long key;
        private int stringId;
        private Object[] formatArgs;
        private Throwable throwable;
        private int priority;
        private Utils.MyLog.Sensitivity sensitivity;

        public long key()
        {
            return key;
        }

        public Date timestamp()
        {
            return timestamp;
        }

        public int stringId()
        {
            return stringId;
        }

        public Object[] formatArgs()
        {
            return formatArgs;
        }

        public Throwable throwable()
        {
            return throwable;
        }

        public int priority()
        {
            return priority;
        }

        public Utils.MyLog.Sensitivity sensitivity()
        {
            return sensitivity;
        }
    }

    private static final ArrayList<StatusEntry> m_statusHistory = new ArrayList<>();

    public static void addStatusEntry(
            long key,
            Date timestamp,
            int stringId,
            Utils.MyLog.Sensitivity sensitivity,
            Object[] formatArgs,
            Throwable throwable,
            int priority)
    {
        StatusEntry entry = new StatusEntry();
        entry.key = key;
        entry.timestamp = timestamp;
        entry.stringId = stringId;
        entry.sensitivity = sensitivity;
        entry.formatArgs = formatArgs;
        entry.throwable = throwable;
        entry.priority = priority;

        synchronized(m_statusHistory)
        {
            m_statusHistory.add(entry);
        }
    }

    public static ArrayList<StatusEntry> cloneStatusHistory()
    {
        ArrayList<StatusEntry> copy;
        synchronized(m_statusHistory)
        {
            copy = new ArrayList<>(m_statusHistory);
        }
        return copy;
    }

    /**
     * @param index The index of the item to retrieve.
     * @return Returns item at `index`. Negative indexes count from the end of
     * the array. If `index` is out of bounds, null is returned.
     */
    public static StatusEntry getStatusEntry(int index)
    {
        synchronized(m_statusHistory)
        {
            if (index < 0)
            {
                // index is negative, so this is subtracting...
                index = m_statusHistory.size() + index;
                // Note that index is still negative if the array is empty or if
                // the negative value was too large.
            }

            if (index >= m_statusHistory.size() || index < 0)
            {
                return null;
            }

            return m_statusHistory.get(index);
        }
    }

    /**
     * @return Returns the last non-DEBUG, non-WARN(ing) item, or null if there is none.
     */
    public static StatusEntry getLastStatusEntryForDisplay()
    {
        synchronized(m_statusHistory)
        {
            ListIterator<StatusEntry> iterator = m_statusHistory.listIterator(m_statusHistory.size());

            while (iterator.hasPrevious())
            {
                StatusEntry current_item = iterator.previous();
                if (current_item.priority() != Log.DEBUG &&
                        current_item.priority() != Log.WARN)
                {
                    return current_item;
                }
            }

            return null;
        }
    }

    /*
     * Diagnostic history support
     */

    public static class DiagnosticEntry {
        private Date timestamp;
        private String msg;
        private JSONObject data;
        private long key;
        public Date timestamp()
        {
            return timestamp;
        }

        public String msg()
        {
            return msg;
        }

        public JSONObject data()
        {
            return data;
        }

        public long key()
        {
            return key;
        }

    }

    private static final List<DiagnosticEntry> m_diagnosticHistory = new ArrayList<>();

    public static void addDiagnosticEntry(long key, Date timestamp, String msg, JSONObject data)
    {
        DiagnosticEntry entry = new DiagnosticEntry();
        entry.key = key;
        entry.timestamp = timestamp;
        entry.msg = msg;
        entry.data = data;
        synchronized(m_diagnosticHistory)
        {
            m_diagnosticHistory.add(entry);
        }
    }

    public static List<DiagnosticEntry> cloneDiagnosticHistory()
    {
        List<DiagnosticEntry> copy;
        synchronized(m_diagnosticHistory)
        {
            copy = new ArrayList<>(m_diagnosticHistory);
        }
        return copy;
    }

    public static class StatusListAdapter extends ArrayAdapter<StatusEntry> {
        private final LayoutInflater m_inflater;
        private final int m_resourceID;
        private final int m_textViewResourceId;
        private final int m_imageViewResourceId;
        private final int m_timestampViewResourceId;
        private final Drawable m_imageInfo;
        private final Drawable m_imageError;

        public StatusListAdapter(
                Context context, 
                int resource, 
                int textViewResourceId,
                int imageViewResourceId,
                int timestampViewResourceId) {
            super(context, resource, textViewResourceId);
            m_resourceID = resource;
            m_textViewResourceId = textViewResourceId;
            m_imageViewResourceId = imageViewResourceId;
            m_timestampViewResourceId = timestampViewResourceId;
            
            m_inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            Resources res = context.getResources();
            m_imageInfo = res.getDrawable(android.R.drawable.presence_online);  
            m_imageError = res.getDrawable(android.R.drawable.presence_busy);  
        }

        @Override 
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView;

            if (convertView == null) {
                rowView = m_inflater.inflate(m_resourceID, null);
            } else {
                rowView = convertView;
            }

            StatusEntry item = getItem(position);
            
            Drawable messageClassImage = null;
            boolean boldText = true;

            switch (item.priority())
            {
            case Log.INFO:
                messageClassImage = m_imageInfo;
                break;
            case Log.ERROR:
                messageClassImage = m_imageError;
                break;
            default:
                // No image
                boldText = false;
                break;
            }
            
            String msg = getContext().getString(item.stringId(), item.formatArgs());
            
            if (item.throwable() != null)
            {
                // Just report the first line of the stack trace
                String[] stackTraceLines = Log.getStackTraceString(item.throwable()).split("\n");
                msg = msg + (stackTraceLines.length > 0 ? "\n" + stackTraceLines[0] : ""); 
            }
            
            TextView textView = (TextView)rowView.findViewById(m_textViewResourceId);
            if (textView != null) {
                textView.setText(msg);
                textView.setTypeface(boldText ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
            
            ImageView imageView = (ImageView)rowView.findViewById(m_imageViewResourceId);
            if (imageView != null) {
                imageView.setImageDrawable(messageClassImage);
            }

            TextView timestampView = (TextView)rowView.findViewById(m_timestampViewResourceId);
            if (timestampView != null) {
                timestampView.setText(Utils.getLocalTimeString(item.timestamp()));
            }
            
            return rowView;
        }
        
        public void addEntries(List<StatusEntry> entries) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                addEntriesFast(entries);
            }
            else {
                addEntriesSlow(entries);
            }
        }
        
        @TargetApi(Build.VERSION_CODES.HONEYCOMB) 
        private void addEntriesFast(List<StatusEntry> entries) {
            addAll(entries);
        }

        private void addEntriesSlow(List<StatusEntry> entries) {
            for (StatusEntry entry : entries) {
                add(entry);
            }
        }
    }
    
    public static class StatusListIntentReceiver extends BroadcastReceiver {
        public void unregisterSelf() {
            m_localBroadcastManager.unregisterReceiver(this);
        }

        public interface NotificationRecipient {
            void statusAddedNotificationReceived();
        }
        
        public static final String STATUS_ADDED = "com.psiphon3.PsiphonAndroidActivity.STATUS_ADDED";
        
        final Context m_context;
        final LocalBroadcastManager m_localBroadcastManager;
        final NotificationRecipient m_recipient;
        
        public StatusListIntentReceiver(Context context, NotificationRecipient recipient) {
            m_context = context;
            m_localBroadcastManager = LocalBroadcastManager.getInstance(m_context);
            m_recipient = recipient;
            
            IntentFilter filter = new IntentFilter(STATUS_ADDED);
            m_localBroadcastManager.registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            m_recipient.statusAddedNotificationReceived();
        }
        
        // Sends an intent to itself.
        public void notifyStatusAdded() {
            m_localBroadcastManager.sendBroadcast(new Intent(STATUS_ADDED));
        }        
    }
    
    public static class StatusListViewManager 
        implements StatusListIntentReceiver.NotificationRecipient {
        
        final StatusListAdapter m_adapter;
        final ListView m_listview;
        final StatusListIntentReceiver m_intentReceiver;
        int m_nextStatusEntryIndex = 0;

        public StatusListViewManager(ListView listview) {
            Context context = listview.getContext();
            
            m_adapter = new StatusListAdapter(
                    context,
                    R.layout.message_row,
                    R.id.MessageRow_Text,
                    0,
                    R.id.MessageRow_Timestamp);
            
            m_listview = listview;
            m_listview.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            m_listview.setAdapter(m_adapter);
            
            m_intentReceiver = new StatusListIntentReceiver(context, this);
            
            scrollListViewToBottom();
        }
        
        public void notifyStatusAdded() {
            m_intentReceiver.notifyStatusAdded();
        }
        
        /**
         * Should only be called from StatusListIntentReceiver.
         * @see com.psiphon3.psiphonlibrary.StatusList.StatusListIntentReceiver.NotificationRecipient#statusAddedNotificationReceived()
         */
        @Override
        public void statusAddedNotificationReceived() {
            // It might only be one item that's been added, but proceed as if 
            // there are a bunch to bulk-load.
            
            List<StatusEntry> newEntries = new ArrayList<>();
            while (true) {
                StatusEntry entry = getStatusEntry(m_nextStatusEntryIndex);
                if (entry == null) {
                    // No more entries to add
                    break;
                }
                
                m_nextStatusEntryIndex += 1;
                
                // Never show debug messages
                // Also, don't show warnings
                if (entry.priority() == Log.DEBUG ||
                        entry.priority() == Log.WARN) {
                    continue;
                }
                
                newEntries.add(entry);
            }
            
            m_adapter.addEntries(newEntries);
        }
        
        private void scrollListViewToBottom() {
            m_listview.post(new Runnable() {
                @Override
                public void run() {
                    // Select the last row so it will scroll into view...
                    m_listview.setSelection(m_adapter.getCount() - 1);
                }
            });
        }

        public void onDestroy() {
            m_intentReceiver.unregisterSelf();
        }
    }
}
