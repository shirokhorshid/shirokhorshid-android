/*
 * Copyright (c) 2025, Psiphon Inc.
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

package com.psiphon3;

import android.content.Context;

import com.psiphon3.log.MyLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

public abstract class SafeFileStorage<T> {

    private final String TAG;
    private final String lockFileName;
    private final String tempFileName;
    private final String finalFileName;

    public SafeFileStorage(String lockFileName, String tempFileName, String finalFileName) {
        this.TAG = getClass().getSimpleName();
        this.lockFileName = lockFileName;
        this.tempFileName = tempFileName;
        this.finalFileName = finalFileName;
    }

    protected abstract void writeDataToStream(T data, OutputStreamWriter writer) throws IOException;

    protected abstract T readDataFromStream(BufferedReader reader) throws IOException;

    protected abstract T getDefaultValue();

    @SuppressWarnings("resource")
    public void save(Context context, T data) {
        File tempFile = new File(context.getFilesDir(), tempFileName);
        File finalFile = new File(context.getFilesDir(), finalFileName);
        File lockFile = new File(context.getFilesDir(), lockFileName);

        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            channel = randomAccessFile.getChannel();

            try {
                lock = channel.lock();
            } catch (OverlappingFileLockException e) {
                MyLog.e(TAG + ": lock already held by this JVM: " + e);
                return;
            }

            try {
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {

                    writeDataToStream(data, writer);
                    writer.flush();
                    fos.getFD().sync();
                }

                if (!tempFile.renameTo(finalFile)) {
                    MyLog.e(TAG + ": failed to rename temp file to final file.");
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

        } catch (IOException e) {
            MyLog.e(TAG + ": failed to save data: " + e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to release lock: " + e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to close channel: " + e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to close random access file: " + e);
                }
            }
        }
    }

    @SuppressWarnings("resource")
    public T load(Context context) {
        File file = new File(context.getFilesDir(), finalFileName);
        File lockFile = new File(context.getFilesDir(), lockFileName);

        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            channel = randomAccessFile.getChannel();

            try {
                lock = channel.lock(0L, Long.MAX_VALUE, true);
            } catch (OverlappingFileLockException e) {
                MyLog.e(TAG + ": read lock already held by this JVM: " + e);
                return getDefaultValue();
            }

            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    return readDataFromStream(reader);
                }
            }

        } catch (IOException e) {
            MyLog.e(TAG + ": failed to read data: " + e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to release lock: " + e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to close channel: " + e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    MyLog.e(TAG + ": failed to close random access file: " + e);
                }
            }
        }

        return getDefaultValue();
    }
}
