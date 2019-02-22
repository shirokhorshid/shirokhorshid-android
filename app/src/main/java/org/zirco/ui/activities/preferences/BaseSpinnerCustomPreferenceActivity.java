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

package org.zirco.ui.activities.preferences;

import com.psiphon3.R;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import com.psiphon3.psiphonlibrary.LocalizedActivities;

/**
 * Base class for a dialog activity for a preference which can have several predefined values, and a customizable one.
 */
public abstract class BaseSpinnerCustomPreferenceActivity extends LocalizedActivities.Activity {
	
	protected Spinner mSpinner;
	protected EditText mCustomEditText;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		
		Window w = getWindow();
		w.requestFeature(Window.FEATURE_LEFT_ICON);
		
		setContentView(R.layout.base_spinner_custom_preference_activity);
		
		w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map);
		
		mCustomEditText = (EditText) findViewById(R.id.BaseSpinnerCustomPreferenceEditText);
		
		mSpinner = (Spinner) findViewById(R.id.BaseSpinnerCustomPreferenceSpinner);
		
		mSpinner.setPromptId(getSpinnerPromptId());
		
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, getSpinnerValuesArrayId(), android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);		
		mSpinner.setAdapter(adapter);		
		
		setSpinnerValueFromPreferences();
		
		mSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
				onSpinnerItemSelected(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) { }
			
		});
		
		Button okBtn = (Button) findViewById(R.id.BaseSpinnerCustomPreferenceOk);
		okBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onOk();				
				finish();
			}
		});
		
		Button cancelBtn = (Button) findViewById(R.id.BaseSpinnerCustomPreferenceCancel);
		cancelBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}
	
	/**
	 * Get the resource id for the prompt of the spinner.
	 * @return The resource id.
	 */
	protected abstract int getSpinnerPromptId();
	
	/**
	 * Get the resource id for the array values of the spinner.
	 * @return The array id.
	 */
	protected abstract int getSpinnerValuesArrayId();
	
	/**
	 * Initialize the spinner with the current value in preferences.
	 */
	protected abstract void setSpinnerValueFromPreferences();
	
	/**
	 * Behavior when the spinner selected item change.
	 * @param position The new selected index.
	 */
	protected abstract void onSpinnerItemSelected(int position);
	
	/**
	 * Behavior when the user press the Ok button.
	 */
	protected abstract void onOk();

}
