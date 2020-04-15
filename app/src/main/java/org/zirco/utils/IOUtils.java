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

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.os.Environment;

/**
 * Utilities for I/O reading and writing.
 */
public class IOUtils {
	
	private static final String APPLICATION_FOLDER = "zirco";
	private static final String DOWNLOAD_FOLDER = "downloads";
	private static final String BOOKMARKS_EXPORT_FOLDER = "bookmarks-exports";
	
	/**
	 * Get the application folder on the SD Card. Create it if not present.
	 * @return The application folder.
	 */
	public static File getApplicationFolder() {
		File root = Environment.getExternalStorageDirectory();
		if (root.canWrite()) {
			
			File folder = new File(root, APPLICATION_FOLDER);
			
			if (!folder.exists()) {
				folder.mkdir();
			}
			
			return folder;
			
		} else {
			return null;
		}
	}
	
	/**
	 * Get the application download folder on the SD Card. Create it if not present.
	 * @return The application download folder.
	 */
	public static File getDownloadFolder() {
	    
	       // PSIPHON: Using system download directory rather than an app-specific one.
        File sysDownloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (sysDownloadFolder != null) {
            if (!sysDownloadFolder.exists()) {
                sysDownloadFolder.mkdir();
            }
        }
        return sysDownloadFolder;

	    /*
		File root = getApplicationFolder();
		
		if (root != null) {
			
			File folder = new File(root, DOWNLOAD_FOLDER);
			
			if (!folder.exists()) {
				folder.mkdir();
			}
			
			return folder;
			
		} else {
			return null;
		}
		*/
	}
	
	/**
	 * Get the application folder for bookmarks export. Create it if not present.
	 * @return The application folder for bookmarks export.
	 */
	public static File getBookmarksExportFolder() {
		File root = getApplicationFolder();
		
		if (root != null) {
			
			File folder = new File(root, BOOKMARKS_EXPORT_FOLDER);
			
			if (!folder.exists()) {
				folder.mkdir();
			}
			
			return folder;
			
		} else {
			return null;
		}
	}
	
	/**
	 * Get the list of xml files in the bookmark export folder.
	 * @return The list of xml files in the bookmark export folder.
	 */
	public static List<String> getExportedBookmarksFileList() {
		List<String> result = new ArrayList<String>();
		
		File folder = getBookmarksExportFolder();		
		
		if (folder != null) {
			
			FileFilter filter = new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					if ((pathname.isFile()) &&
							(pathname.getPath().endsWith(".xml"))) {
						return true;
					}
					return false;
				}
			};
			
			File[] files = folder.listFiles(filter);
			
			for (File file : files) {
				result.add(file.getName());
			}			
		}
		
		Collections.sort(result, new Comparator<String>() {

			@Override
			public int compare(String arg0, String arg1) {				
				return arg1.compareTo(arg0);
			}    		
    	});
		
		return result;
	}

}
