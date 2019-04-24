/*
 * Zirco Browser for Android
 * 
 * Copyright (C) 2010 - 2012 J. Devauchelle and contributors.
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebIconDatabase;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter.CursorToStringConverter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.psiphon3.FeedbackActivity;
import com.psiphon3.R;
import com.psiphon3.StatusActivity;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import com.psiphon3.psiphonlibrary.TunnelService;

import net.grandcentrix.tray.AppPreferences;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.greendroid.QuickAction;
import org.greendroid.QuickActionGrid;
import org.greendroid.QuickActionWidget;
import org.greendroid.QuickActionWidget.OnQuickActionClickListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.zirco.controllers.Controller;
import org.zirco.events.EventConstants;
import org.zirco.events.EventController;
import org.zirco.events.IDownloadEventsListener;
import org.zirco.model.adapters.UrlSuggestionCursorAdapter;
import org.zirco.model.items.DownloadItem;
import org.zirco.providers.BookmarksProviderWrapper;
import org.zirco.ui.activities.preferences.PreferencesActivity;
import org.zirco.ui.components.CustomWebView;
import org.zirco.ui.components.CustomWebViewClient;
import org.zirco.ui.runnables.FaviconUpdaterRunnable;
import org.zirco.ui.runnables.HideToolbarsRunnable;
import org.zirco.ui.runnables.HistoryUpdater;
import org.zirco.utils.AnimationManager;
import org.zirco.utils.ApplicationUtils;
import org.zirco.utils.Constants;
import org.zirco.utils.UrlUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The application main activity.
 */
public class MainActivity extends LocalizedActivities.Activity implements IToolbarsContainer, OnTouchListener, IDownloadEventsListener {
	
	public static MainActivity INSTANCE = null;
	
	private static final int FLIP_PIXEL_THRESHOLD = 200;
	private static final int FLIP_TIME_THRESHOLD = 400;
	
    private static final int MENU_FEEDBACK = Menu.FIRST;
    private static final int MENU_ADD_BOOKMARK = Menu.FIRST + 1;
	private static final int MENU_SHOW_BOOKMARKS = Menu.FIRST + 2;
	private static final int MENU_SHOW_DOWNLOADS = Menu.FIRST + 3;	
	private static final int MENU_PREFERENCES = Menu.FIRST + 4;
	private static final int MENU_EXIT = Menu.FIRST + 5;
	
	private static final int CONTEXT_MENU_OPEN = Menu.FIRST + 10;
	private static final int CONTEXT_MENU_OPEN_IN_NEW_TAB = Menu.FIRST + 11;
	private static final int CONTEXT_MENU_DOWNLOAD = Menu.FIRST + 12;
	private static final int CONTEXT_MENU_COPY = Menu.FIRST + 13;
	private static final int CONTEXT_MENU_SEND_MAIL = Menu.FIRST + 14;
	private static final int CONTEXT_MENU_SHARE = Menu.FIRST + 15;
	
	private static final int OPEN_BOOKMARKS_HISTORY_ACTIVITY = 0;
	private static final int OPEN_DOWNLOADS_ACTIVITY = 1;
	private static final int OPEN_FILE_CHOOSER_ACTIVITY = 2;
	
	protected static final FrameLayout.LayoutParams COVER_SCREEN_PARAMS =
	        new FrameLayout.LayoutParams(
	        ViewGroup.LayoutParams.MATCH_PARENT,
	        ViewGroup.LayoutParams.MATCH_PARENT);
	
	protected LayoutInflater mInflater = null;
	
	private LinearLayout mTopBar;
	private LinearLayout mBottomBar;
	
	private LinearLayout mFindBar;
	
	private ImageButton mFindPreviousButton;
	private ImageButton mFindNextButton;
	private ImageButton mFindCloseButton;
	
	private EditText mFindText;
	
	private ImageView mPreviousTabView;
	private ImageView mNextTabView;
	
	private ImageButton mToolsButton;
	private AutoCompleteTextView mUrlEditText;
	private ImageButton mGoButton;	
	private ProgressBar mProgressBar;	
	
	private ImageView mBubbleRightView;
	private ImageView mBubbleLeftView;
	
	private CustomWebView mCurrentWebView;
	private List<CustomWebView> mWebViews;
	
	private ImageButton mPreviousButton;
	private ImageButton mNextButton;
	
	private ImageButton mNewTabButton;
	private ImageButton mRemoveTabButton;
	
	private ImageButton mQuickButton;
	
	private Drawable mCircularProgress;
	
	private boolean mUrlBarVisible;
	private boolean mToolsActionGridVisible = false;
	private boolean mFindDialogVisible = false;
	
	private TextWatcher mUrlTextWatcher;
	
	private HideToolbarsRunnable mHideToolbarsRunnable;
	
	private ViewFlipper mViewFlipper;
	
	private GestureDetector mGestureDetector;
	
	private SwitchTabsMethod mSwitchTabsMethod = SwitchTabsMethod.BOTH;
	
	private QuickActionGrid mToolsActionGrid;
	
	private ValueCallback<Uri> mUploadMessage;
	
	private OnSharedPreferenceChangeListener mPreferenceChangeListener;
	
	private View mCustomView;
	private Bitmap mDefaultVideoPoster = null;
	private View mVideoProgressView = null;
	
	private FrameLayout mFullscreenContainer;	
	
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    
    private int mOriginalOrientation;
	
	private enum SwitchTabsMethod {
		BUTTONS,
		FLING,
		BOTH
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);              

        Intent intent = getIntent();

        INSTANCE = this;
        
        Constants.initializeConstantsFromResources(this);
        
        Controller.getInstance().setPreferences(PreferenceManager.getDefaultSharedPreferences(this));    
        
        if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_SHOW_FULL_SCREEN, false)) {        	
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        
        // PSIPHON: always show the title bar
        //if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_GENERAL_HIDE_TITLE_BARS, true)) {
        //	requestWindowFeature(Window.FEATURE_NO_TITLE); 
        //}

        setProgressBarVisibility(true);
        
        setContentView(R.layout.zirco_main);                        
        
        mCircularProgress = getResources().getDrawable(R.drawable.spinner);
        
        EventController.getInstance().addDownloadListener(this);                
                
        mHideToolbarsRunnable = null;
        
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        buildComponents();                
        
        mViewFlipper.removeAllViews();   
        
        updateSwitchTabsMethod();

        // PSIPHON: make sure the service is running
        if (!ensurePsiphonRunning())
        {
            // Must return before trying to open tabs and whatnot.
            // Control switching to Psiphon task.
            return;
        }
        
        // PSIPHON: always use internal bookmarks
        //updateBookmarksDatabaseSource();
        //
        //registerPreferenceChangeListener();
        
        //
        // Psiphon: Restore browser tabs from last session
        //
        if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_BROWSER_RESTORE_LAST_PAGE, true))
        {
            try
            {
                JSONArray jsonURLs = new JSONArray(Controller.getInstance().getPreferences().getString("urlsToRestore", "[]"));
                for (int i = 0; i < jsonURLs.length(); i++)
                {
                    String url = jsonURLs.getString(i);
                    if (url == null || url.length() == 0) continue;
                    addTab(false);
                    navigateToUrl(url);
                }
            } 
            catch (JSONException eJSON)
            {
                // Do nothing
            }
            
        }
        // end Psiphon changes
        
        // PSIPHON: open home pages if they're not already open (or a blank tab if none)
        ArrayList<String> homePages = intent.getStringArrayListExtra("homePages");
        
        if (intent.getData() != null)
        {
            homePages.add(intent.getDataString());
        }
        
        for (String homePage : homePages)
        {
            boolean urlAlreadyOpen = false;
            
            // If we already have tabs open, check to make sure the homePages 
            // won't create duplicate tabs.
            if (mWebViews.size() > 0)
            {
                // Many home pages redirect to another URL, so to determine if
                // an open tab really matches a home page, we'll need to collect
                // that home page's redirect URLs.
                List<String> homePageEquivs = findEquivalentUrls(
                        mWebViews.get(0).getSettings().getUserAgentString(),
                        homePage);
                
                for (CustomWebView webView : mWebViews)
                {
                    String webViewUrl = webView.getUrl();
                    if (webViewUrl == null || webViewUrl.length() == 0)
                    {
                        webViewUrl = webView.getLoadedUrl();
                    }
                    
                    if (webViewUrl == null) continue;
                    
                    for (String homePageEquiv : homePageEquivs)
                    {
                        if (homePageEquiv.compareToIgnoreCase(webViewUrl) == 0)
                        {
                            urlAlreadyOpen = true;
                            break;
                        }
                    }
                    
                    if (urlAlreadyOpen) break;
                }
            }
            
            if (!urlAlreadyOpen)
            {
            	addTab(false);
            	navigateToUrl(homePage);
            }
        }
        if (mWebViews.size() == 0)
        {
            addTab(true);
        }
        
        // PSIPHON: don't show Zirco changelist or restore last page
        // The current intent data Uri is also deliberately ignored
        //
        /*
        if (intent.getData() != null) {
        	// App first launch from another app.
        	addTab(false);
        	navigateToUrl(intent.getDataString());
        } else {
        	// Normal start.
        	int currentVersionCode = ApplicationUtils.getApplicationVersionCode(this);
        	int savedVersionCode = PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREFERENCES_LAST_VERSION_CODE, -1);
        	
        	// If currentVersionCode and savedVersionCode are different, the application has been updated.
        	if (currentVersionCode != savedVersionCode) {
        		// Save current version code.
        		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            	editor.putInt(Constants.PREFERENCES_LAST_VERSION_CODE, currentVersionCode);
            	editor.commit();
            	
            	// Display changelog dialog.
            	Intent changelogIntent = new Intent(this, ChangelogActivity.class);
        		startActivity(changelogIntent);
        	}
        	
        	boolean lastPageRestored = false;
        	if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREFERENCES_BROWSER_RESTORE_LAST_PAGE, true)) {
        		if (savedInstanceState != null) {        		
        			String savedUrl = savedInstanceState.getString(Constants.EXTRA_SAVED_URL);
        			if (savedUrl != null) {
        				addTab(false);
        				navigateToUrl(savedUrl);
        				lastPageRestored = true;
        			}
        		}
        	}
        	
        	if (!lastPageRestored) {
        		addTab(true);
        	}
        }
        */
        
        initializeWebIconDatabase();
        
        startToolbarsHideRunnable();
    }
    
    // PSIPHON
    // TODO: Update to ensure Psiphon is *connected*, not just that the service
    // is running.
    /**
     * Check that Psiphon is running. If it isn't, launch it and close this task. 
     * @return  true if Psiphon is running, false otherwise. If false, the caller
     *          should avoid doing significant work (e.g., just return).
     */
    private boolean ensurePsiphonRunning()
    {
        // See com.psiphon3.StatusActivity#isServiceRunning() for details.
        ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.uid == android.os.Process.myUid() &&
                    TunnelService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }


        // The Psiphon service isn't running. Punt back to the Psiphon status activity.
        Intent intent = new Intent(
                "ACTION_VIEW",
                null,
                this,
                StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(intent);

        this.finish();

        return false;
    }

    /**
     * Initialize the Web icons database.
     */
    private void initializeWebIconDatabase() {
        
    	final WebIconDatabase db = WebIconDatabase.getInstance();
    	db.open(getDir("icons", 0).getPath());   
    }

    @Override
	protected void onDestroy() {
		WebIconDatabase.getInstance().close();
		
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREFERENCES_PRIVACY_CLEAR_CACHE_ON_EXIT, false)) {
			mCurrentWebView.clearCache(true);
		}
		
		EventController.getInstance().removeDownloadListener(this);
		
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);

		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(Constants.EXTRA_SAVED_URL, mCurrentWebView.getUrl());
		super.onSaveInstanceState(outState);
	}

	/**
     * Handle url request from external apps.
     * @param intent The intent.
     */
    @Override
	protected void onNewIntent(Intent intent) {

    	// PSIPHON: this will ignore "homePages" extra, by design
    	
    	if (intent.getData() != null) {
    		addTab(false);
    		navigateToUrl(intent.getDataString());
    	}
		
		setIntent(intent);
		
		super.onNewIntent(intent);
	}        
    
    /**
     * Restart the application.
     */
    public void restartApplication() {
    	PendingIntent intent = PendingIntent.getActivity(this.getBaseContext(), 0, new Intent(getIntent()), getIntent().getFlags());
		AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, intent);
		System.exit(2);
    }
    
    /**
     * Create main UI.
     */
	private void buildComponents() {
		
		mToolsActionGrid = new QuickActionGrid(this);
		mToolsActionGrid.addQuickAction(new QuickAction(this, R.drawable.ic_btn_home, R.string.QuickAction_Home));
		mToolsActionGrid.addQuickAction(new QuickAction(this, R.drawable.ic_btn_share, R.string.QuickAction_Share));
		mToolsActionGrid.addQuickAction(new QuickAction(this, R.drawable.ic_btn_find, R.string.QuickAction_Find));
		mToolsActionGrid.addQuickAction(new QuickAction(this, R.drawable.ic_btn_select, R.string.QuickAction_SelectText));
		mToolsActionGrid.addQuickAction(new QuickAction(this, R.drawable.ic_btn_mobile_view, R.string.QuickAction_MobileView));
				
		mToolsActionGrid.setOnQuickActionClickListener(new OnQuickActionClickListener() {			
			@Override
			public void onQuickActionClicked(QuickActionWidget widget, int position) {
				switch (position) {
				case 0:
					navigateToHome();
					break;
				case 1:
					ApplicationUtils.sharePage(MainActivity.this, mCurrentWebView.getTitle(), mCurrentWebView.getUrl());
					break;
				case 2:					
					// Somewhat dirty hack: when the find dialog was shown from a QuickAction,
					// the soft keyboard did not show... Hack is to wait a little before showing
					// the file dialog through a thread.
					startShowFindDialogRunnable();
					break;
				case 3:
					swithToSelectAndCopyTextMode();
					break;
				case 4:
					String currentUrl = mUrlEditText.getText().toString();
		    		
		    		// Do not reload mobile view if already on it.
		    		if (!currentUrl.startsWith(Constants.URL_GOOGLE_MOBILE_VIEW_NO_FORMAT)) {
		    			String url = String.format(Constants.URL_GOOGLE_MOBILE_VIEW, mUrlEditText.getText().toString());
		    			navigateToUrl(url);
		    		}
		    		break;				
				}
			}
		});
				
		mToolsActionGrid.setOnDismissListener(new PopupWindow.OnDismissListener() {			
			@Override
			public void onDismiss() {
				mToolsActionGridVisible = false;
				startToolbarsHideRunnable();
			}
		});
		
		mGestureDetector = new GestureDetector(this, new GestureListener());
    	
    	mUrlBarVisible = true;
    	
    	mWebViews = new ArrayList<CustomWebView>();
    	Controller.getInstance().setWebViewList(mWebViews);
    	
    	mBubbleRightView = (ImageView) findViewById(R.id.BubbleRightView);
    	mBubbleRightView.setOnClickListener(new View.OnClickListener() {
    		@Override
			public void onClick(View v) {
				setToolbarsVisibility(true);				
			}
		});    	
    	mBubbleRightView.setVisibility(View.GONE);
    	
    	mBubbleLeftView = (ImageView) findViewById(R.id.BubbleLeftView);
    	mBubbleLeftView.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				setToolbarsVisibility(true);
			}
		});
    	mBubbleLeftView.setVisibility(View.GONE);
    	
    	mViewFlipper = (ViewFlipper) findViewById(R.id.ViewFlipper);
    	
    	mTopBar = (LinearLayout) findViewById(R.id.BarLayout);    	
    	mTopBar.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				// Dummy event to steel it from the WebView, in case of clicking between the buttons.				
			}
		});
    	
    	mBottomBar = (LinearLayout) findViewById(R.id.BottomBarLayout);    	
    	mBottomBar.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				// Dummy event to steel it from the WebView, in case of clicking between the buttons.				
			}
		});
    	
    	mFindBar = (LinearLayout) findViewById(R.id.findControls);
    	mFindBar.setVisibility(View.GONE);
    	
    	mPreviousTabView = (ImageView) findViewById(R.id.PreviousTabView);
    	mPreviousTabView.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				showPreviousTab(true);
			}
		});
    	mPreviousTabView.setVisibility(View.GONE);
    	
    	mNextTabView = (ImageView) findViewById(R.id.NextTabView);
    	mNextTabView.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				showNextTab(true);
			}
		});
    	mNextTabView.setVisibility(View.GONE);
    	
    	String[] from = new String[] {UrlSuggestionCursorAdapter.URL_SUGGESTION_TITLE, UrlSuggestionCursorAdapter.URL_SUGGESTION_URL};
    	int[] to = new int[] {R.id.AutocompleteTitle, R.id.AutocompleteUrl};
    	
    	UrlSuggestionCursorAdapter adapter = new UrlSuggestionCursorAdapter(this, R.layout.url_autocomplete_line, null, from, to);
    	      	
    	adapter.setCursorToStringConverter(new CursorToStringConverter() {			
			@Override
			public CharSequence convertToString(Cursor cursor) {
				String aColumnString = cursor.getString(cursor.getColumnIndex(UrlSuggestionCursorAdapter.URL_SUGGESTION_URL));
                return aColumnString;
			}
		});
    	
    	adapter.setFilterQueryProvider(new FilterQueryProvider() {		
			@Override
			public Cursor runQuery(CharSequence constraint) {
				if ((constraint != null) &&
						(constraint.length() > 0)) {
					return BookmarksProviderWrapper.getUrlSuggestions(getContentResolver(),
							constraint.toString(),
							PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean(Constants.PREFERENCE_USE_WEAVE, false));
				} else {
					return BookmarksProviderWrapper.getUrlSuggestions(getContentResolver(),
							null,
							PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean(Constants.PREFERENCE_USE_WEAVE, false));
				}
			}
		});
    	
    	mUrlEditText = (AutoCompleteTextView) findViewById(R.id.UrlText);
    	mUrlEditText.setThreshold(1);
    	mUrlEditText.setAdapter(adapter);    
    	mUrlEditText.setSelectAllOnFocus(true);
    	
    	mUrlEditText.setOnKeyListener(new View.OnKeyListener() {

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {												
				if (keyCode == KeyEvent.KEYCODE_ENTER) {
					navigateToUrl();
					return true;
				}
				
				return false;
			}
    		
    	});
    	

    	mUrlTextWatcher = new TextWatcher() {			
    		@Override
    		public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }

    		@Override
    		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }

    		@Override
    		public void afterTextChanged(Editable arg0) {
    			updateGoButton();
    		}
    	};
    	
    	mUrlEditText.addTextChangedListener(mUrlTextWatcher);
    	
    	mUrlEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {

    		@Override
    		public void onFocusChange(View v, boolean hasFocus) {
    			// Select all when focus gained.
    			if (hasFocus) {
    				mUrlEditText.setSelection(0, mUrlEditText.getText().length());
    			}
    		}
    	});    	
    	
    	mUrlEditText.setCompoundDrawablePadding(5);
    	    	
    	mGoButton = (ImageButton) findViewById(R.id.GoBtn);    	
    	mGoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	
            	if (mCurrentWebView.isLoading()) {
            		mCurrentWebView.stopLoading();
            	} else if (!mCurrentWebView.isSameUrl(mUrlEditText.getText().toString())) {
            		navigateToUrl();
            	} else {
            		mCurrentWebView.reload();
            	}
            }          
        });
    	
    	mToolsButton = (ImageButton) findViewById(R.id.ToolsBtn);
    	mToolsButton.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				mToolsActionGridVisible = true;
				mToolsActionGrid.show(v);				
			}
		});
    	
    	mProgressBar = (ProgressBar) findViewById(R.id.WebViewProgress);
    	mProgressBar.setMax(100);
    	
    	mPreviousButton = (ImageButton) findViewById(R.id.PreviousBtn);
    	mNextButton = (ImageButton) findViewById(R.id.NextBtn);
    	
    	mPreviousButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	navigatePrevious();
            }          
        });
		
		mNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	navigateNext();
            }          
        });
    	
		mNewTabButton = (ImageButton) findViewById(R.id.NewTabBtn);
		mNewTabButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	addTab(true);
            }          
        });
		
		mRemoveTabButton = (ImageButton) findViewById(R.id.RemoveTabBtn);
		mRemoveTabButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
            	if (mViewFlipper.getChildCount() == 1 && !mCurrentWebView.getUrl().equals(Constants.URL_ABOUT_START)) {
            		navigateToHome();
                	updateUI();
                	updatePreviousNextTabViewsVisibility();
            	}
            	else
            		removeCurrentTab();
            }          
        });
		
		mQuickButton = (ImageButton) findViewById(R.id.QuickBtn);
		mQuickButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {            	
            	onQuickButton();
            }          
        });
		
		mFindPreviousButton = (ImageButton) findViewById(R.id.find_previous);
		mFindPreviousButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mCurrentWebView.findNext(false);
				hideKeyboardFromFindDialog();
			}
		});
		
		mFindNextButton = (ImageButton) findViewById(R.id.find_next);
		mFindNextButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mCurrentWebView.findNext(true);
				hideKeyboardFromFindDialog();
			}
		});
		
		mFindCloseButton = (ImageButton) findViewById(R.id.find_close);
		mFindCloseButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				closeFindDialog();
			}
		});
		
		mFindText = (EditText) findViewById(R.id.find_value);
		mFindText.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				doFind();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			
			@Override
			public void afterTextChanged(Editable s) { }
		});

    }
    	
	// PSIPHON: always use internal DB for bookmarks
	/*
	private void registerPreferenceChangeListener() {
    	mPreferenceChangeListener = new OnSharedPreferenceChangeListener() {			
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				if (key.equals(Constants.PREFERENCE_BOOKMARKS_DATABASE)) {
					updateBookmarksDatabaseSource();
				}
			}
		};
		
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }
    */
	
    /**
     * Apply preferences to the current UI objects.
     */
    public void applyPreferences() {    	
    	// To update to Bubble position.
    	setToolbarsVisibility(false);
    	
    	updateSwitchTabsMethod();
    	
    	for (CustomWebView view : mWebViews) {
    		view.initializeOptions();
    	}
    }
    
    private void updateSwitchTabsMethod() {
    	String method = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREFERENCES_GENERAL_SWITCH_TABS_METHOD, "buttons");
    	
    	if (method.equals("buttons")) {
    		mSwitchTabsMethod = SwitchTabsMethod.BUTTONS;
    	} else if (method.equals("fling")) {
    		mSwitchTabsMethod = SwitchTabsMethod.FLING;
    	} else if (method.equals("both")) {
    		mSwitchTabsMethod = SwitchTabsMethod.BOTH;
    	} else {
    		mSwitchTabsMethod = SwitchTabsMethod.BUTTONS;
    	}
    }
    
    /*
    private void updateBookmarksDatabaseSource() {
    	String source = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREFERENCE_BOOKMARKS_DATABASE, "INTERNAL");
    	
    	if (source.equals("STOCK")) {
    		BookmarksProviderWrapper.setBookmarksSource(BookmarksSource.STOCK);
    	} else if (source.equals("INTERNAL")) {
    		BookmarksProviderWrapper.setBookmarksSource(BookmarksSource.INTERNAL);
    	}
    }
    */
    
    private void setStatusBarVisibility(boolean visible) {
        int flag = visible ? 0 : WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(flag, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    
    /**
     * Initialize a newly created WebView.
     */
    private void initializeCurrentWebView() {
    	
    	mCurrentWebView.setWebViewClient(new CustomWebViewClient(this));
    	mCurrentWebView.setOnTouchListener(this);
    	
    	mCurrentWebView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {

			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				HitTestResult result = ((WebView) v).getHitTestResult();
				
				if (result == null) 
				{
				    return;
				}
				
				int resultType = result.getType();
				if ((resultType == HitTestResult.ANCHOR_TYPE) ||
						(resultType == HitTestResult.IMAGE_ANCHOR_TYPE) ||
						(resultType == HitTestResult.SRC_ANCHOR_TYPE) ||
						(resultType == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
					
					Intent i = new Intent();
					i.putExtra(Constants.EXTRA_ID_URL, result.getExtra());
					
					MenuItem item = menu.add(0, CONTEXT_MENU_OPEN, 0, R.string.Main_MenuOpen);
					item.setIntent(i);
	
					item = menu.add(0, CONTEXT_MENU_OPEN_IN_NEW_TAB, 0, R.string.Main_MenuOpenNewTab);					
					item.setIntent(i);
					
					item = menu.add(0, CONTEXT_MENU_COPY, 0, R.string.Main_MenuCopyLinkUrl);					
					item.setIntent(i);
					
					item = menu.add(0, CONTEXT_MENU_DOWNLOAD, 0, R.string.Main_MenuDownload);					
					item.setIntent(i);
					
					item = menu.add(0, CONTEXT_MENU_SHARE, 0, R.string.Main_MenuShareLinkUrl);					
					item.setIntent(i);
				
					menu.setHeaderTitle(result.getExtra());					
				} else if (resultType == HitTestResult.IMAGE_TYPE) {
					Intent i = new Intent();
					i.putExtra(Constants.EXTRA_ID_URL, result.getExtra());
					
					MenuItem item = menu.add(0, CONTEXT_MENU_OPEN, 0, R.string.Main_MenuViewImage);					
					item.setIntent(i);
					
					item = menu.add(0, CONTEXT_MENU_COPY, 0, R.string.Main_MenuCopyImageUrl);					
					item.setIntent(i);
					
					item = menu.add(0, CONTEXT_MENU_DOWNLOAD, 0, R.string.Main_MenuDownloadImage);					
					item.setIntent(i);	
					
					item = menu.add(0, CONTEXT_MENU_SHARE, 0, R.string.Main_MenuShareImageUrl);					
					item.setIntent(i);
					
					menu.setHeaderTitle(result.getExtra());
					
				} else if (resultType == HitTestResult.EMAIL_TYPE) {
					
					Intent sendMail = new Intent(Intent.ACTION_VIEW, Uri.parse(WebView.SCHEME_MAILTO + result.getExtra()));
					
					MenuItem item = menu.add(0, CONTEXT_MENU_SEND_MAIL, 0, R.string.Main_MenuSendEmail);					
					item.setIntent(sendMail);										
					
					Intent i = new Intent();
					i.putExtra(Constants.EXTRA_ID_URL, result.getExtra());
					
					item = menu.add(0, CONTEXT_MENU_COPY, 0, R.string.Main_MenuCopyEmailUrl);					
					item.setIntent(i);		
					
					item = menu.add(0, CONTEXT_MENU_SHARE, 0, R.string.Main_MenuShareEmailUrl);					
					item.setIntent(i);
					
					menu.setHeaderTitle(result.getExtra());
				}
			}
    		
    	});  	
		
    	mCurrentWebView.setDownloadListener(new DownloadListener() {

			@Override
			public void onDownloadStart(String url, String userAgent,
					String contentDisposition, String mimetype,
					long contentLength) {
				doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
			}
    		
    	});
    	
		final Activity activity = this;
		mCurrentWebView.setWebChromeClient(new WebChromeClient() {
			
			@SuppressWarnings("unused")
			// This is an undocumented method, it _is_ used, whatever Eclipse may think :)
			// Used to show a file chooser dialog.
			public void openFileChooser(ValueCallback<Uri> uploadMsg) {
				mUploadMessage = uploadMsg;
				Intent i = new Intent(Intent.ACTION_GET_CONTENT);
				i.addCategory(Intent.CATEGORY_OPENABLE);
				i.setType("*/*");
				MainActivity.this.startActivityForResult(
						Intent.createChooser(i, MainActivity.this.getString(R.string.Main_FileChooserPrompt)),
						OPEN_FILE_CHOOSER_ACTIVITY);
			}
			
			@Override
			public Bitmap getDefaultVideoPoster() {
				if (mDefaultVideoPoster == null) {
		            mDefaultVideoPoster = BitmapFactory.decodeResource(MainActivity.this.getResources(), R.drawable.default_video_poster);
		        }
				
		        return mDefaultVideoPoster;
			}

			@Override
			public View getVideoLoadingProgressView() {
				if (mVideoProgressView == null) {
		            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
		            mVideoProgressView = inflater.inflate(R.layout.video_loading_progress, null);
		        }
				
		        return mVideoProgressView;
			}
			
			@Override
			public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
			    super.onShowCustomView(view, callback);
			    showCustomView(view, -1, callback);
			}

			@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            @Override
			public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
			    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			        super.onShowCustomView(view, requestedOrientation, callback);
			    }
			    showCustomView(view, requestedOrientation, callback);
			}
			
			@Override
			public void onHideCustomView() {
				hideCustomView();
			}
			
//			@Override
//			public void onShowCustomView(View view, CustomViewCallback callback) {
//				super.onShowCustomView(view, callback);
//				
//				if (view instanceof FrameLayout) {
//					mCustomViewContainer = (FrameLayout) view;
//		            mCustomViewCallback = callback;
//		            
//		            mContentView = (LinearLayout) findViewById(R.id.MainContainer);
//		            
//		            if (mCustomViewContainer.getFocusedChild() instanceof VideoView) {
//		                mCustomVideoView = (VideoView) mCustomViewContainer.getFocusedChild();
//		                // frame.removeView(video);
//		                mContentView.setVisibility(View.GONE);
//		                mCustomViewContainer.setVisibility(View.VISIBLE);
//		                
//		                setContentView(mCustomViewContainer);
//		                //mCustomViewContainer.bringToFront();
//
//		                mCustomVideoView.setOnCompletionListener(new OnCompletionListener() {							
//							@Override
//							public void onCompletion(MediaPlayer mp) {
//								mp.stop();
//								onHideCustomView();
//							}
//						});
//		                
//		                mCustomVideoView.setOnErrorListener(new OnErrorListener() {						
//							@Override
//							public boolean onError(MediaPlayer mp, int what, int extra) {
//								onHideCustomView();
//								return true;
//							}
//						});
//		                
//		                mCustomVideoView.start();
//		            }
//
//				}
//			}
//
//			@Override
//			public void onHideCustomView() {
//				super.onHideCustomView();
//				
//				if (mCustomVideoView == null) {
//					return;
//				}
//				
//				mCustomVideoView.setVisibility(View.GONE);
//				mCustomViewContainer.removeView(mCustomVideoView);
//				mCustomVideoView = null;
//				
//				mCustomViewContainer.setVisibility(View.GONE);
//		        mCustomViewCallback.onCustomViewHidden();
//		        
//		        mContentView.setVisibility(View.VISIBLE);
//		        setContentView(mContentView);		        
//			}

			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				((CustomWebView) view).setProgress(newProgress);				
				mProgressBar.setProgress(mCurrentWebView.getProgress());
			}
						
			@Override
			public void onReceivedIcon(WebView view, Bitmap icon) {
				new Thread(new FaviconUpdaterRunnable(MainActivity.this, view.getUrl(), view.getOriginalUrl(), icon)).start();
				updateFavIcon();
				
				super.onReceivedIcon(view, icon);
			}

			@Override
			public boolean onCreateWindow(WebView view, final boolean dialog, final boolean userGesture, final Message resultMsg) {
				
				WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
				
				addTab(false, mViewFlipper.getDisplayedChild());
				
				transport.setWebView(mCurrentWebView);
				resultMsg.sendToTarget();
				
				return true;
			}
			
			@Override
			public void onReceivedTitle(WebView view, String title) {
				setTitle(String.format(getResources().getString(R.string.ApplicationNameUrl), title)); 
				
				startHistoryUpdaterRunnable(title, mCurrentWebView.getUrl(), mCurrentWebView.getOriginalUrl());
				
				super.onReceivedTitle(view, title);
			}

			@Override
			public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
				new AlertDialog.Builder(activity)
				.setTitle(R.string.Commons_JavaScriptDialog)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok,
						new AlertDialog.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				})
				.setCancelable(false)
				.create()
				.show();

				return true;
			}

			@Override
			public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
				new AlertDialog.Builder(MainActivity.this)
				.setTitle(R.string.Commons_JavaScriptDialog)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) {
						result.confirm();
					}
				})
				.setNegativeButton(android.R.string.cancel, 
						new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) {
						result.cancel();
					}
				})
				.create()
				.show();

				return true;
			}

			@Override
			public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
				
				final LayoutInflater factory = LayoutInflater.from(MainActivity.this);
                final View v = factory.inflate(R.layout.javascript_prompt_dialog, null);
                ((TextView) v.findViewById(R.id.JavaScriptPromptMessage)).setText(message);
                ((EditText) v.findViewById(R.id.JavaScriptPromptInput)).setText(defaultValue);

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.Commons_JavaScriptDialog)
                    .setView(v)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    String value = ((EditText) v.findViewById(R.id.JavaScriptPromptInput)).getText()
                                            .toString();
                                    result.confirm(value);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    result.cancel();
                                }
                            })
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                public void onCancel(DialogInterface dialog) {
                                    result.cancel();
                                }
                            })
                    .show();
                
                return true;

			}		
			
		});
    }
    
    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard.
     */
    public void swithToSelectAndCopyTextMode() {
        try {
         KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
         shiftPressEvent.dispatch(mCurrentWebView);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    
    /**
     * Initiate a download. Check the SD card and start the download runnable.
     * @param url The url to download.
     * @param userAgent The user agent.
     * @param contentDisposition The content disposition.
     * @param mimetype The mime type.
     * @param contentLength The content length.
     */
    private void doDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
    	    
        if (ApplicationUtils.checkCardState(this, true)) {
        	DownloadItem item = new DownloadItem(this, url);
        	Controller.getInstance().addToDownload(item);
        	item.startDownload();

        	Toast.makeText(this, getString(R.string.Main_DownloadStartedMsg), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Add a new tab.
     * @param navigateToHome If True, will load the user home page.
     */
    private void addTab(boolean navigateToHome) {
    	addTab(navigateToHome, -1);
    }
    
    /**
     * Add a new tab.
     * @param navigateToHome If True, will load the user home page.
     * @param parentIndex The index of the new tab.
     */
    private void addTab(boolean navigateToHome, int parentIndex) {
    	if (mFindDialogVisible) {
    		closeFindDialog();
    	}
    	
    	RelativeLayout view = (RelativeLayout) mInflater.inflate(R.layout.webview, mViewFlipper, false);
    	
    	mCurrentWebView = (CustomWebView) view.findViewById(R.id.webview);
    	
    	initializeCurrentWebView();    			
		
    	synchronized (mViewFlipper) {
    		if (parentIndex != -1) {
    			mWebViews.add(parentIndex + 1, mCurrentWebView);    		
    			mViewFlipper.addView(view, parentIndex + 1);
    		} else {
    			mWebViews.add(mCurrentWebView);
    			mViewFlipper.addView(view);
    		}
    		mViewFlipper.setDisplayedChild(mViewFlipper.indexOfChild(view));    		
    	}
    	
    	updateUI();
    	updatePreviousNextTabViewsVisibility();
    	
    	mUrlEditText.clearFocus();
    	
    	if (navigateToHome) {
    		navigateToHome();
    	}
    }
    
    /**
     * Remove the current tab.
     */
    private void removeCurrentTab() {
    	
    	if (mFindDialogVisible) {
    		closeFindDialog();
    	}
    	
    	int removeIndex = mViewFlipper.getDisplayedChild();
    	
    	mCurrentWebView.doOnPause();
    	
    	synchronized (mViewFlipper) {
    		mViewFlipper.removeViewAt(removeIndex);
    		mViewFlipper.setDisplayedChild(removeIndex - 1);    		
    		mWebViews.remove(removeIndex);    		
    	}
    	
    	mCurrentWebView = mWebViews.get(mViewFlipper.getDisplayedChild());
    	
    	updateUI();
    	updatePreviousNextTabViewsVisibility();
    	
    	mUrlEditText.clearFocus();
    }
    
    private void doFind() {
		CharSequence find = mFindText.getText();
		if (find.length() == 0) {
			mFindPreviousButton.setEnabled(false);
			mFindNextButton.setEnabled(false);
			mCurrentWebView.clearMatches();
		} else {
			int found = mCurrentWebView.findAll(find.toString());
			if (found < 2) {
				mFindPreviousButton.setEnabled(false);
				mFindNextButton.setEnabled(false);
			} else {
				mFindPreviousButton.setEnabled(true);
				mFindNextButton.setEnabled(true);
			}
		}
	}
	
	private void showFindDialog() {
		setFindBarVisibility(true);
		mCurrentWebView.doSetFindIsUp(true);
		CharSequence text = mFindText.getText();
		if (text.length() > 0) {
			mFindText.setSelection(0, text.length());
			doFind();
		} else {
			mFindPreviousButton.setEnabled(false);
			mFindNextButton.setEnabled(false);
		}
				
		mFindText.requestFocus();
		showKeyboardForFindDialog();		
	}
	
	private void closeFindDialog() {
		hideKeyboardFromFindDialog();
		mCurrentWebView.doNotifyFindDialogDismissed();
		setFindBarVisibility(false);
	}
    
    private void setFindBarVisibility(boolean visible) {
    	if (visible) {
    		mFindBar.startAnimation(AnimationManager.getInstance().getTopBarShowAnimation());
    		mFindBar.setVisibility(View.VISIBLE);    		
    		mFindDialogVisible = true;
    	} else {
    		mFindBar.startAnimation(AnimationManager.getInstance().getTopBarHideAnimation());
    		mFindBar.setVisibility(View.GONE);
    		mFindDialogVisible = false;
    	}
    }
    
    /**
     * Change the tool bars visibility.
     * @param visible If True, the tool bars will be shown.
     */
    private void setToolbarsVisibility(boolean visible) {
    	boolean switchTabByButtons = isSwitchTabsByButtonsEnabled();
    	boolean showPreviousTabView = mViewFlipper.getDisplayedChild() > 0;
		boolean showNextTabView = mViewFlipper.getDisplayedChild() < mViewFlipper.getChildCount() - 1;
    	
    	if (visible) {
    		
    		if (!mUrlBarVisible) {    
    		    // PSIPHON: disable hiding the address bar
    			// mTopBar.startAnimation(AnimationManager.getInstance().getTopBarShowAnimation());
    			mBottomBar.startAnimation(AnimationManager.getInstance().getBottomBarShowAnimation());
    			
    			if (switchTabByButtons) {
    				if (showPreviousTabView) {
    					mPreviousTabView.startAnimation(AnimationManager.getInstance().getPreviousTabViewShowAnimation());
    				}

    				if (showNextTabView) {
    					mNextTabView.startAnimation(AnimationManager.getInstance().getNextTabViewShowAnimation());
    				}
    			}
    			
    			mTopBar.setVisibility(View.VISIBLE);
    			mBottomBar.setVisibility(View.VISIBLE);

    			if (switchTabByButtons) {
    				if (showPreviousTabView) {
    					mPreviousTabView.setVisibility(View.VISIBLE);
    				}

    				if (showNextTabView) {
    					mNextTabView.setVisibility(View.VISIBLE);
    				}
    			}
    			
    			mBubbleRightView.setVisibility(View.GONE);
    			mBubbleLeftView.setVisibility(View.GONE);
    		}
    		
    		startToolbarsHideRunnable();
    		
    		mUrlBarVisible = true;    		    		
    		
    	} else {  	
    		
    		if (mUrlBarVisible) {
                // PSIPHON: disable hiding the address bar
    		    // mTopBar.startAnimation(AnimationManager.getInstance().getTopBarHideAnimation());
    			mBottomBar.startAnimation(AnimationManager.getInstance().getBottomBarHideAnimation());    			    			
    			
    			if (switchTabByButtons) {
    				if (showPreviousTabView) {
    					mPreviousTabView.startAnimation(AnimationManager.getInstance().getPreviousTabViewHideAnimation());
    				}

    				if (showNextTabView) {
    					mNextTabView.startAnimation(AnimationManager.getInstance().getNextTabViewHideAnimation());
    				}
    			}
    			
                // PSIPHON: disable hiding the address bar
    			// mTopBar.setVisibility(View.GONE);
    			mBottomBar.setVisibility(View.GONE);
    			
    			if (switchTabByButtons) {
    				if (showPreviousTabView) {
    					mPreviousTabView.setVisibility(View.GONE);
    				}

    				if (showNextTabView) {
    					mNextTabView.setVisibility(View.GONE);
    				}
    			}

    			String bubblePosition = Controller.getInstance().getPreferences().getString(Constants.PREFERENCES_GENERAL_BUBBLE_POSITION, "right");

    			if (bubblePosition.equals("right")) {
    				mBubbleRightView.setVisibility(View.VISIBLE);
    				mBubbleLeftView.setVisibility(View.GONE);
    			} else if (bubblePosition.equals("left")) {
    				mBubbleRightView.setVisibility(View.GONE);
    				mBubbleLeftView.setVisibility(View.VISIBLE);
    			} else if (bubblePosition.equals("both")) {
    				mBubbleRightView.setVisibility(View.VISIBLE);
    				mBubbleLeftView.setVisibility(View.VISIBLE);
    			} else {
    				mBubbleRightView.setVisibility(View.VISIBLE);
    				mBubbleLeftView.setVisibility(View.GONE);
    			}
    		}
			
			mUrlBarVisible = false;
    	}
    }
    
    private void showKeyboardForFindDialog() {
    	InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    	imm.showSoftInput(mFindText, InputMethodManager.SHOW_IMPLICIT);
    }
    
    private void hideKeyboardFromFindDialog() {
    	InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    	imm.hideSoftInputFromWindow(mFindText.getWindowToken(), 0);
    }
    
    /**
     * Hide the keyboard.
     * @param delayedHideToolbars If True, will start a runnable to delay tool bars hiding. If False, tool bars are hidden immediatly.
     */
    private void hideKeyboard(boolean delayedHideToolbars) {
    	InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    	imm.hideSoftInputFromWindow(mUrlEditText.getWindowToken(), 0);
    	
    	if (mUrlBarVisible) {
    		if (delayedHideToolbars) {
    			startToolbarsHideRunnable();
    		} else {
    			setToolbarsVisibility(false);
    		}
    	}
    }
    
    /**
     * Thread to delay the show of the find dialog. This seems to be necessary when shown from
     * a QuickAction. If not, the keyboard does not show. 50ms seems to be enough on
     * a Nexus One and on the (rather) slow emulator. Dirty hack :(
     */
    private void startShowFindDialogRunnable() {
    	new Thread(new Runnable() {
			
    		private Handler mHandler = new Handler() {
    			public void handleMessage(Message msg) {
    				showFindDialog();
    			}
    		};
    		
			@Override
			public void run() {
				try {
					Thread.sleep(50);
					mHandler.sendEmptyMessage(0);					
				} catch (InterruptedException e) {
					mHandler.sendEmptyMessage(0);
				}
				
			}
		}).start();
    }
    
    /**
     * Start a runnable to hide the tool bars after a user-defined delay.
     */
    private void startToolbarsHideRunnable() {
    	    	    	
    	if (mHideToolbarsRunnable != null) {
    		mHideToolbarsRunnable.setDisabled();
    	}
    	
    	int delay = Integer.parseInt(Controller.getInstance().getPreferences().getString(Constants.PREFERENCES_GENERAL_BARS_DURATION, "3000"));
    	if (delay <= 0) {
    		delay = 3000;
    	}
    	
    	mHideToolbarsRunnable = new HideToolbarsRunnable(this, delay);    	
    	new Thread(mHideToolbarsRunnable).start();
    }
    
    /**
	 * Hide the tool bars.
	 */
	public void hideToolbars() {
		if (mUrlBarVisible) {			
			if ((!mUrlEditText.hasFocus()) &&
					(!mToolsActionGridVisible)) {
				
				if (!mCurrentWebView.isLoading()) {
					setToolbarsVisibility(false);
				}
			}
		}
		mHideToolbarsRunnable = null;
	}
    
    /**
     * Start a runnable to update history.
     * @param title The page title.
     * @param url The page url.
     */
    private void startHistoryUpdaterRunnable(String title, String url, String originalUrl) {
    	if ((url != null) &&
    			(url.length() > 0)) {
    		new Thread(new HistoryUpdater(this, title, url, originalUrl)).start();
    	}
    }
    
    /**
     * Navigate to the given url.
     * @param url The url.
     */
    private void navigateToUrl(String url) {
    	// Needed to hide toolbars properly.
    	mUrlEditText.clearFocus();
    	
    	if ((url != null) &&
    			(url.length() > 0)) {
    	
    		if (UrlUtils.isUrl(url)) {
    			url = UrlUtils.checkUrl(url);
    		} else {
    			url = UrlUtils.getSearchUrl(this, url);
    		}    		    	
    		
    		hideKeyboard(true);
    		
    		if (url.equals(Constants.URL_ABOUT_START)) {
    			
    			mCurrentWebView.loadDataWithBaseURL("file:///android_asset/startpage/",
    					ApplicationUtils.getStartPage(this), "text/html", "UTF-8", Constants.URL_ABOUT_START);
    			
    		} else {
    			
    			// If the url is not from GWT mobile view, and is in the mobile view url list, then load it with GWT.
    			if ((!url.startsWith(Constants.URL_GOOGLE_MOBILE_VIEW_NO_FORMAT)) &&
    					(UrlUtils.checkInMobileViewUrlList(this, url))) {
    				
    				url = String.format(Constants.URL_GOOGLE_MOBILE_VIEW, url);    				
    			}
    			mCurrentWebView.loadUrl(url);    			
    		}
    	}
    }        
    
    /**
     * Navigate to the url given in the url edit text.
     */
    private void navigateToUrl() {
    	navigateToUrl(mUrlEditText.getText().toString());    	
    }
    
    /**
     * Navigate to the user home page.
     */
    private void navigateToHome() {
    	navigateToUrl(Controller.getInstance().getPreferences().getString(Constants.PREFERENCES_GENERAL_HOME_PAGE,
    			Constants.URL_ABOUT_START));
    }
    
    /**
     * Navigate to the previous page in history.
     */
    private void navigatePrevious() {
    	// Needed to hide toolbars properly.
    	mUrlEditText.clearFocus();
    	
    	hideKeyboard(true);
    	mCurrentWebView.goBack();
    }
    
    /**
     * Navigate to the next page in history. 
     */
    private void navigateNext() {
    	// Needed to hide toolbars properly.
    	mUrlEditText.clearFocus();
    	
    	hideKeyboard(true);
    	mCurrentWebView.goForward();
    }

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			this.moveTaskToBack(true);
			return true;
		default: return super.onKeyLongPress(keyCode, event);
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mCustomView != null) {
				hideCustomView();
			} else if (mFindDialogVisible) {
				closeFindDialog();
			} else {
				if (mCurrentWebView.canGoBack()) {
					mCurrentWebView.goBack();				
				} else {
					this.moveTaskToBack(true);
					return true;
				}
			}
			return true;
		case KeyEvent.KEYCODE_SEARCH:
			if (!mFindDialogVisible) {
				showFindDialog();
			}
			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			String volumeKeysBehaviour = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREFERENCES_UI_VOLUME_KEYS_BEHAVIOUR, "DEFAULT");
			
			if (volumeKeysBehaviour.equals("DEFAULT")) {
				return super.onKeyUp(keyCode, event);
			} else {
				return true;
			}
		default: return super.onKeyUp(keyCode, event);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		String volumeKeysBehaviour = PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.PREFERENCES_UI_VOLUME_KEYS_BEHAVIOUR, "DEFAULT");
		
		if (!volumeKeysBehaviour.equals("DEFAULT")) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				
				if (volumeKeysBehaviour.equals("SWITCH_TABS")) {
					showPreviousTab(false);
				} else if (volumeKeysBehaviour.equals("SCROLL")) {
					mCurrentWebView.pageDown(false);
				} else if (volumeKeysBehaviour.equals("HISTORY")) {
					mCurrentWebView.goForward();
				} else {
					mCurrentWebView.zoomIn();
				}
				
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
				
				if (volumeKeysBehaviour.equals("SWITCH_TABS")) {
					showNextTab(false);
				} else if (volumeKeysBehaviour.equals("SCROLL")) {
					mCurrentWebView.pageUp(false);
				} else if (volumeKeysBehaviour.equals("HISTORY")) {
					mCurrentWebView.goBack();
				} else {
					mCurrentWebView.zoomOut();
				}
				
				return true;
			default: return super.onKeyDown(keyCode, event);
			}
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	/**
	 * Set the application title to default.
	 */
	private void clearTitle() {
		this.setTitle(getResources().getString(R.string.ApplicationName));
    }
	
	/**
	 * Update the application title.
	 */
	private void updateTitle() {
		String value = mCurrentWebView.getTitle();
    	
    	if ((value != null) &&
    			(value.length() > 0)) {    	
    		this.setTitle(String.format(getResources().getString(R.string.ApplicationNameUrl), value));    		
    	} else {
    		clearTitle();
    	}
	}
	
	/**
	 * Get a Drawable of the current favicon, with its size normalized relative to current screen density.
	 * @return The normalized favicon.
	 */
	private BitmapDrawable getNormalizedFavicon() {
		
		BitmapDrawable favIcon = new BitmapDrawable(getResources(), mCurrentWebView.getFavicon());
		
		if (mCurrentWebView.getFavicon() != null) {
			int imageButtonSize = ApplicationUtils.getImageButtonSize(this);
			int favIconSize = ApplicationUtils.getFaviconSize(this);
			
			Bitmap bm = Bitmap.createBitmap(imageButtonSize, imageButtonSize, Bitmap.Config.ARGB_4444);
			Canvas canvas = new Canvas(bm);
			
			favIcon.setBounds((imageButtonSize / 2) - (favIconSize / 2), (imageButtonSize / 2) - (favIconSize / 2), (imageButtonSize / 2) + (favIconSize / 2), (imageButtonSize / 2) + (favIconSize / 2));
			favIcon.draw(canvas);
			
			favIcon = new BitmapDrawable(getResources(), bm);
		}
		
		return favIcon;
	}
	
	/**
	 * Update the "Go" button image.
	 */
	private void updateGoButton() {		
		if (mCurrentWebView.isLoading()) {
			mGoButton.setImageResource(R.drawable.ic_btn_stop);			
			mUrlEditText.setCompoundDrawablesWithIntrinsicBounds(null, null, mCircularProgress, null);
			((AnimationDrawable) mCircularProgress).start();
		} else {
			if (!mCurrentWebView.isSameUrl(mUrlEditText.getText().toString())) {
				mGoButton.setImageResource(R.drawable.ic_btn_go);
			} else {
				mGoButton.setImageResource(R.drawable.ic_btn_reload);
			}			
			
			mUrlEditText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);			
			((AnimationDrawable) mCircularProgress).stop();
		}
	}
	
	/**
	 * Update the fav icon display.
	 */
	private void updateFavIcon() {
		BitmapDrawable favicon = getNormalizedFavicon();
		
		if (mCurrentWebView.getFavicon() != null) {
			mToolsButton.setImageDrawable(favicon);
		} else {
			mToolsButton.setImageResource(R.drawable.fav_icn_default_toolbar);
		}
	}
	
	/**
	 * Update the UI: Url edit text, previous/next button state,...
	 */
	private void updateUI() {
		mUrlEditText.removeTextChangedListener(mUrlTextWatcher);
		mUrlEditText.setText(mCurrentWebView.getUrl());
		mUrlEditText.addTextChangedListener(mUrlTextWatcher);
		
		mPreviousButton.setEnabled(mCurrentWebView.canGoBack());
		mNextButton.setEnabled(mCurrentWebView.canGoForward());
		
		if (mCurrentWebView.getUrl() != null)
			mRemoveTabButton.setEnabled((mViewFlipper.getChildCount() > 1 || !mCurrentWebView.getUrl().equals(Constants.URL_ABOUT_START)));
		else
			mRemoveTabButton.setEnabled(mViewFlipper.getChildCount() > 1);
		
		mProgressBar.setProgress(mCurrentWebView.getProgress());
		
		updateGoButton();
		
		updateTitle();
		
		updateFavIcon();
	}
	
	private boolean isSwitchTabsByFlingEnabled() {
		return (mSwitchTabsMethod == SwitchTabsMethod.FLING) || (mSwitchTabsMethod == SwitchTabsMethod.BOTH);
	}
	
	private boolean isSwitchTabsByButtonsEnabled() {
		return (mSwitchTabsMethod == SwitchTabsMethod.BUTTONS) || (mSwitchTabsMethod == SwitchTabsMethod.BOTH);
	}
	
	/**
	 * Open the "Add bookmark" dialog.
	 */
	private void openAddBookmarkDialog() {
		Intent i = new Intent(this, EditBookmarkActivity.class);
		
		i.putExtra(Constants.EXTRA_ID_BOOKMARK_ID, (long) -1);
		i.putExtra(Constants.EXTRA_ID_BOOKMARK_TITLE, mCurrentWebView.getTitle());
		i.putExtra(Constants.EXTRA_ID_BOOKMARK_URL, mCurrentWebView.getUrl());
		
		startActivity(i);
	}
	
	/**
	 * Open the bookmark list.
	 */
	private void openBookmarksHistoryActivity() {
    	Intent i = new Intent(this, BookmarksHistoryActivity.class);
    	startActivityForResult(i, OPEN_BOOKMARKS_HISTORY_ACTIVITY);
    }
	
	/**
	 * Open the download list.
	 */
	private void openDownloadsList() {
		Intent i = new Intent(this, DownloadsListActivity.class);
    	startActivityForResult(i, OPEN_DOWNLOADS_ACTIVITY);
	}
	
	/**
	 * Perform the user-defined action when clicking on the quick button.
	 */
	private void onQuickButton() {
		openBookmarksHistoryActivity();
	}
	
	/**
	 * Open preferences.
	 */
	private void openPreferences() {
		Intent preferencesActivity = new Intent(this, PreferencesActivity.class);
  		startActivity(preferencesActivity);
	}

    // PSIPHON
	// feedback menu item
	private void launchFeedback()
	{	    
        Intent intent = new Intent(
                "ACTION_VIEW",
                null,
                this,
                FeedbackActivity.class);
        this.startActivity(intent);
    }
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
    	MenuItem item;
    	
        // PSIPHON
        // feedback menu item
        item = menu.add(0, MENU_FEEDBACK, 0, R.string.Main_MenuFeedback);
        item.setIcon(R.drawable.ic_menu_feedback);
        
        item = menu.add(0, MENU_ADD_BOOKMARK, 0, R.string.Main_MenuAddBookmark);
        item.setIcon(R.drawable.ic_menu_add_bookmark);
        
        item = menu.add(0, MENU_SHOW_BOOKMARKS, 0, R.string.Main_MenuShowBookmarks);
        item.setIcon(R.drawable.ic_menu_bookmarks);
        
        item = menu.add(0, MENU_SHOW_DOWNLOADS, 0, R.string.Main_MenuShowDownloads);
        item.setIcon(R.drawable.ic_menu_downloads);                
        
        item = menu.add(0, MENU_PREFERENCES, 0, R.string.Main_MenuPreferences);
        item.setIcon(R.drawable.ic_menu_preferences);
        
        item = menu.add(0, MENU_EXIT, 0, R.string.Main_MenuExit);
        item.setIcon(R.drawable.ic_menu_exit);
    	
    	return true;
	}
	
	@Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	switch(item.getItemId()) {
        // PSIPHON
        // feedback menu item
        case MENU_FEEDBACK:         
            launchFeedback();
            return true;
        case MENU_ADD_BOOKMARK:         
            openAddBookmarkDialog();
            return true;
    	case MENU_SHOW_BOOKMARKS:    		
    		openBookmarksHistoryActivity();
            return true;
    	case MENU_SHOW_DOWNLOADS:    		
    		openDownloadsList();
            return true;
    	case MENU_PREFERENCES:    		
    		openPreferences();
            return true;    	
    	case MENU_EXIT:
    		this.finish();
    		return true;
        default: return super.onMenuItemSelected(featureId, item);
    	}
    }
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        if (requestCode == OPEN_BOOKMARKS_HISTORY_ACTIVITY) {
        	if (intent != null) {
        		Bundle b = intent.getExtras();
        		if (b != null) {
        			if (b.getBoolean(Constants.EXTRA_ID_NEW_TAB)) {
        				addTab(false);
        			}
        			navigateToUrl(b.getString(Constants.EXTRA_ID_URL));
        		}
        	}
        } else if (requestCode == OPEN_FILE_CHOOSER_ACTIVITY) {
        	if (mUploadMessage == null) {
        		return;
        	}
        	
        	Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
	}
	
	@Override
	protected void onPause() {
	    //
	    // Psiphon: Store the URLs for the open tabs
	    //
	    if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_BROWSER_RESTORE_LAST_PAGE, true)) 
	    {	    
            JSONArray jsonURLs = new JSONArray();
    	    for (CustomWebView webView : mWebViews)
    	    {
    	        String url = webView.getUrl();
    	        if (url == null || url.length() == 0) url = webView.getLoadedUrl();
    	        jsonURLs.put(url);
    	    }
    	    
    	    SharedPreferences prefs = Controller.getInstance().getPreferences();	
    	    SharedPreferences.Editor prefsEditor = prefs.edit();
    	    prefsEditor.putString("urlsToRestore", jsonURLs.toString());
    	    prefsEditor.commit();
	    }	    
	    // end Psiphon changes
	    
		mCurrentWebView.doOnPause();
		super.onPause();
	}
	
    @Override
    protected void onResume() {
        mCurrentWebView.doOnResume();
        super.onResume();

        if (!ensurePsiphonRunning())
        {
            // Must return -- control switching to Psiphon task.
            return;
        }
    }

	/**
	 * Show a toast alert on tab switch.
	 */
	private void showToastOnTabSwitch() {
		if (Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_SHOW_TOAST_ON_TAB_SWITCH, true)) {
			String text;
			if (mCurrentWebView.getTitle() != null) {
				text = String.format(getString(R.string.Main_ToastTabSwitchFullMessage), mViewFlipper.getDisplayedChild() + 1, mCurrentWebView.getTitle());
			} else {
				text = String.format(getString(R.string.Main_ToastTabSwitchMessage), mViewFlipper.getDisplayedChild() + 1);
			}
			Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
		}		
	}
	
	private void updatePreviousNextTabViewsVisibility() {
    	if ((mUrlBarVisible) &&
    			(isSwitchTabsByButtonsEnabled())) {
    		if (mViewFlipper.getDisplayedChild() > 0) {
    			mPreviousTabView.setVisibility(View.VISIBLE);
    		} else {
    			mPreviousTabView.setVisibility(View.GONE);
    		}

    		if (mViewFlipper.getDisplayedChild() < mViewFlipper.getChildCount() - 1) {
    			mNextTabView.setVisibility(View.VISIBLE);
    		} else {
    			mNextTabView.setVisibility(View.GONE);
    		}
    	} else {
    		mPreviousTabView.setVisibility(View.GONE);
    		mNextTabView.setVisibility(View.GONE);
    	}
    }
	
	/**
	 * Show the previous tab, if any.
	 */
	private void showPreviousTab(boolean resetToolbarsRunnable) {
		
		if (mViewFlipper.getChildCount() > 1) {
			
			if (mFindDialogVisible) {
				closeFindDialog();
			}
			
			mCurrentWebView.doOnPause();
			
			mViewFlipper.setInAnimation(AnimationManager.getInstance().getInFromLeftAnimation());
			mViewFlipper.setOutAnimation(AnimationManager.getInstance().getOutToRightAnimation());

			mViewFlipper.showPrevious();

			mCurrentWebView = mWebViews.get(mViewFlipper.getDisplayedChild());

			mCurrentWebView.doOnResume();
			
			if (resetToolbarsRunnable) {
				startToolbarsHideRunnable();
			}
			
			showToastOnTabSwitch();
			
			updatePreviousNextTabViewsVisibility();

			updateUI();
		}
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setFullscreen(boolean enabled) {
        Window win = MainActivity.this.getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        final int bits = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (enabled) {
            winParams.flags |=  bits;
            if (mCustomView != null) {
                mCustomView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            } else {
                //mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        } else {
            winParams.flags &= ~bits;
            if (mCustomView != null) {
                mCustomView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            } else {
                //mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
        win.setAttributes(winParams);
    }

	
	private void showCustomView(View view, int requestedOrientation, WebChromeClient.CustomViewCallback callback) {
        // if a view already exists then immediately terminate the new one
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }

        if (requestedOrientation == -1) {
            requestedOrientation = MainActivity.this.getRequestedOrientation();
        }

        mOriginalOrientation = MainActivity.this.getRequestedOrientation();
        FrameLayout decor = (FrameLayout) MainActivity.this.getWindow().getDecorView();
        mFullscreenContainer = new FullscreenHolder(MainActivity.this);
        mFullscreenContainer.addView(view, COVER_SCREEN_PARAMS);
        decor.addView(mFullscreenContainer, COVER_SCREEN_PARAMS);
        mCustomView = view;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            setFullscreen(true);
        }
        mCustomViewCallback = callback;
        MainActivity.this.setRequestedOrientation(requestedOrientation);
    }
	
	private void hideCustomView() {
		if (mCustomView == null)
            return;
		
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            setFullscreen(false);
        }
        FrameLayout decor = (FrameLayout) MainActivity.this.getWindow().getDecorView();
        decor.removeView(mFullscreenContainer);
        mFullscreenContainer = null;
        mCustomView = null;
        mCustomViewCallback.onCustomViewHidden();
        // Show the content view.
        MainActivity.this.setRequestedOrientation(mOriginalOrientation);
	}
	
	/**
	 * Show the next tab, if any.
	 */
	private void showNextTab(boolean resetToolbarsRunnable) {
		
		if (mViewFlipper.getChildCount() > 1) {
			
			if (mFindDialogVisible) {
				closeFindDialog();
			}
			
			mCurrentWebView.doOnPause();
			
			mViewFlipper.setInAnimation(AnimationManager.getInstance().getInFromRightAnimation());
			mViewFlipper.setOutAnimation(AnimationManager.getInstance().getOutToLeftAnimation());

			mViewFlipper.showNext();

			mCurrentWebView = mWebViews.get(mViewFlipper.getDisplayedChild());

			mCurrentWebView.doOnResume();
			
			if (resetToolbarsRunnable) {
				startToolbarsHideRunnable();
			}
			
			showToastOnTabSwitch();
			
			updatePreviousNextTabViewsVisibility();

			updateUI();
		}
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		hideKeyboard(false);
		
		return mGestureDetector.onTouchEvent(event);
	}
	
	/**
	 * Check if the url is in the AdBlock white list.
	 * @param url The url to check
	 * @return true if the url is in the white list
	 */
	private boolean checkInAdBlockWhiteList(String url) {
		
		if (url != null) {
			boolean inList = false;
			Iterator<String> iter = Controller.getInstance().getAdBlockWhiteList(this).iterator();			
			while ((iter.hasNext()) &&
					(!inList)) {
				if (url.contains(iter.next())) {
					inList = true;
				}
			}
			return inList;
		} else {
			return false;
		}
	}
	
	public void onPageFinished(String url) {
		updateUI();			
		
		/* PSIPHON: Disable AdBlocker
		if ((Controller.getInstance().getPreferences().getBoolean(Constants.PREFERENCES_ADBLOCKER_ENABLE, false)) &&
				(!checkInAdBlockWhiteList(mCurrentWebView.getUrl()))) {
			mCurrentWebView.loadAdSweep();
		}
		*/
		
		WebIconDatabase.getInstance().retainIconForPageUrl(mCurrentWebView.getUrl());
		
		if (mUrlBarVisible) {
			startToolbarsHideRunnable();
		}
	}
	
	public void onPageStarted(String url) {
		if (mFindDialogVisible) {
			closeFindDialog();
		}
		
		mUrlEditText.removeTextChangedListener(mUrlTextWatcher);
		mUrlEditText.setText(url);
		mUrlEditText.addTextChangedListener(mUrlTextWatcher);
		
		mPreviousButton.setEnabled(false);
		mNextButton.setEnabled(false);
		
		updateGoButton();
		
		setToolbarsVisibility(true);
	}
	
	public void onUrlLoading(String url) {
		setToolbarsVisibility(true);
	}
	
	public void onMailTo(String url) {
		Intent sendMail = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		startActivity(sendMail);
	}
	
	public void onExternalApplicationUrl(String url) {
		try {
			
			Intent i  = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(i);
			
		} catch (Exception e) {
			
			// Notify user that the vnd url cannot be viewed.
			new AlertDialog.Builder(this)
			.setTitle(R.string.Main_VndErrorTitle)
			.setMessage(String.format(getString(R.string.Main_VndErrorMessage), url))
			.setPositiveButton(android.R.string.ok,
					new AlertDialog.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which) { }
			})
			.setCancelable(true)
			.create()
			.show();
		}
	}
	
	public void setHttpAuthUsernamePassword(String host, String realm, String username, String password) {
		mCurrentWebView.setHttpAuthUsernamePassword(host, realm, username, password);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		if ((item != null) &&
				(item.getIntent() != null)) {
			Bundle b = item.getIntent().getExtras();

			switch(item.getItemId()) {
			case CONTEXT_MENU_OPEN:
				if (b != null) {
					navigateToUrl(b.getString(Constants.EXTRA_ID_URL));
				}			
				return true;

			case CONTEXT_MENU_OPEN_IN_NEW_TAB:
				if (b != null) {
					addTab(false, mViewFlipper.getDisplayedChild());
					navigateToUrl(b.getString(Constants.EXTRA_ID_URL));
				}			
				return true;

			case CONTEXT_MENU_DOWNLOAD:
				if (b != null) {
					doDownloadStart(b.getString(Constants.EXTRA_ID_URL), null, null, null, 0);
				}
				return true;
			case CONTEXT_MENU_COPY:
				if (b != null) {
					ApplicationUtils.copyTextToClipboard(this, b.getString(Constants.EXTRA_ID_URL), getString(R.string.Commons_UrlCopyToastMessage));
				}
				return true;
			case CONTEXT_MENU_SHARE:
				if (b != null) {
					ApplicationUtils.sharePage(this, "", b.getString(Constants.EXTRA_ID_URL));
				}
				return true;
			default: return super.onContextItemSelected(item);
			}	
		}
		
		return super.onContextItemSelected(item);
	}

	@Override
	public void onDownloadEvent(String event, Object data) {
		if (event.equals(EventConstants.EVT_DOWNLOAD_ON_FINISHED)) {
			
			DownloadItem item = (DownloadItem) data;
			
			if (item.getErrorMessage() == null) {
				Toast.makeText(this, getString(R.string.Main_DownloadFinishedMsg), Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, getString(R.string.Main_DownloadErrorMsg, item.getErrorMessage()), Toast.LENGTH_SHORT).show();
			}
		}			
	}
	
	// PSIPHON
	/**
	 * Discover other URLs that are equivalent to the given URL, where a URL is
	 * "equivalent" if it is in the given URL's redirection chain. 
	 * @param url  The URL that we want to find equivalents for.
	 * @return An array of equivalent URLs, which will contain the original URL.
	 *         (E.g., it's guaranteed to be at least size 1.)
	 */
    @SuppressWarnings("unchecked")
	private List<String> findEquivalentUrls(
	        final String userAgentString, 
	        final String url) 
	{
        List<String> uriStrings = new ArrayList<String>();
        uriStrings.add(url);

        // Hack around android.os.NetworkOnMainThreadException restriction.
        // Still blocks the main thread!

        class RedirectRequest implements Runnable
        {
            Set<URI> uris = null;
            
            public Set<URI> getUris()
            {
                return this.uris;
            }
            
            public void run()
            {
                try
                {
                    HttpParams params = new BasicHttpParams();
                    HttpConnectionParams.setConnectionTimeout(params, 5000);
                    HttpConnectionParams.setSoTimeout(params, 5000);
                    final AppPreferences multiProcessPreferences = new AppPreferences(MainActivity.this);
                    HttpHost httpproxy = new HttpHost(
                            "localhost",
                            multiProcessPreferences.getInt(MainActivity.this.getString(R.string.current_local_http_proxy_port), 0));
                    params.setParameter(ConnRoutePNames.DEFAULT_PROXY, httpproxy);
                    DefaultHttpClient client = new DefaultHttpClient(params);
                    HttpContext context = new BasicHttpContext();
                    HttpRequestBase request = new HttpHead(url);
                    request.setHeader("User-Agent", userAgentString);
                    
                    client.execute(request, context);
                
                    // Clean up the request.
                    request.abort();
                    
                    // The context holds info about the redirections that occurred.
                    RedirectLocations redirectLocations = (RedirectLocations)context.getAttribute("http.protocol.redirect-locations");
                    
                    if (redirectLocations != null)
                    {
                        // In Android's org.apache.http version, there's no way to 
                        // access `redirectLocations.uris`, so we'll have to use reflection
                        // to get at the private variable.
                        Field field;
                        field = redirectLocations.getClass().getDeclaredField("uris");
                        field.setAccessible(true);
                        this.uris = (Set<URI>)field.get(redirectLocations);
                    }
                } 
                catch (Exception e)
                {
                    // pass
                }
            }
        }
        RedirectRequest redirectRequest = new RedirectRequest();
        Thread thread = new Thread(redirectRequest);
        thread.start();
        try
        {
            thread.join();
        } 
        catch (InterruptedException e)
        {
            // pass
        }

        Set<URI> uris = redirectRequest.getUris();
        
        // Add in the redirected URIs we've found.
        if (uris != null && uris.size() > 0) 
        {
            for (URI uri : uris)
            {
                uriStrings.add(uri.toString());
            }
        }
        
        // Hack:
        // If a uriString doesn't end with a '/', then we'll also add an
        // equivalent that has one. Our redirection testing doesn't seem
        // to catch cases of http://example.com vs. http://example.com/
        for (int i = uriStrings.size()-1; i >= 0; i--)
        {
            String uriString = uriStrings.get(i);
        
            if (!uriString.endsWith("/"))
            {
                uriStrings.add(uriString+"/");
            }
        }
            
        return uriStrings;
	}
	
	/**
	 * Gesture listener implementation.
	 */
	private class GestureListener extends GestureDetector.SimpleOnGestureListener {

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			mCurrentWebView.zoomIn();
			return super.onDoubleTap(e);
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,	float velocityY) {
			if (isSwitchTabsByFlingEnabled()) {
				if (e2.getEventTime() - e1.getEventTime() <= FLIP_TIME_THRESHOLD) {
					if (e2.getX() > (e1.getX() + FLIP_PIXEL_THRESHOLD)) {						

						showPreviousTab(false);
						return false;
					}

					// going forwards: pushing stuff to the left
					if (e2.getX() < (e1.getX() - FLIP_PIXEL_THRESHOLD)) {					

						showNextTab(false);
						return false;
					}
				}
			}
			
			return super.onFling(e1, e2, velocityX, velocityY);
		}
		
	}
	
	static class FullscreenHolder extends FrameLayout {

        public FullscreenHolder(Context ctx) {
            super(ctx);
            setBackgroundColor(ctx.getResources().getColor(android.R.color.black));
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            return true;
        }

    }
	
}
