/*
 * Zirco Browser for Android
 * 
 * Copyright (C) 2010 J. Devauchelle and contributors.
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

package org.zirco.ui.activities;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import org.zirco.controllers.Controller;
import org.zirco.utils.Constants;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;

/**
 * Combined bookmarks and history activity.
 */
public class BookmarksHistoryActivity extends LocalizedActivities.TabActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		
		if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_SHOW_FULL_SCREEN, false)) {        	
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        
        // PSIPHON: always show the title bar
        //if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_GENERAL_HIDE_TITLE_BARS, true)) {
        //	requestWindowFeature(Window.FEATURE_NO_TITLE);
        //}
		
		setContentView(R.layout.bookmarks_history_activity);
		
		setTitle(R.string.BookmarksListActivity_Title);
		
		Resources res = getResources();
		TabHost tabHost = getTabHost();
		TabHost.TabSpec spec;
		Intent intent;
		
		// Bookmarks
		intent = new Intent().setClass(this, BookmarksListActivity.class);
		
		spec = tabHost.newTabSpec("bookmarks").setIndicator(res.getString(R.string.Main_MenuShowBookmarks),
                res.getDrawable(R.drawable.ic_tab_bookmarks))
                .setContent(intent);
		tabHost.addTab(spec);
		
		// History
		intent = new Intent().setClass(this, HistoryListActivity.class);

		spec = tabHost.newTabSpec("history").setIndicator(res.getString(R.string.Main_MenuShowHistory),
                res.getDrawable(R.drawable.ic_tab_history))
                .setContent(intent);
		tabHost.addTab(spec);

		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREFERENCE_USE_WEAVE, false)) {
			// Weave bookmarks
			intent = new Intent().setClass(this, WeaveBookmarksListActivity.class);

			spec = tabHost.newTabSpec("weave").setIndicator(res.getString(R.string.WeaveBookmarksListActivity_Title),
					res.getDrawable(R.drawable.ic_tab_weave))
					.setContent(intent);
			tabHost.addTab(spec);
		}
		
		tabHost.setCurrentTab(0);
		
		tabHost.setOnTabChangedListener(new OnTabChangeListener() {			
			@Override
			public void onTabChanged(String tabId) {
				if (tabId.equals("bookmarks")) {
					setTitle(R.string.BookmarksListActivity_Title);
				} else if (tabId.equals("history")) {
					setTitle(R.string.HistoryListActivity_Title);
				} else if (tabId.equals("weave")) {
					setTitle(R.string.WeaveBookmarksListActivity_Title);
				} else {
					setTitle(R.string.ApplicationName);
				}
			}
		});
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
}
