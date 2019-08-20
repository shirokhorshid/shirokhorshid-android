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

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;

import com.psiphon3.StatusActivity;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class TunnelVpnService extends VpnService
{
    public static final String USER_STARTED_INTENT_FLAG = "userStarted";

    private TunnelManager m_Manager = new TunnelManager(this);

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Note that this will be called no matter what the current LocaleManager locale is, be it system or not.
        // So we want to always have the TunnelManager update their context to have the new configuration.
        // We don't need to update the notifications though if the language is not set to default.
        // Also note that if this service is stopped when the system language is changed, notifications like the
        // upgrade one will not be updated until something else triggers them to be updated. This could be fixed by
        // adding a broadcast receiver for locale changes but ATM it feels not worth the effort.
        m_Manager.updateContext(this);

        LocaleManager localeManager = LocaleManager.getInstance(this);
        if (localeManager.isSetToSystemLocale()) {
            m_Manager.updateNotifications();
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // Need to use super class behavior in specified cases:
        // http://developer.android.com/reference/android/net/VpnService.html#onBind%28android.content.Intent%29
        
        String action = intent.getAction();
        if (action != null && action.equals(SERVICE_INTERFACE))
        {
            return super.onBind(intent);
        }
        
        return m_Manager.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (!intent.getBooleanExtra(TunnelVpnService.USER_STARTED_INTENT_FLAG, false))
        {
            // The user didn't trigger this so start up the StatusActivity and let them handle this
            startStatusActivity();

            // Call stopSelf after starting the activity
            // This is because a lot of the logic around whether to auto-start the vpn or not is based off
            // if the service is running. So we want to make sure the service is in a stopped state rather
            // than adding extra logic around if this is a "always-on" start or not.
            stopSelf();

            // Indicate we don't want the caller to try calling again
            return Service.START_NOT_STICKY;
        }

        return m_Manager.onStartCommand(intent, flags, startId);
    }

    private void startStatusActivity()
    {
        Intent intent = new Intent(this, StatusActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onCreate()
    {
        m_Manager.onCreate();
    }

    @Override
    public void onDestroy()
    {
        m_Manager.onDestroy();
    }

    @Override
    public void onRevoke()
    {
        m_Manager.onRevoke();
    }
    
    public VpnService.Builder newBuilder() { return new VpnService.Builder(); }
}
