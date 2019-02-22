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
import org.zirco.providers.BookmarksProviderWrapper;
import org.zirco.utils.Constants;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

/**
 * Add / Edit bookmark activity.
 */
public class EditBookmarkActivity extends LocalizedActivities.Activity {
	
	private EditText mTitleEditText;
	private EditText mUrlEditText;
	
	private Button mOkButton;
	private Button mCancelButton;
	
	private long mRowId = -1;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window w = getWindow();
		 w.requestFeature(Window.FEATURE_LEFT_ICON);
        
        setContentView(R.layout.edit_bookmark_activity);
        
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,	android.R.drawable.ic_input_add);
        
        mTitleEditText = (EditText) findViewById(R.id.EditBookmarkActivity_TitleValue);
        mUrlEditText = (EditText) findViewById(R.id.EditBookmarkActivity_UrlValue);
        
        mOkButton = (Button) findViewById(R.id.EditBookmarkActivity_BtnOk);
        mCancelButton = (Button) findViewById(R.id.EditBookmarkActivity_BtnCancel);
        
        mOkButton.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {				
				setAsBookmark();
				setResult(RESULT_OK);
				finish();
			}
		});
        
        mCancelButton.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
        
        Bundle extras = getIntent().getExtras();
    	if (extras != null) {
    		
    		String title = extras.getString(Constants.EXTRA_ID_BOOKMARK_TITLE);
    		if ((title != null) &&
    				(title.length() > 0)) {
    			mTitleEditText.setText(title);
    		}
    		
    		String url = extras.getString(Constants.EXTRA_ID_BOOKMARK_URL);
    		if ((url != null) &&
    				(url.length() > 0)) {
    			mUrlEditText.setText(url);
    		} else {
    			mUrlEditText.setHint("http://");
    		}
    		
    		mRowId = extras.getLong(Constants.EXTRA_ID_BOOKMARK_ID);
    		
    	}
    	
    	if (mRowId == -1) {
    		setTitle(R.string.EditBookmarkActivity_TitleAdd);
    	}
	}
	
	/**
	 * Set the current title and url values as a bookmark, e.g. adding a record if necessary or set only the bookmark flag.
	 */
	private void setAsBookmark() {
		BookmarksProviderWrapper.setAsBookmark(getContentResolver(), mRowId, mTitleEditText.getText().toString(), mUrlEditText.getText().toString(), true);
	}

}
