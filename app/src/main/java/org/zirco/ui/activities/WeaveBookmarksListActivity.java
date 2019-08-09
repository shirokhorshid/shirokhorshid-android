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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.psiphon3.psiphonlibrary.LocalizedActivities;
import org.emergent.android.weave.client.WeaveAccountInfo;
import com.psiphon3.R;
import org.zirco.model.DbAdapter;
import org.zirco.model.adapters.WeaveBookmarksCursorAdapter;
import org.zirco.model.items.WeaveBookmarkItem;
import org.zirco.providers.BookmarksProviderWrapper;
import org.zirco.providers.WeaveColumns;
import org.zirco.sync.ISyncListener;
import org.zirco.sync.WeaveSyncTask;
import org.zirco.ui.activities.preferences.WeavePreferencesActivity;
import org.zirco.utils.ApplicationUtils;
import org.zirco.utils.Constants;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class WeaveBookmarksListActivity extends LocalizedActivities.Activity implements ISyncListener {
	
	private static final int MENU_SYNC = Menu.FIRST;
	private static final int MENU_CLEAR = Menu.FIRST + 1;
	
	private static final int MENU_OPEN_IN_TAB = Menu.FIRST + 10;
    private static final int MENU_COPY_URL = Menu.FIRST + 11;
    private static final int MENU_SHARE = Menu.FIRST + 12;
	
	private static final String ROOT_FOLDER = "places";
	
	private LinearLayout mNavigationView;
	private TextView mNavigationText;
	private ImageButton mNavigationBack;
	private ListView mListView;
	
	private Button mSetupButton;
	private Button mSyncButton;	
	
	private View mEmptyView;
	private View mEmptyFolderView;
	
	private List<WeaveBookmarkItem> mNavigationList;
	
	private ProgressDialog mProgressDialog;
	
	private DbAdapter mDbAdapter;
	private Cursor mCursor = null;
	
	private WeaveSyncTask mSyncTask;
	
	private static final AtomicReference<AsyncTask<WeaveAccountInfo, Integer, Throwable>> mSyncThread =
	      new AtomicReference<AsyncTask<WeaveAccountInfo, Integer, Throwable>>();
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.weave_bookmarks_list_activity);
        
        mNavigationView = (LinearLayout) findViewById(R.id.WeaveBookmarksNavigationView);
        mNavigationText = (TextView) findViewById(R.id.WeaveBookmarksNavigationText);
        mNavigationBack = (ImageButton) findViewById(R.id.WeaveBookmarksNavigationBack);
        mListView = (ListView) findViewById(R.id.WeaveBookmarksList);
        
        mNavigationBack.setOnClickListener(new OnClickListener() {		
			@Override
			public void onClick(View v) {
				doNavigationBack();	
			}
		});
        
        mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				WeaveBookmarkItem selectedItem = BookmarksProviderWrapper.getWeaveBookmarkById(getContentResolver(), id);

				if (selectedItem != null) {
					if (selectedItem.isFolder()) {		
						mNavigationList.add(selectedItem);
						fillData();			
					} else {		
						String url = selectedItem.getUrl();
						
						if (url != null) {				
							Intent result = new Intent();
							result.putExtra(Constants.EXTRA_ID_NEW_TAB, false);
							result.putExtra(Constants.EXTRA_ID_URL, url);

							if (getParent() != null) {
				            	getParent().setResult(RESULT_OK, result);
				            } else {
				            	setResult(RESULT_OK, result);            
				            }
							
							finish();
						}
					}
				}
			}
        });
        
        mEmptyView = findViewById(R.id.WeaveBookmarksEmptyView);
        mEmptyFolderView = findViewById(R.id.WeaveBookmarksEmptyFolderView);
        
        //mListView.setEmptyView(mEmptyView);
        
        mSetupButton = (Button) findViewById(R.id.WeaveBookmarksEmptyViewSetupButton);
        mSetupButton.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				startActivity(new Intent(WeaveBookmarksListActivity.this, WeavePreferencesActivity.class));
			}
		});
        
        mSyncButton = (Button) findViewById(R.id.WeaveBookmarksEmptyViewSyncButton);
        mSyncButton.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				doSync();
			}
		});
        
        mNavigationList = new ArrayList<WeaveBookmarkItem>();
        mNavigationList.add(new WeaveBookmarkItem(getResources().getString(R.string.WeaveBookmarksListActivity_WeaveRootFolder), null, ROOT_FOLDER, true));
        
        mDbAdapter = new DbAdapter(this);
        mDbAdapter.open();
        
        registerForContextMenu(mListView);
        
        fillData();
	}
	
	@Override
	protected void onDestroy() {
		if (mCursor != null) {
			mCursor.close();
		}
		mDbAdapter.close();		
		super.onDestroy();
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
    	MenuItem item = menu.add(0, MENU_SYNC, 0, R.string.WeaveBookmarksListActivity_MenuSync);
    	item.setIcon(R.drawable.ic_menu_sync);
    	
    	item = menu.add(0, MENU_CLEAR, 0, R.string.WeaveBookmarksListActivity_MenuClear);
    	item.setIcon(R.drawable.ic_menu_delete);
    	
    	return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {

		switch(item.getItemId()) {
		case MENU_SYNC:
			doSync();
			return true;
		case MENU_CLEAR:
			doClear();
			return true;
		default: return super.onMenuItemSelected(featureId, item);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		long id = ((AdapterContextMenuInfo) menuInfo).id;
		if (id != -1) {
			WeaveBookmarkItem item = BookmarksProviderWrapper.getWeaveBookmarkById(getContentResolver(), id);
			if (!item.isFolder()) {
				menu.setHeaderTitle(item.getTitle());
				
				menu.add(0, MENU_OPEN_IN_TAB, 0, R.string.BookmarksListActivity_MenuOpenInTab);
				menu.add(0, MENU_COPY_URL, 0, R.string.BookmarksHistoryActivity_MenuCopyLinkUrl);
				menu.add(0, MENU_SHARE, 0, R.string.Main_MenuShareLinkUrl);
			}
		}				
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		WeaveBookmarkItem bookmarkItem = BookmarksProviderWrapper.getWeaveBookmarkById(getContentResolver(), info.id);
		
		switch (item.getItemId()) {
		case MENU_OPEN_IN_TAB:    	
            Intent i = new Intent();
            i.putExtra(Constants.EXTRA_ID_NEW_TAB, true);
            i.putExtra(Constants.EXTRA_ID_URL, bookmarkItem.getUrl());
            
            if (getParent() != null) {
            	getParent().setResult(RESULT_OK, i);
            } else {
            	setResult(RESULT_OK, i);            
            }
            
            finish();
            return true;
            
		case MENU_COPY_URL:			
    		ApplicationUtils.copyTextToClipboard(this,  bookmarkItem.getUrl(), getString(R.string.Commons_UrlCopyToastMessage));
    		return true;
    		
		case MENU_SHARE:
			ApplicationUtils.sharePage(this, bookmarkItem.getTitle(), bookmarkItem.getUrl());
			return true;
    		
		default: return super.onContextItemSelected(item);
		}
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mNavigationList.size() > 1) {
				doNavigationBack();
				return true;
			} else {
				return super.onKeyUp(keyCode, event);
			}
		default: return super.onKeyUp(keyCode, event);
		}
	}
	
	/**
	 * Set the list loading animation.
	 */
    private void setAnimation() {
    	AnimationSet set = new AnimationSet(true);

        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        animation.setDuration(75);
        set.addAnimation(animation);

        animation = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f
        );
        animation.setDuration(50);
        set.addAnimation(animation);

        LayoutAnimationController controller =
                new LayoutAnimationController(set, 0.5f);

        mListView.setLayoutAnimation(controller);
    }
	
	private void doSync() {
		String authToken = ApplicationUtils.getWeaveAuthToken(this);
		
		if (authToken != null) {
			WeaveAccountInfo info = WeaveAccountInfo.createWeaveAccountInfo(authToken);
			mSyncTask = new WeaveSyncTask(this, this);
			
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setTitle(R.string.WeaveSync_SyncTitle);
			mProgressDialog.setMessage(getString(R.string.WeaveSync_Connecting));
			mProgressDialog.setCancelable(true);
			mProgressDialog.setOnCancelListener(new OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					mSyncTask.cancel(true);
				}
			});
			
			mProgressDialog.show();
			
			boolean retVal = mSyncThread.compareAndSet(null, mSyncTask);
			if (retVal) {
				mSyncTask.execute(info);
			}
			
			
		} else {
			ApplicationUtils.showErrorDialog(this, R.string.Errors_WeaveSyncFailedTitle, R.string.Errors_WeaveAuthFailedMessage);
		}
		
	}
	
	private void doClear() {
		mProgressDialog = ProgressDialog.show(this,
    			this.getResources().getString(R.string.Commons_PleaseWait),
    			this.getResources().getString(R.string.Commons_ClearingBookmarks));
		
		new Clearer();
		
		// Reset last sync date.
		Editor lastSyncDateEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		lastSyncDateEditor.putLong(Constants.PREFERENCE_WEAVE_LAST_SYNC_DATE, -1);
		lastSyncDateEditor.commit();
	}
	
	private void doNavigationBack() {
		mNavigationList.remove(mNavigationList.size() - 1);
		if (mNavigationList.size() == 0) {
			mNavigationList.add(new WeaveBookmarkItem(getResources().getString(R.string.WeaveBookmarksListActivity_WeaveRootFolder), null, ROOT_FOLDER, true));
		}
		
		fillData();
	}
	
	private void fillData() {
		
		String[] from = { WeaveColumns.WEAVE_BOOKMARKS_TITLE, WeaveColumns.WEAVE_BOOKMARKS_URL };
		int[] to = { R.id.BookmarkRow_Title, R.id.BookmarkRow_Url };
		
		mCursor = BookmarksProviderWrapper.getWeaveBookmarksByParentId(getContentResolver(), mNavigationList.get(mNavigationList.size() - 1).getWeaveId());
		
		ListAdapter adapter = new WeaveBookmarksCursorAdapter(this,
				R.layout.weave_bookmark_row,
				mCursor,
				from,
				to);	
		
		if (adapter.isEmpty() &&
				(mNavigationList.size() <= 1)) {
			mNavigationView.setVisibility(View.GONE);			
		} else {
			mNavigationView.setVisibility(View.VISIBLE);			
		}		
		
		if (mNavigationList.size() > 1) {
			mNavigationBack.setEnabled(true);
			mListView.setEmptyView(mEmptyFolderView);
		} else {
			mNavigationBack.setEnabled(false);
			mListView.setEmptyView(mEmptyView);
		}
		
		mListView.setAdapter(adapter);		
		
		setAnimation();
		
		mNavigationText.setText(getNavigationText());				
	}
	
	private String getNavigationText() {
		StringBuilder sb = new StringBuilder();
		
		for (WeaveBookmarkItem navigationItem : mNavigationList) {
			if (sb.length() != 0) {
				sb.append(" > ");
			}
			
			sb.append(navigationItem.getTitle());
		}
		
		return sb.toString();
	}

	@Override
	public void onSyncCancelled() {
		mSyncThread.compareAndSet(mSyncTask, null);
		mProgressDialog.dismiss();	
		fillData();
		
		if (mSyncTask.isFullSync()) {
			// Reset last sync date is this was a full sync.
			Editor lastSyncDateEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			lastSyncDateEditor.putLong(Constants.PREFERENCE_WEAVE_LAST_SYNC_DATE, -1);
			lastSyncDateEditor.commit();
		}
	}

	@Override
	public void onSyncEnd(Throwable result) {
		mSyncThread.compareAndSet(mSyncTask, null);
		if (result != null) {
			String msg = String.format(getResources().getString(R.string.Errors_WeaveSyncFailedMessage), result.getMessage());
			Log.e("MainActivity: Sync failed.", msg);
			
			ApplicationUtils.showErrorDialog(this, R.string.Errors_WeaveSyncFailedTitle, msg);
		} else {
			Editor lastSyncDateEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			lastSyncDateEditor.putLong(Constants.PREFERENCE_WEAVE_LAST_SYNC_DATE, new Date().getTime());
			lastSyncDateEditor.commit();
		}
		
		mProgressDialog.dismiss();
		fillData();
	}

	@Override
	public void onSyncProgress(int step, int done, int total) {
		switch (step) {
		case 0:
			mProgressDialog.setMessage(getString(R.string.WeaveSync_Connecting));
			break;
		case 1:
			mProgressDialog.setMessage(getString(R.string.WeaveSync_GettingData));
			break;
		case 2:
			mProgressDialog.setMessage(String.format(getString(R.string.WeaveSync_ReadingData), done, total));
			break;
		case 3:
			mProgressDialog.setMessage(getString(R.string.WeaveSync_WrittingData));
			break;
		}
	}
	
	private class Clearer implements Runnable {

		public Clearer() {
			new Thread(this).start();
		}
		
		@Override
		public void run() {
			BookmarksProviderWrapper.clearWeaveBookmarks(getContentResolver());
			
			mHandler.sendEmptyMessage(0);
		}
		
		private Handler mHandler = new Handler() {
			public void handleMessage(Message msg) {				
				mProgressDialog.dismiss();
				fillData();
			}
		};
		
	}

}
