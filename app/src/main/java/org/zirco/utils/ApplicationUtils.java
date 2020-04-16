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

package org.zirco.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;

import com.psiphon3.R;

/**
 * Application utilities.
 */
public class ApplicationUtils {
	/**
	 * Display a standard yes / no dialog.
	 * @param context The current context.
	 * @param icon The dialog icon.
	 * @param title The dialog title.
	 * @param message The dialog message.
	 * @param onYes The dialog listener for the yes button.
	 */
	public static void showYesNoDialog(Context context, int icon, int title, int message, DialogInterface.OnClickListener onYes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
    	builder.setCancelable(true);
    	builder.setIcon(icon);
    	builder.setTitle(context.getResources().getString(title));
    	builder.setMessage(context.getResources().getString(message));

    	builder.setInverseBackgroundForced(true);
    	builder.setPositiveButton(context.getResources().getString(R.string.Commons_Yes), onYes);
    	builder.setNegativeButton(context.getResources().getString(R.string.Commons_No), new DialogInterface.OnClickListener() {
    		@Override
    		public void onClick(DialogInterface dialog, int which) {
    			dialog.dismiss();
    		}
    	});
    	AlertDialog alert = builder.create();
    	alert.show();
	}
	
	/**
	 * Display a continue / cancel dialog.
	 * @param context The current context.
	 * @param icon The dialog icon.
	 * @param title The dialog title.
	 * @param message The dialog message.
	 * @param onContinue The dialog listener for the continue button.
	 * @param onCancel The dialog listener for the cancel button.
	 */
	public static void showContinueCancelDialog(Context context, int icon, String title, String message, DialogInterface.OnClickListener onContinue, DialogInterface.OnClickListener onCancel) {		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
    	builder.setCancelable(true);
    	builder.setIcon(icon);
    	builder.setTitle(title);
    	builder.setMessage(message);

    	builder.setInverseBackgroundForced(true);
    	builder.setPositiveButton(context.getResources().getString(R.string.Commons_Continue), onContinue);
    	builder.setNegativeButton(context.getResources().getString(R.string.Commons_Cancel), onCancel);
    	AlertDialog alert = builder.create();
    	alert.show();
	}
	
	/**
	 * Display a standard Ok dialog.
	 * @param context The current context.
	 * @param icon The dialog icon.
	 * @param title The dialog title.
	 * @param message The dialog message.
	 */
	public static void showOkDialog(Context context, int icon, String title, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(false);
    	builder.setIcon(icon);
    	builder.setTitle(title);
    	builder.setMessage(message);
    	
    	builder.setInverseBackgroundForced(true);
    	builder.setPositiveButton(context.getResources().getString(R.string.Commons_Ok), new DialogInterface.OnClickListener() {
    		@Override
    		public void onClick(DialogInterface dialog, int which) {
    			dialog.dismiss();
    		}
    	});
    	AlertDialog alert = builder.create();
    	alert.show();
	}

	/**
     * Display a standard Ok dialog.
     * @param context The current context.
     * @param icon The dialog icon.
     * @param title The dialog title.
     * @param message The dialog message.
     * @param onOk The dialog listener for the ok button.
     */
    public static void showOkDialog(Context context, int icon, String title, String message, DialogInterface.OnClickListener onOk) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setIcon(icon);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setInverseBackgroundForced(true);
        builder.setPositiveButton(context.getResources().getString(R.string.Commons_Ok), onOk);
        AlertDialog alert = builder.create();
        alert.show();
    }
	
	/**
	 * Display a standard Ok / Cancel dialog.
	 * @param context The current context.
	 * @param icon The dialog icon.
	 * @param title The dialog title.
	 * @param message The dialog message.
	 * @param onYes The dialog listener for the yes button.
	 */
	public static void showOkCancelDialog(Context context, int icon, String title, String message, DialogInterface.OnClickListener onYes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
    	builder.setCancelable(true);
    	builder.setIcon(icon);
    	builder.setTitle(title);
    	builder.setMessage(message);

    	builder.setInverseBackgroundForced(true);
    	builder.setPositiveButton(context.getResources().getString(R.string.Commons_Ok), onYes);
    	builder.setNegativeButton(context.getResources().getString(R.string.Commons_Cancel), new DialogInterface.OnClickListener() {
    		@Override
    		public void onClick(DialogInterface dialog, int which) {
    			dialog.dismiss();
    		}
    	});
    	AlertDialog alert = builder.create();
    	alert.show();
	}
	
	/**
	 * Check if the SD card is available. Display an alert if not.
	 * @param context The current context.
	 * @param showMessage If true, will display a message for the user.
	 * @return True if the SD card is available, false otherwise.
	 */
	public static boolean checkCardState(Context context, boolean showMessage) {
		// Check to see if we have an SDCard
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            
        	int messageId;

            // Check to see if the SDCard is busy, same as the music app
            if (status.equals(Environment.MEDIA_SHARED)) {
                messageId = R.string.Commons_SDCardErrorSDUnavailable;
            } else {
                messageId = R.string.Commons_SDCardErrorNoSDMsg;
            }
            
            if (showMessage) {
            	ApplicationUtils.showErrorDialog(context, R.string.Commons_SDCardErrorTitle, messageId);
            }
            
            return false;
        }
        
        return true;
	}

	/**
	 * Checks if we can write to external storage, requesting permission to do so if we can't.
	 *
	 * @param activity The activity to request permissions for
	 * @param requestReason The reason we are requesting permissions
	 *
	 * @return true iff we have the permission to write to external storage.
	 */
	public static boolean ensureWriteStoragePermissionGranted(final Activity activity, final String requestReason, final int requestCode) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			// Permission is automatically granted on sdk < 23 upon installation
			return true;
		}

		// The permission has already been granted
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			return true;
		}

		// The permission has already been denied at least once, so we explain why we need it
		if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			showOkDialog(activity,
					android.R.drawable.ic_dialog_info,
					activity.getString(R.string.Commons_PermissionRequestReasonTitle),
					requestReason,
					new DialogInterface.OnClickListener() {
						@SuppressLint("InlinedApi")
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
						}
					});

			return false;
		}

		// Request the permission. The request code doesn't matter because we aren't handling it.
		ActivityCompat.requestPermissions(activity, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
		return false;
	}
	
	/**
	 * Show an error dialog.
	 * @param context The current context.
	 * @param title The title string id.
	 * @param message The message string id.
	 */
	public static void showErrorDialog(Context context, int title, int message) {
		new AlertDialog.Builder(context)
        .setTitle(title)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setMessage(message)
        .setPositiveButton(R.string.Commons_Ok, null)
        .show();
	}
	
	public static void showErrorDialog(Context context, int title, String message) {
		new AlertDialog.Builder(context)
        .setTitle(title)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setMessage(message)
        .setPositiveButton(R.string.Commons_Ok, null)
        .show();
	}
	
}
