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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.psiphon3.R;
import org.zirco.model.items.BookmarkItem;
import org.zirco.model.items.HistoryItem;
import org.zirco.providers.BookmarksProviderWrapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.text.ClipboardManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

/**
 * Application utilities.
 */
public class ApplicationUtils {
	
	private static String mAdSweepString = null;
	
	private static String mRawStartPage = null;
	private static String mRawStartPageStyles = null;
	private static String mRawStartPageBookmarks = null;
	private static String mRawStartPageHistory = null;
	
	private static String mRawStartPageSearch = null;
	
	private static int mFaviconSize = -1;	
	private static int mImageButtonSize = -1;
	private static int mFaviconSizeForBookmarks = -1;
	
	/**
	 * Share a page.
	 * @param activity The parent activity.
	 * @param title The page title.
	 * @param url The page url.
	 */
	public static void sharePage(Activity activity, String title, String url) {
    	Intent shareIntent = new Intent(Intent.ACTION_SEND);
    	
    	shareIntent.setType("text/plain");
    	shareIntent.putExtra(Intent.EXTRA_TEXT, url);
    	shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
    	
    	try {
    		activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.Main_ShareChooserTitle)));
        } catch(android.content.ActivityNotFoundException ex) {
            // if no app handles it, do nothing
        }
    }
	
	public static String getWeaveAuthToken(Context context) {
		String server = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_WEAVE_SERVER, Constants.WEAVE_DEFAULT_SERVER);
    	String userName = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_WEAVE_USERNAME, null);
    	String password = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_WEAVE_PASSWORD, null);
    	String key = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_WEAVE_KEY, null);
    	
    	boolean ok = (server != null) && (server.length() > 0) && (UrlUtils.isUrl(server)) && 
    		(userName != null) && (userName.length() > 0) &&
    		(password != null) && (password.length() > 0) &&
    		(key != null) && (key.length() > 0);
    	
    	if (ok) {
    		return String.format(Constants.WEAVE_AUTH_TOKEN_SCHEME, key, password, userName, server);
    	} else {
    		return null;
    	}
    }
	
	public static int getImageButtonSize(Activity activity) {
		if (mImageButtonSize == -1) {
			DisplayMetrics metrics = new DisplayMetrics();
			activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

			switch (metrics.densityDpi) {
			case DisplayMetrics.DENSITY_LOW: mImageButtonSize = 16; break;
			case DisplayMetrics.DENSITY_MEDIUM: mImageButtonSize = 32; break;
			case DisplayMetrics.DENSITY_HIGH: mImageButtonSize = 48; break;
			default: mImageButtonSize = 32;
			}
		}
		
		return mImageButtonSize;
	}
	
	/**
	 * Get the required size of the favicon, depending on current screen density.
	 * @param activity The current activity.
	 * @return The size of the favicon, in pixels.
	 */
	public static int getFaviconSize(Activity activity) {
		if (mFaviconSize == -1) {
			DisplayMetrics metrics = new DisplayMetrics();
			activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

			switch (metrics.densityDpi) {
			case DisplayMetrics.DENSITY_LOW: mFaviconSize = 12; break;
			case DisplayMetrics.DENSITY_MEDIUM: mFaviconSize = 24; break;
			case DisplayMetrics.DENSITY_HIGH: mFaviconSize = 32; break;
			default: mFaviconSize = 24;
			}
		}
		
		return mFaviconSize;
	}
	
	/**
	 * Get the required size of the favicon, depending on current screen density.
	 * @param activity The current activity.
	 * @return The size of the favicon, in pixels.
	 */
	public static int getFaviconSizeForBookmarks(Activity activity) {
		if (mFaviconSizeForBookmarks == -1) {
			DisplayMetrics metrics = new DisplayMetrics();
			activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

			switch (metrics.densityDpi) {
			case DisplayMetrics.DENSITY_LOW: mFaviconSizeForBookmarks = 12; break;
			case DisplayMetrics.DENSITY_MEDIUM: mFaviconSizeForBookmarks = 16; break;
			case DisplayMetrics.DENSITY_HIGH: mFaviconSizeForBookmarks = 24; break;
			default: mFaviconSizeForBookmarks = 16;
			}
		}
		
		return mFaviconSizeForBookmarks;
	}
	
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
	 *
	 * @return true iff we have the permission to write to external storage.
	 */
	public static boolean ensureWriteStoragePermissionGranted(Activity activity) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			// Permission is automatically granted on sdk < 23 upon installation
			return true;
		}

		// The permission has already been granted
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			return true;
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
	
	/**
	 * Load a raw string resource.
	 * @param context The current context.
	 * @param resourceId The resource id.
	 * @return The loaded string.
	 */
	private static String getStringFromRawResource(Context context, int resourceId) {
		String result = null;
		
		InputStream is = context.getResources().openRawResource(resourceId);
		if (is != null) {
			StringBuilder sb = new StringBuilder();
			String line;

			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				while ((line = reader.readLine()) != null) {					
					sb.append(line).append("\n");
				}
			} catch (IOException e) {
				Log.w("ApplicationUtils", String.format("Unable to load resource %s: %s", resourceId, e.getMessage()));
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					Log.w("ApplicationUtils", String.format("Unable to load resource %s: %s", resourceId, e.getMessage()));
				}
			}
			result = sb.toString();
		} else {        
			result = "";
		}
		
		return result;
	}
	
	/**
	 * Load the AdSweep script if necessary.
	 * @param context The current context.
	 * @return The AdSweep script.
	 */
	public static String getAdSweepString(Context context) {
		if (mAdSweepString == null) {
			InputStream is = context.getResources().openRawResource(R.raw.adsweep);
			if (is != null) {
				StringBuilder sb = new StringBuilder();
				String line;

				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
					while ((line = reader.readLine()) != null) {
						if ((line.length() > 0) &&
								(!line.startsWith("//"))) {
							sb.append(line).append("\n");
						}
					}
				} catch (IOException e) {
					Log.w("AdSweep", "Unable to load AdSweep: " + e.getMessage());
				} finally {
					try {
						is.close();
					} catch (IOException e) {
						Log.w("AdSweep", "Unable to load AdSweep: " + e.getMessage());
					}
				}
				mAdSweepString = sb.toString();
			} else {        
				mAdSweepString = "";
			}
		}
		return mAdSweepString;
	}
	
	/**
	 * Load the changelog string.
	 * @param context The current context.
	 * @return The changelog string.
	 */
	public static String getChangelogString(Context context) {
		return getStringFromRawResource(context, R.raw.changelog);
	}
	
	/**
	 * Build the html result of the most recent bookmarks.
	 * @param context The current context.
	 * @return The html result of the most recent bookmarks.
	 */
	private static String getBookmarksHtml(Context context) {
		String result = "";
		StringBuilder bookmarksSb = new StringBuilder();
		
		if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCES_START_PAGE_SHOW_BOOKMARKS, true)) {
			
			int limit;
			try {
				limit = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCES_START_PAGE_BOOKMARKS_LIMIT, "5"));
			} catch (Exception e) {
				limit = 5;
			}

			List<BookmarkItem> results = BookmarksProviderWrapper.getStockBookmarksWithLimit(context.getContentResolver(), limit);
			
			for (BookmarkItem item : results) {
				bookmarksSb.append(String.format("<li><a href=\"%s\">%s</a></li>",
						item.getUrl(),
						item.getTitle()));
			}
		}
		
		result = String.format(mRawStartPageBookmarks,
				context.getResources().getString(R.string.StartPage_Bookmarks),
				bookmarksSb.toString());
		
		return result;
	}
	
	/**
	 * Build the html result of the most recent history.
	 * @param context The current context.
	 * @return The html result of the most recent history.
	 */
	private static String getHistoryHtml(Context context) {
		String result = "";
		StringBuilder historySb = new StringBuilder();
		
		if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCES_START_PAGE_SHOW_HISTORY, true)) {			

			int limit;
			try {
				limit = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCES_START_PAGE_HISTORY_LIMIT, "5"));
			} catch (Exception e) {
				limit = 5;
			}
			
			List<HistoryItem> results = BookmarksProviderWrapper.getStockHistoryWithLimit(context.getContentResolver(), limit);
			
			for (HistoryItem item : results) {
				historySb.append(String.format("<li><a href=\"%s\">%s</a></li>",
						item.getUrl(),
						item.getTitle()));
			}
		}
		
		result = String.format(mRawStartPageHistory,
				context.getResources().getString(R.string.StartPage_History),
				historySb.toString());
		
		return result;
	}
	
	/**
	 * Load the start page html.
	 * @param context The current context.
	 * @return The start page html.
	 */
	public static String getStartPage(Context context) {
		
		if (mRawStartPage == null) {
			
			mRawStartPage = getStringFromRawResource(context, R.raw.start);
			mRawStartPageStyles = getStringFromRawResource(context, R.raw.start_style);
			mRawStartPageBookmarks = getStringFromRawResource(context, R.raw.start_bookmarks);
			mRawStartPageHistory = getStringFromRawResource(context, R.raw.start_history);
			
			mRawStartPageSearch = getStringFromRawResource(context, R.raw.start_search);
		}
		
		String result = mRawStartPage;
	
		String bookmarksHtml = getBookmarksHtml(context);
		String historyHtml = getHistoryHtml(context);
		
		String searchHtml = "";
		if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCES_START_PAGE_SHOW_SEARCH, false)) {
			searchHtml = String.format(mRawStartPageSearch, context.getResources().getString(R.string.StartPage_Search), context.getResources().getString(R.string.StartPage_SearchButton));
		}
		
		String bodyHtml = searchHtml + bookmarksHtml + historyHtml;
		
		result = String.format(mRawStartPage,
				mRawStartPageStyles,
				context.getResources().getString(R.string.StartPage_Welcome),
				bodyHtml);		
		
		return result;
	}
	
	/**
	 * Get the application version code.
	 * @param context The current context.
	 * @return The application version code.
	 */
	public static int getApplicationVersionCode(Context context) {
    	
		int result = -1;
		
		try {
			
			PackageManager manager = context.getPackageManager();
			PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
			
			result = info.versionCode;
			
		} catch (NameNotFoundException e) {
			Log.w("ApplicationUtils", "Unable to get application version: " + e.getMessage());
			result = -1;
		}
		
		return result;
	}
	
	/**
     * Copy a text to the clipboard.
     * @param context The current context.
     * @param text The text to copy.
     * @param toastMessage The message to show in a Toast notification. If empty or null, does not display notification.
     */
    public static void copyTextToClipboard(Context context, String text, String toastMessage) {
    	ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Activity.CLIPBOARD_SERVICE); 
    	clipboard.setText(text);
    	
    	if ((toastMessage != null) &&
    			(toastMessage.length() > 0)) {
    		Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
    	}
    }

}
