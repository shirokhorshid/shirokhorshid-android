/*
 * Zirco Browser for Android
 * 
 * Copyright (C) 2010 - 2011 J. Devauchelle and contributors.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package org.zirco.providers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

public class ZircoBookmarksContentProvider extends ContentProvider {
	
	public static final String AUTHORITY = "org.zirco.providers.psiphonzircobookmarkscontentprovider";
	
	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.zirco.bookmarks";

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "bookmarks.db";
	
	public static final String BOOKMARKS_TABLE = "bookmarks";

    public static class BookmarkColumns implements BaseColumns {
        public static final String URL = "url";
        public static final String VISITS = "visits";
        public static final String DATE = "date";
        public static final String BOOKMARK = "bookmark";
        public static final String TITLE = "title";
        public static final String CREATED = "created";
        public static final String FAVICON = "favicon";
    }

    private static final String BOOKMARKS_TABLE_CREATE = "CREATE TABLE " + BOOKMARKS_TABLE + " (" +
			BookmarkColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            BookmarkColumns.TITLE + " TEXT, " +
            BookmarkColumns.URL + " TEXT, " +
            BookmarkColumns.VISITS + " INTEGER, " +
            BookmarkColumns.DATE + " LONG, " +
            BookmarkColumns.CREATED + " LONG, " +
            BookmarkColumns.BOOKMARK + " INTEGER, " +
            BookmarkColumns.FAVICON + " BLOB DEFAULT NULL);";
	
	private static final int BOOKMARKS = 1;

	private static final UriMatcher sUriMatcher;
	
	private SQLiteDatabase mDb;

    private Context mContext;
	
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, BOOKMARKS_TABLE, BOOKMARKS);
	}
	
	@Override
	public boolean onCreate() {
		mContext = getContext();
        DatabaseHelper mDbHelper = new DatabaseHelper(mContext);
		mDb = mDbHelper.getWritableDatabase();
		return true;
	}

	@Override
	public int delete(Uri uri, String whereClause, String[] whereArgs) {
		int count = 0;
		
		switch (sUriMatcher.match(uri)) {
		case BOOKMARKS:
			count = mDb.delete(BOOKMARKS_TABLE, whereClause, whereArgs);
			break;
			
		default: throw new IllegalArgumentException("Unknown URI " + uri);
		}		
		
		if (count > 0) {
			mContext.getContentResolver().notifyChange(uri, null);
		}
		
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case BOOKMARKS:
			return CONTENT_TYPE;
		default: throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
	    return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		
		switch (sUriMatcher.match(uri)) {
		case BOOKMARKS:
			qb.setTables(BOOKMARKS_TABLE);			
			break;
		default: throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		Cursor c = qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}
	
	private static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(BOOKMARKS_TABLE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }		
	}

    private static final Uri BOOKMARKS_URI = Uri.parse("content://" + AUTHORITY + "/" + BOOKMARKS_TABLE);
    private static String[] sHistoryBookmarksProjection = new String[] { BookmarkColumns._ID,
            BookmarkColumns.TITLE,
            BookmarkColumns.URL,
            BookmarkColumns.VISITS,
            BookmarkColumns.DATE,
            BookmarkColumns.CREATED,
            BookmarkColumns.BOOKMARK,
            BookmarkColumns.FAVICON };

    public static Cursor getAllRecords(ContentResolver contentResolver) {
        return contentResolver.query(BOOKMARKS_URI, sHistoryBookmarksProjection, null, null, null);
    }

}
