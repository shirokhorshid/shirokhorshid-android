/*
 * Copyright (c) 2016, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3.psiphonlibrary;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.AuthenticatedDataPackage.AuthenticatedDataPackageException;
import com.psiphon3.psiphonlibrary.Utils.MyLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

/**
 * Contains logic relating to downloading and applying upgrades.
 */
public interface UpgradeManager
{
    /**
     * To be used by other UpgradeManager classes only.
     */
    abstract class UpgradeFile
    {
        private Context context;
        
        public UpgradeFile(Context context)
        {
            this.context = context;
        }
        
        public String getFullPath(String filename)
        {
            return this.context.getFileStreamPath(filename).getAbsolutePath();
        }
        
        public String getFullPath()
        {
            return getFullPath(getFilename());
        }
        
        public abstract String getFilename();
        
        public boolean exists()
        {
            try
            {
                this.context.openFileInput(getFilename());
            } 
            catch (FileNotFoundException e)
            {
                return false;
            }
            
            return true;
        }
        
        public boolean delete()
        {
            return this.context.deleteFile(getFilename());
        }
        
        public boolean rename(String newFilename)
        {
            File file = getFile();
            return file.renameTo(new File(getFullPath(newFilename)));
        }
        
        public Uri getUri()
        {
            File file = getFile();
            return Uri.fromFile(file);
        }
        
        public long getSize()
        {
            File file = getFile();
            return file.length();
        }

        public File getFile()
        {
            return new File(getFullPath());
        }

        public abstract boolean isWorldReadable();
        
        @SuppressLint("WorldReadableFiles")
        public FileOutputStream createForWriting() throws FileNotFoundException
        {
            int mode = 0;
            if (isWorldReadable() && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) mode |= Context.MODE_WORLD_READABLE;

            return this.context.openFileOutput(getFilename(), mode);             
        }

        @SuppressLint("WorldReadableFiles")
        public boolean write(byte[] data, int length, boolean append)
        {
            FileOutputStream fos = null;
            try
            {
                int mode = 0;
                if (isWorldReadable() && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) mode |= Context.MODE_WORLD_READABLE;
                if (append) mode |= Context.MODE_APPEND;

                fos = this.context.openFileOutput(getFilename(), mode); 

                fos.write(data, 0, length);
            } 
            catch (FileNotFoundException e)
            {
                // This absolutely should not happen. The documentation indicates:
                // "If the file exists but is a directory rather than a regular 
                //  file, does not exist but cannot be created, or cannot be 
                //  opened for any other reason then a FileNotFoundException is thrown."
                MyLog.w(R.string.UpgradeManager_UpgradeFileNotFound, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return false;
            }
            catch (IOException e)
            {
                MyLog.w(R.string.UpgradeManager_UpgradeFileWriteFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return false;
            }
            finally
            {
                if (fos != null)
                {
                    try { fos.close(); } catch (IOException e) {}
                }
            }
            
            return true;
        }
    }

    class VerifiedUpgradeFile extends UpgradeFile
    {
        public VerifiedUpgradeFile(Context context)
        {
            super(context);
        }
        
        public String getFilename()
        {
            return "PsiphonAndroid.apk";
        }
        
        public boolean isWorldReadable()
        {
            // Making the APK world readable so Installer component can access it
            return true;
        }
    }    

    class UnverifiedUpgradeFile extends UpgradeFile
    {
        public UnverifiedUpgradeFile(Context context)
        {
            super(context);
        }
        
        public String getFilename()
        {
            return "PsiphonAndroid.apk.unverified";
        }
        
        public boolean isWorldReadable()
        {
            // Making the APK world readable so Installer component can access it
            return true;
        }
    }    

    class DownloadedUpgradeFile extends UpgradeFile
    {
        public DownloadedUpgradeFile(Context context)
        {
            super(context);
        }
        
        public String getFilename()
        {
            return "PsiphonAndroid.upgrade_package";
        }
        
        public boolean isWorldReadable()
        {
            return false;
        }

        private InputStream openUnzipStream() throws IOException, FileNotFoundException
        {
            return new GZIPInputStream(new BufferedInputStream(super.context.openFileInput(getFilename())));
        }

        public boolean extractAndVerify()
        {
            InputStream unzipStream = null;

            try
            {
                // NOTE: On Android, the OS also performs its own upgrade authentication which
                // checks that the APK is signed with the same developer key. Our own
                // additional signature check mitigates against a malicious MiM which supplies
                // a malicious, unsigned, upgrade payload which our intent would start to install.
                
                unzipStream = openUnzipStream();
                
                UnverifiedUpgradeFile unverifiedFile = new UnverifiedUpgradeFile(super.context);
                OutputStream dataDestination = unverifiedFile.createForWriting();
                
                AuthenticatedDataPackage.extractAndVerifyData(
                        EmbeddedValues.UPGRADE_SIGNATURE_PUBLIC_KEY,
                        unzipStream,
                        true, // "data" is Base64 (and is a large value to be streamed)
                        dataDestination);

                return unverifiedFile.rename(new VerifiedUpgradeFile(super.context).getFilename());
            }
            catch (FileNotFoundException e)
            {
                MyLog.w(R.string.UpgradeManager_UpgradeFileNotFound, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return false;
            }
            catch (IOException e)
            {
                MyLog.w(R.string.UpgradeManager_UpgradeFileReadFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return false;
            }
            catch (AuthenticatedDataPackageException e)
            {
                MyLog.w(R.string.UpgradeManager_UpgradeFileAuthenticateFailed, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return false;
            }
            finally
            {
                if (unzipStream != null)
                {
                    try { unzipStream.close(); } catch (IOException e) {}
                }
            }
        }
    }
    
    /**
     * Used for checking if an upgrade has been downloaded and installing it.
     */
    class UpgradeInstaller
    {
        private static NotificationManager mNotificationManager;
        private static NotificationCompat.Builder mNotificationBuilder;

        /**
         * Check if an upgrade file is available, and if it's actually a higher
         * version.
         * Side-effect: May delete existing upgrade file if it's invalid or an old version.
         * @return true if upgrade file is available to be applied.
         */
        protected static VerifiedUpgradeFile getAvailableCompleteUpgradeFile(Context context)
        {
            DownloadedUpgradeFile downloadedFile = new DownloadedUpgradeFile(context);
            
            if (downloadedFile.exists())
            {
                boolean success  = downloadedFile.extractAndVerify();

                // If the extract and verify succeeds, delete it since it's no longer
                // required and we don't want to re-install it.
                // If the file isn't working and we think we have the complete file,
                // there may be corrupt bytes. So delete it and next time we'll start over.
                // NOTE: this means if the failure was due to not enough free space
                // to write the extracted file... we still re-download.

                downloadedFile.delete();

                if (!success)
                {
                    return null;
                }
            }
            
            VerifiedUpgradeFile file = new VerifiedUpgradeFile(context);

            // Does the file exist?
            if (!file.exists())
            {
                return null;
            }

            // Is it a higher version than the current app?
            
            final PackageManager pm = context.getPackageManager();
            
            // Info about the potential upgrade file
            PackageInfo upgradePackageInfo = pm.getPackageArchiveInfo(file.getFullPath(), 0);

            if (upgradePackageInfo == null)
            {
                // There's probably something wrong with the upgrade file.
                file.delete();
                MyLog.w(R.string.UpgradeManager_CannotExtractUpgradePackageInfo, MyLog.Sensitivity.NOT_SENSITIVE);
                return null;
            }
            
            // Info about the current app
            PackageInfo currentPackageInfo;
            try
            {
                currentPackageInfo = context.getPackageManager().getPackageInfo(
                                                context.getPackageName(), 
                                                0);
            } 
            catch (NameNotFoundException e)
            {
                // This really shouldn't happen -- we're getting info about the 
                // current package, which clearly exists.
                MyLog.w(R.string.UpgradeManager_CanNotRetrievePackageInfo, MyLog.Sensitivity.NOT_SENSITIVE, e);
                return null;
            }
            
            // Does the upgrade package have a higher version?
            if (upgradePackageInfo.versionCode <= currentPackageInfo.versionCode)
            {
                file.delete();
                return null;
            }
            
            return file;
        }

        /**
         * Checks if a valid upgrade file is available for install.
         * Note that this is not a zero-cost function call, as package verification is done.
         * @param context
         * @return true if an upgrade file is available
         */
        public static boolean upgradeFileAvailable(Context context) {
            return getAvailableCompleteUpgradeFile(context) != null;
        }
        
        /**
         * Create an Android notification to launch the upgrade, if available
         * @param context
         * @return true if an upgrade is available and the notification was shown
         */
        public static boolean notifyUpgrade(Context context)
        {
            VerifiedUpgradeFile file = getAvailableCompleteUpgradeFile(context);
            if (file == null)
            {
                return false;
            }
            
            // This intent triggers the upgrade. It's launched if the user clicks the notification.

            Intent upgradeIntent = new Intent(Intent.ACTION_VIEW);
            Uri apkURI = Build.VERSION.SDK_INT < Build.VERSION_CODES.N ?
                    file.getUri() :
                    FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".UpgradeFileProvider", file.getFile());
            upgradeIntent.setDataAndType(apkURI, "application/vnd.android.package-archive");
            upgradeIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        
            PendingIntent invokeUpgradeIntent = 
                    PendingIntent.getActivity(
                            context,
                            0,
                            upgradeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
        
            if (mNotificationBuilder == null)
            {
                mNotificationBuilder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.notification_icon_upgrade_available)
                        .setContentTitle(context.getString(R.string.UpgradeManager_UpgradePromptTitle))
                        .setContentText(context.getString(R.string.UpgradeManager_UpgradePromptMessage))
                        .setContentIntent(invokeUpgradeIntent);
            }

            if (mNotificationManager == null)
            {
                mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            }

            if (mNotificationManager != null)
            {
                mNotificationManager.notify(R.string.UpgradeManager_UpgradeAvailableNotificationId, mNotificationBuilder.build());
            }

            return true;
        }
    }
}
