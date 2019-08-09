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

package org.zirco.ui.activities;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import org.zirco.controllers.Controller;
import org.zirco.model.adapters.HistoryExpandableListAdapter;
import org.zirco.model.items.HistoryItem;
import org.zirco.providers.BookmarksProviderWrapper;
import org.zirco.ui.components.CustomWebView;
import org.zirco.utils.ApplicationUtils;
import org.zirco.utils.Constants;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CompoundButton;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

/**
 * history list activity.
 */
public class HistoryListActivity extends LocalizedActivities.ExpandableListActivity {
	
	private static final int MENU_CLEAR_HISTORY = Menu.FIRST;
	
	private static final int MENU_OPEN_IN_TAB = Menu.FIRST + 10;
	private static final int MENU_COPY_URL = Menu.FIRST + 11;
	private static final int MENU_SHARE = Menu.FIRST + 12;
	private static final int MENU_DELETE_FROM_HISTORY = Menu.FIRST + 13;
	
	private ExpandableListAdapter mAdapter;
	
	private ProgressDialog mProgressDialog;
	
	private OnCheckedChangeListener mBookmarkStarChangeListener;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(R.string.HistoryListActivity_Title);
        
        registerForContextMenu(getExpandableListView());
        
        mBookmarkStarChangeListener = new OnCheckedChangeListener() {			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {								
				long id = (Long) buttonView.getTag();
				BookmarksProviderWrapper.toggleBookmark(getContentResolver(), id, isChecked);
				
				if (isChecked) {
					Toast.makeText(HistoryListActivity.this, R.string.HistoryListActivity_BookmarkAdded, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(HistoryListActivity.this, R.string.HistoryListActivity_BookmarkRemoved, Toast.LENGTH_SHORT).show();
				}
			}
		};
        
        fillData();
	}

	/**
	 * Fill the history list.
	 */
	private void fillData() {
		Cursor c = BookmarksProviderWrapper.getStockHistory(getContentResolver());
		
		mAdapter = new HistoryExpandableListAdapter(
				this,
				mBookmarkStarChangeListener,
				c,
				c.getColumnIndex(BookmarksProviderWrapper.BookmarkColumns.DATE),
				ApplicationUtils.getFaviconSizeForBookmarks(this));
		
        setListAdapter(mAdapter);
        
        if (getExpandableListAdapter().getGroupCount() > 0) {
        	getExpandableListView().expandGroup(0);
        }
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		ExpandableListView.ExpandableListContextMenuInfo info =
			(ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

		int type = ExpandableListView.getPackedPositionType(info.packedPosition);
		int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
		int child =	ExpandableListView.getPackedPositionChild(info.packedPosition);
		
		if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
			
			HistoryItem item = (HistoryItem) getExpandableListAdapter().getChild(group, child);
			menu.setHeaderTitle(item.getTitle());
			
			menu.add(0, MENU_OPEN_IN_TAB, 0, R.string.HistoryListActivity_MenuOpenInTab);
			menu.add(0, MENU_COPY_URL, 0, R.string.BookmarksHistoryActivity_MenuCopyLinkUrl);
			menu.add(0, MENU_SHARE, 0, R.string.Main_MenuShareLinkUrl);
			menu.add(0, MENU_DELETE_FROM_HISTORY, 0, R.string.HistoryListActivity_MenuDelete);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem menuItem) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuItem.getMenuInfo();
		
		int type = ExpandableListView.getPackedPositionType(info.packedPosition);
		
		if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
			int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
			int child =	ExpandableListView.getPackedPositionChild(info.packedPosition);
			
			HistoryItem item = (HistoryItem) getExpandableListAdapter().getChild(group, child);
			
			switch (menuItem.getItemId()) {
			case MENU_OPEN_IN_TAB:
				doNavigateToUrl(item.getUrl(), true);
				break;
			case MENU_COPY_URL:
				ApplicationUtils.copyTextToClipboard(this, item.getUrl(), getString(R.string.Commons_UrlCopyToastMessage));
				break;
			case MENU_SHARE:
				ApplicationUtils.sharePage(this, item.getTitle(), item.getUrl());
				break;
			case MENU_DELETE_FROM_HISTORY:
				BookmarksProviderWrapper.deleteHistoryRecord(getContentResolver(), item.getId());
				fillData();
				break;
			default:
				break;
			}
		}
		
		return super.onContextItemSelected(menuItem);
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
    	MenuItem item;
    	item = menu.add(0, MENU_CLEAR_HISTORY, 0, R.string.Commons_ClearHistory);
        item.setIcon(R.drawable.ic_menu_delete);
        
        return true;
	}
	
	@Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
		
		switch(item.getItemId()) {
		case MENU_CLEAR_HISTORY:
        	clearHistory();
        	return true;
        default: return super.onMenuItemSelected(featureId, item);
		}
	}
	
	@Override
	public boolean onChildClick(ExpandableListView parent, View v,	int groupPosition, int childPosition, long id) {
		HistoryItem item = (HistoryItem) getExpandableListAdapter().getChild(groupPosition, childPosition);
        doNavigateToUrl(item.getUrl(), false);
        
		return super.onChildClick(parent, v, groupPosition, childPosition, id);
	}
	
	/**
	 * Load the given url.
	 * @param url The url.
	 * @param newTab If True, will open a new tab. If False, the current tab is used.
	 */
	private void doNavigateToUrl(String url, boolean newTab) {
		Intent result = new Intent();
        result.putExtra(Constants.EXTRA_ID_NEW_TAB, newTab);
        result.putExtra(Constants.EXTRA_ID_URL,  url);
        
        if (getParent() != null) {
        	getParent().setResult(RESULT_OK, result);
        } else {
        	setResult(RESULT_OK, result);
        }
        finish();
	}

	/**
	 * Clear history.
	 */
	private void doClearHistory() {
    	mProgressDialog = ProgressDialog.show(this,
    			this.getResources().getString(R.string.Commons_PleaseWait),
    			this.getResources().getString(R.string.Commons_ClearingHistory));
    	
    	new HistoryClearer();
    }
	
	/**
	 * Display confirmation and clear history.
	 */
	private void clearHistory() {
		ApplicationUtils.showYesNoDialog(this,
				android.R.drawable.ic_dialog_alert,
				R.string.Commons_ClearHistory,
				R.string.Commons_NoUndoMessage,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
		    			doClearHistory();
					}			
		});
    }
	
	/**
	 * Runnable to clear history.
	 */
	private class HistoryClearer /*implements Runnable*/ {

		/**
		 * Constructor.
		 */
		public HistoryClearer() {
			//new Thread(this).start();
			// Newer versions of Android (ie. KitKat) throw this exception:
			// java.lang.RuntimeException: java.lang.Throwable:
			// A WebView method was called on thread 'Thread-xxxxx'. All WebView methods must be called on the same thread.
			this.run();
		}

		//@Override
		public void run() {
			BookmarksProviderWrapper.clearHistoryAndOrBookmarks(getContentResolver(), true, false);
			
			for (CustomWebView webView : Controller.getInstance().getWebViewList()) {
				webView.clearHistory();
			}
			
			handler.sendEmptyMessage(0);
		}

		private Handler handler = new Handler() {
			public void handleMessage(Message msg) {
				mProgressDialog.dismiss();
				fillData();
			}
		};
	}
	
}
