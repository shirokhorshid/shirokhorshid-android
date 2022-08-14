/*
 * Copyright (c) 2022, Psiphon Inc.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import ru.ivanarh.jndcrash.NDCrashService;

public class PsiphonCrashService extends NDCrashService {
    public static String getStdRedirectPath(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/stderr.tmp";
    }

    public static String getCrashReportPath(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/crashreport.tmp";
    }
    @Override

    public void onCrash(String reportPath) {
        super.onCrash(reportPath);

        File dir = getFilesDir();
        File tmpCrashReportFile =new File(getCrashReportPath(this));
        File finalReportFile = new File(dir, "crash.txt");

        if (tmpCrashReportFile.exists()) {
            tmpCrashReportFile.renameTo(finalReportFile);
        }

        File stdErrFile = new File(getStdRedirectPath(this));
        if (stdErrFile.exists() && finalReportFile.exists()) {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(finalReportFile, true));
                BufferedReader in = new BufferedReader(new FileReader(stdErrFile));
                String str;

                out.write("=================================================================\n");
                out.write("                              STDERR                             \n");
                out.write("=================================================================\n");

                while ((str = in.readLine()) != null) {
                    out.write(str);
                    out.newLine();
                }
                out.flush();
                out.close();

                in.close();

                stdErrFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
