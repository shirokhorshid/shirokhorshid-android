/*
 * Copyright (c) 2016, Psiphon Inc.
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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.psiphon3.psiphonlibrary.Utils.MyLog;
import com.psiphon3.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * All logging is done directly to the LoggingProvider from all processes.
 */
public class LoggingProvider extends ContentProvider {
    public static final Uri INSERT_URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + "." + LoggingProvider.class.getSimpleName());

    /**
     * JSON-ify the arguments to be used in a call to the LoggingProvider content provider.
     * @param context The context to be used for access app resources.
     * @param date Timestamp for the log.
     * @param stringResID String resource ID.
     * @param sensitivity Log sensitivity level.
     * @param formatArgs Arguments to be formatted into the log string.
     * @param priority One of the log priority levels supported by MyLog. Like: Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.VERBOSE
     * @return null on error.
     */
    public static String makeStatusLogJSON(Context context,
                                           Date date,
                                           int stringResID,
                                           MyLog.Sensitivity sensitivity,
                                           Object[] formatArgs,
                                           int priority) {
        String resourceName = context.getResources().getResourceName(stringResID);

        JSONObject json = new JSONObject();
        try {
            JSONArray jsonArray = new JSONArray();
            if (formatArgs != null) {
                for (Object arg : formatArgs) {
                    jsonArray.put(arg);
                }
            }

            json.put("timestamp", date.getTime()); // Store as millis since epoch
            json.put("stringResourceName", resourceName);
            json.put("sensitivity", sensitivity.name());
            json.put("formatArgs", jsonArray);
            json.put("priority", priority);
            return json.toString();
        } catch (JSONException e) {
            // pass
        }

        return null;
    }

    /**
     * JSON-ify the arguments to be used in a call to the LoggingProvider content provider.
     * @param date Timestamp for the log.
     * @param msg String nessage name.
     * @param data String json data.
     * @return null on error.
     */
    public static String makeDiagnosticLogJSON(Date date, String msg, JSONObject data) {
        JSONObject json = new JSONObject();
        try {
            json.put("timestamp", date.getTime()); // Store as millis since epoch
            json.put("msg", msg);
            json.put("data", data);
            return json.toString();
        } catch (JSONException e) {
            // pass
        }

        return null;
    }

    /**
     * To be called by the UI when logs should be read from the provider DB into the StatusList.
     * @param context
     */
    public static void retrieveLogs(Context context) {
        LogDatabaseHelper.retrieveLogs(context);
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        assert(false);
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        assert(false);
        return null;
    }

    /**
     * Called when a content provider consumer wants to create a log.
     * @param uri Ignored.
     * @param values Must have COLUMN_NAME_LOGJSON value, created by makeStatusLogJSON() or makeDiagnosticLogJSON()
     *               and boolean COLUMN_NAME_IS_DIAGNOSTIC indicating whether the log entry is 'diagnostic' or 'status'
     * @return Always returns null.
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        LogDatabaseHelper.insertLog(this.getContext(), values);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        assert(false);
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        assert(false);
        return 0;
    }

    /**
     * The database where logs are stored until they can be consumed by the app.
     */
    public static class LogDatabaseHelper extends SQLiteOpenHelper {
        private static final int DAYS_TO_STORE_LOGS = 2;
        private static final String DATABASE_NAME = "loggingprovider.db";
        private static final int DATABASE_VERSION = 2;

        private static final String TABLE_NAME = "log";
        private static final String COLUMN_NAME_ID = "_ID";
        public static final String COLUMN_NAME_LOGJSON = "logjson";
        public static final String COLUMN_NAME_IS_DIAGNOSTIC = "is_diagnostic";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
        private static final String DICTIONARY_TABLE_CREATE =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_NAME_LOGJSON + " TEXT NOT NULL, " +
                        COLUMN_NAME_IS_DIAGNOSTIC + " BOOLEAN DEFAULT 0, " +
                        COLUMN_NAME_TIMESTAMP + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP " +
                ");";

        /**
         * The database object. Note that SQLite is thread-safe (by default).
         */
        private SQLiteDatabase mDB;

        // Singleton pattern
        private static LogDatabaseHelper mLogDatabaseHelper;
        public Object clone() throws CloneNotSupportedException
        {
            throw new CloneNotSupportedException();
        }
        public static synchronized LogDatabaseHelper get(Context context)
        {
            if (mLogDatabaseHelper == null)
            {
                mLogDatabaseHelper = new LogDatabaseHelper(context);
            }

            return mLogDatabaseHelper;
        }

        public synchronized SQLiteDatabase getDB()
        {
            if (mDB == null)
            {
                mDB = mLogDatabaseHelper.getWritableDatabase();
            }

            return mDB;
        }

        public LogDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DICTIONARY_TABLE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if(newVersion == 2 && oldVersion == 1) {
                db.execSQL("DROP TABLE IF EXISTS "+ TABLE_NAME);
                db.execSQL(DICTIONARY_TABLE_CREATE);
            }
        }

        /**
         * Insert a new log. May execute asynchronously.
         */
        public static void insertLog(Context context, ContentValues values) {
            // If this function is being called in the UI thread, then we need to do the work in an
            // async task. Otherwise we'll do the work directly.
            // For info about content provider thread use: http://stackoverflow.com/a/3571583
            if (Looper.myLooper() == Looper.getMainLooper()) {
                InsertLogTask task = new InsertLogTask(context);
                task.execute(values);
            }
            else {
                LogDatabaseHelper.insertLogHelper(context, values);
            }
        }

        /**
         * Task to do the async work.
         */
        private static class InsertLogTask extends AsyncTask<ContentValues, Void, Void> {
            private Context mContext;
            public InsertLogTask (Context context){
                mContext = context;
            }

            @Override
            protected Void doInBackground(ContentValues... params) {
                // DO NOT LOG WITHIN THIS FUNCTION

                // There will only ever be one item in the array, but...
                for (int i = 0; i < params.length; i++) {
                    LogDatabaseHelper.insertLogHelper(mContext, params[i]);
                }

                return null;
            }
        }

        /**
         * Inserts a new log. Should be called via insertLog or InsertLogTask.
         * @param context
         * @param values
         */
        private static void insertLogHelper(Context context, ContentValues values) {
            // DO NOT LOG WITHIN THIS FUNCTION

            SQLiteDatabase db = LogDatabaseHelper.get(context).getDB();

            db.beginTransaction();

            db.insert(TABLE_NAME, null, values);

            db.setTransactionSuccessful();
            db.endTransaction();

            context.getContentResolver().notifyChange(INSERT_URI, null);
        }

        /**
         * To be called by the UI at a time when it's appropriate to truncate logs database.
         * May execute asynchronously.
         */
        public static void truncateLogs(Context context, boolean full) {
            // If this function is being called in the UI thread, then we need to do the work in an
            // async task. Otherwise we'll do the work directly.
            // For info about content provider thread use: http://stackoverflow.com/a/3571583
            if (Looper.myLooper() == Looper.getMainLooper()) {
                TruncateLogsTask task = new TruncateLogsTask(context, full);
                task.execute();
            }
            else {
                LogDatabaseHelper.truncateLogsHelper(context, full);
            }

        }

        /**
         * Task to do the async work.
         */
        private static class TruncateLogsTask extends AsyncTask<Void, Void, Void> {
            private Context mContext;
            private boolean mFull;
            public TruncateLogsTask (Context context, boolean full) {
                mContext = context;
                mFull = full;
            }

            @Override
            protected Void doInBackground(Void... params) {
                LogDatabaseHelper.truncateLogsHelper(mContext, mFull);
                return null;
            }
        }

        /**
         * Does the log truncation work. Should be called via truncateLogs or TruncateLogsTask.
         * @param context
         */
        private static void truncateLogsHelper(Context context, boolean full) {
            SQLiteDatabase db = LogDatabaseHelper.get(context).getDB();

            String whereClause = null;
            String[] whereArgs = null;

            if (!full) {
                whereClause = COLUMN_NAME_TIMESTAMP + "<?";

                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.add(Calendar.DATE, -DAYS_TO_STORE_LOGS);

                whereArgs = new String[]{dateFormat.format(cal.getTime())};
            }

            db.delete(TABLE_NAME, whereClause, whereArgs);
        }

        /**
         * To be called by the UI at a time when it's appropriate to consume logs that were stored
         * by the provider. May execute asynchronously.
         */
        public static void retrieveLogs(Context context) {
            // If this function is being called in the UI thread, then we need to do the work in an
            // async task. Otherwise we'll do the work directly.
            // For info about content provider thread use: http://stackoverflow.com/a/3571583
            if (Looper.myLooper() == Looper.getMainLooper()) {
                RetrieveLogsTask task = new RetrieveLogsTask(context);
                task.execute();
            }
            else {
                LogDatabaseHelper.retrieveLogsHelper(context);
            }

        }

        /**
         * Task to do the async work.
         */
        private static class RetrieveLogsTask extends AsyncTask<Void, Void, Void> {
            private Context mContext;
            public RetrieveLogsTask (Context context){
                mContext = context;
            }

            @Override
            protected Void doInBackground(Void... params) {
                // DO NOT LOG WITHIN THIS FUNCTION

                LogDatabaseHelper.retrieveLogsHelper(mContext);

                return null;
            }
        }

        /**
         * Does the log retrieval work. Should be called via retrieveLogs or RetrieveLogsTask.
         * @param context
         */
        private static void retrieveLogsHelper(Context context) {
            // DO NOT LOG WITHIN THIS FUNCTION

            // We will cursor through DB records, passing them off to StatusList

            SQLiteDatabase db = LogDatabaseHelper.get(context).getDB();

            String[] projection = {
                    COLUMN_NAME_ID,
                    COLUMN_NAME_IS_DIAGNOSTIC,
                    COLUMN_NAME_LOGJSON
            };


            // retrieve status logs  (COLUMN_NAME_IS_DIAGNOSTIC == false)
            String whereClause = "NOT(" + COLUMN_NAME_IS_DIAGNOSTIC + ") ";
            String[] whereArgs = null;

            StatusList.StatusEntry lastEntry = StatusList.getStatusEntry(-1);
            if (lastEntry != null) {
                whereClause += " AND " + COLUMN_NAME_ID + " >?";
                whereArgs = new String[]{String.valueOf(lastEntry.key())};
            }

            String sortOrder = COLUMN_NAME_ID + " ASC";

            Cursor cursor = db.query(
                    TABLE_NAME,
                    projection,
                    whereClause,
                    whereArgs,
                    null, null,
                    sortOrder);

            int numberOfLogsRetrieved = 0;

            // Iterate over the cursor
            try {
                while (cursor.moveToNext()) {

                    long ID = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID));
                    String logJSON = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME_LOGJSON));

                    // Extract log args from JSON.
                    String stringResourceName;
                    int priority;
                    MyLog.Sensitivity sensitivity;
                    Object[] formatArgs;
                    Date timestamp;
                    try {
                        JSONObject jsonObj = new JSONObject(logJSON);
                        stringResourceName = jsonObj.getString("stringResourceName");
                        sensitivity = MyLog.Sensitivity.valueOf(jsonObj.getString("sensitivity"));
                        priority = jsonObj.getInt("priority");
                        timestamp = new Date(jsonObj.getLong("timestamp"));

                        JSONArray formatArgsJSONArray = jsonObj.getJSONArray("formatArgs");
                        formatArgs = new Object[formatArgsJSONArray.length()];
                        for (int i = 0; i < formatArgsJSONArray.length(); i++) {
                            formatArgs[i] = formatArgsJSONArray.get(i);
                        }

                        // Convert the resource name to ID.
                        int resourceID = context.getResources().getIdentifier(stringResourceName, null, null);
                        if (resourceID == 0) {
                            // Failed to convert from resource name to ID. This can happen if a
                            // string resource has been renamed since the log entry was created.
                            continue;
                        }

                        // Pass the log info on to StatusList.
                        // Keep this call in the try block so it gets skipped if there's an exception above.
                        StatusList.addStatusEntry(
                                ID,
                                timestamp,
                                resourceID,
                                sensitivity,
                                formatArgs,
                                null,
                                priority);

                        numberOfLogsRetrieved++;
                    } catch (JSONException e) {
                        // just skip this entry
                    }
                }
            } finally {
                cursor.close();
            }

            if (numberOfLogsRetrieved > 0) {
                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(MainBase.TabbedActivityBase.STATUS_ENTRY_AVAILABLE));
            }


            // retrieve diagnostic logs  (COLUMN_NAME_IS_DIAGNOSTIC == true)
            whereClause = COLUMN_NAME_IS_DIAGNOSTIC;
            whereArgs = null;
            StatusList.DiagnosticEntry lastDiagnosticEntry = StatusList.getDiagnosticEntry(-1);
            if (lastDiagnosticEntry != null) {
                whereClause += " AND " + COLUMN_NAME_ID + " >?";
                whereArgs = new String[]{String.valueOf(lastDiagnosticEntry.key())};
            }

            sortOrder = COLUMN_NAME_ID + " ASC";

            cursor = db.query(
                    TABLE_NAME,
                    projection,
                    whereClause,
                    whereArgs,
                    null, null,
                    sortOrder);

            // Iterate over the cursor
            try {
                while (cursor.moveToNext()) {

                    long ID = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID));
                    String logJSON = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME_LOGJSON));

                    // Extract log args from JSON.
                    Date timestamp;
                    String msg;
                    JSONObject data;
                    try {
                        JSONObject jsonObj = new JSONObject(logJSON);
                        timestamp = new Date(jsonObj.getLong("timestamp"));
                        msg = jsonObj.getString("msg");
                        data = jsonObj.getJSONObject("data");

                        // Pass the log info on to StatusList.
                        // Keep this call in the try block so it gets skipped if there's an exception above.
                        StatusList.addDiagnosticEntry(ID, timestamp, msg, data);
                    } catch (JSONException e) {
                        // just skip this entry
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }
}
