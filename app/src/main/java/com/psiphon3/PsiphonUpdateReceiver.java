/*
 * Copyright (c) 2024, Psiphon Inc.
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.psiphon3.log.MyLog;
import com.psiphon3.psiphonlibrary.TunnelServiceInteractor;

import net.grandcentrix.tray.AppPreferences;

public class PsiphonUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            MyLog.i("PsiphonUpdateReceiver: psiphon app package was updated.");

            // Check if the service was running before the update and restart it if necessary
            boolean shouldStart = new AppPreferences(context).getBoolean(context.getString(R.string.serviceRunningPreference), false);

            if (shouldStart) {
                MyLog.i("PsiphonUpdateReceiver: automatically restarting VPN service after update.");
                TunnelServiceInteractor tunnelServiceInteractor = new TunnelServiceInteractor(context, false);
                if (!tunnelServiceInteractor.isServiceRunning(context)) {
                    tunnelServiceInteractor.startTunnelService(context);
                } else {
                    MyLog.i("PsiphonUpdateReceiver: VPN service is already running; doing nothing.");
                }
            }
        }
    }
}
